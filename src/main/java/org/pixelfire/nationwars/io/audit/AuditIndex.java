package org.pixelfire.nationwars.io.audit;

import com.mojang.logging.LogUtils;
import org.pixelfire.nationwars.compute.WorkerPool;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;

/**
 * An in-memory index over the audit log — entry id, actor, targets, timestamp — so queries don't need
 * to decompress anything. Built once at startup by scanning every day file inside the retention
 * window, off the main thread; {@link #isReady()} is false until that finishes, and every query
 * method returns {@link AuditQueryResult.StillIndexing} rather than a partial answer while it is.
 *
 * <p>Rebuilding replaces {@link #snapshot} with a brand-new, fully-built one in a single volatile
 * write, so a reader never sees a half-built index — it sees either the previous one (empty, before
 * the first rebuild) or a complete one, never something in between.
 */
public final class AuditIndex
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile boolean ready;
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public boolean isReady()
    {
        return ready;
    }

    /**
     * Scans every day file in {@code auditDir} whose date falls inside the last {@code retentionDays}
     * days, parses them, and replaces the index contents — all on the worker pool. {@code isReady()}
     * flips to true only once that finishes and the result has been published back via
     * {@code mainThreadExecutor}, matching how every other worker task in this mod hands its result
     * back to the main thread rather than the caller blocking on it.
     *
     * @param auditFileLock the same lock {@link AuditWriter#fileLock()} returns, so this scan can
     *                      never read a file while that writer is mid-write to it — this runs on a
     *                      worker-pool thread, entirely independent of the writer thread, and has no
     *                      other way to avoid that race
     */
    public void rebuildAsync(final Path auditDir, final int retentionDays, final Lock auditFileLock, final WorkerPool workerPool,
            final Executor mainThreadExecutor)
    {
        workerPool.submit("audit-index-rebuild", () -> buildSnapshot(auditDir, retentionDays, auditFileLock), mainThreadExecutor, built ->
        {
            snapshot = built;
            ready = true;
            LOGGER.info("nationwars audit index ready: {} entries indexed from the last {} day(s)", built.byEntryId.size(), retentionDays);
        });
    }

    public AuditQueryResult byActor(final UUID actorUuid, final long sinceMillis, final int limit)
    {
        if (!ready)
        {
            return new AuditQueryResult.StillIndexing();
        }
        final List<AuditIndexEntry> matches = snapshot.byActor.getOrDefault(actorUuid, List.of()).stream()
                .filter(e -> e.timestamp() >= sinceMillis)
                .limit(Math.max(limit, 0))
                .toList();
        return new AuditQueryResult.Entries(matches);
    }

    public AuditQueryResult byTarget(final UUID targetId)
    {
        if (!ready)
        {
            return new AuditQueryResult.StillIndexing();
        }
        return new AuditQueryResult.Entries(snapshot.byTarget.getOrDefault(targetId, List.of()));
    }

    /**
     * The union of every entry referencing any of {@code targetIds}, deduplicated by entry id — used by
     * {@code /nationwars staff revert} to gather every entry that could possibly depend on the one being
     * reverted.
     */
    public AuditQueryResult byTargets(final List<UUID> targetIds)
    {
        if (!ready)
        {
            return new AuditQueryResult.StillIndexing();
        }
        final Map<String, AuditIndexEntry> union = new HashMap<>();
        for (final UUID targetId : targetIds)
        {
            for (final AuditIndexEntry entry : snapshot.byTarget.getOrDefault(targetId, List.of()))
            {
                union.put(entry.entryId(), entry);
            }
        }
        return new AuditQueryResult.Entries(List.copyOf(union.values()));
    }

    /**
     * The one summary matching {@code entryId}, if the index is ready and knows of it. Distinguishing
     * "still indexing" from "not found" matters here too, so this mirrors the other query methods'
     * shape rather than returning a plain {@code Optional}.
     */
    public AuditQueryResult byEntryId(final String entryId)
    {
        if (!ready)
        {
            return new AuditQueryResult.StillIndexing();
        }
        final AuditIndexEntry found = snapshot.byEntryId.get(entryId);
        return new AuditQueryResult.Entries(found == null ? List.of() : List.of(found));
    }

    private static Snapshot buildSnapshot(final Path auditDir, final int retentionDays, final Lock auditFileLock)
    {
        final Map<String, AuditIndexEntry> byEntryId = new HashMap<>();
        final Map<UUID, List<AuditIndexEntry>> byActor = new HashMap<>();
        final Map<UUID, List<AuditIndexEntry>> byTarget = new HashMap<>();

        final File dir = auditDir.toFile();

        // Held for the whole scan, not per-file: this is a one-shot startup operation over a handful
        // of files, and locking it as one block guarantees a fully consistent snapshot with no write
        // interleaved partway through, rather than just avoiding a torn read of any single file.
        auditFileLock.lock();
        try
        {
            final File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.endsWith(".jsonl.gz")) : null;
            if (files == null)
            {
                return new Snapshot(byEntryId, byActor, byTarget);
            }

            final LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
            for (final File file : files)
            {
                final LocalDate day = parseDayOrNull(file.getName());
                if (day == null || day.isBefore(cutoff))
                {
                    continue;
                }
                indexFile(file, byEntryId, byActor, byTarget);
            }
        }
        finally
        {
            auditFileLock.unlock();
        }

        byActor.replaceAll((actor, entries) -> sortedByTimeDescending(entries));
        byTarget.replaceAll((target, entries) -> sortedByTimeDescending(entries));

        return new Snapshot(byEntryId, byActor, byTarget);
    }

    private static void indexFile(final File file, final Map<String, AuditIndexEntry> byEntryId,
            final Map<UUID, List<AuditIndexEntry>> byActor, final Map<UUID, List<AuditIndexEntry>> byTarget)
    {
        final List<String> lines;
        try
        {
            lines = AuditFiles.readAllLines(file);
        }
        catch (final IOException e)
        {
            LOGGER.error("nationwars failed to read audit file {} while rebuilding the index; its entries are unindexed until the next restart",
                    file, e);
            return;
        }

        for (final String line : lines)
        {
            try
            {
                final AuditIndexEntry summary = AuditIndexEntry.summarize(AuditEntryJson.fromJsonLine(line));
                byEntryId.put(summary.entryId(), summary);
                if (summary.actorUuid() != null)
                {
                    byActor.computeIfAbsent(summary.actorUuid(), id -> new ArrayList<>()).add(summary);
                }
                for (final UUID target : summary.targets())
                {
                    byTarget.computeIfAbsent(target, id -> new ArrayList<>()).add(summary);
                }
            }
            catch (final IOException e)
            {
                LOGGER.error("nationwars skipped a malformed audit entry in {}: {}", file, line, e);
            }
        }
    }

    private static List<AuditIndexEntry> sortedByTimeDescending(final List<AuditIndexEntry> entries)
    {
        final List<AuditIndexEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(AuditIndexEntry::timestamp).reversed());
        return List.copyOf(sorted);
    }

    private static LocalDate parseDayOrNull(final String fileName)
    {
        final String dateText = fileName.substring(0, Math.max(0, fileName.length() - ".jsonl.gz".length()));
        try
        {
            return LocalDate.parse(dateText);
        }
        catch (final DateTimeParseException e)
        {
            return null;
        }
    }

    private record Snapshot(Map<String, AuditIndexEntry> byEntryId, Map<UUID, List<AuditIndexEntry>> byActor,
            Map<UUID, List<AuditIndexEntry>> byTarget)
    {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());
    }
}
