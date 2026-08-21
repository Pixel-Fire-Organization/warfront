package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pixelfire.nationwars.compute.WorkerPool;
import org.pixelfire.nationwars.io.WriterThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AuditIndexTest
{
    @TempDir
    Path auditDir;

    private WorkerPool workerPool;

    @AfterEach
    void tearDown()
    {
        if (workerPool != null)
        {
            workerPool.close();
        }
    }

    @Test
    void queriesReturnStillIndexingBeforeTheFirstRebuildCompletes()
    {
        final AuditIndex index = new AuditIndex();

        assertFalse(index.isReady());
        assertInstanceOfStillIndexing(index.byActor(UUID.randomUUID(), 0, 20));
        assertInstanceOfStillIndexing(index.byTarget(UUID.randomUUID()));
        assertInstanceOfStillIndexing(index.byEntryId("anything"));
    }

    @Test
    void rebuildIndexesEntriesWrittenByAnEarlierSession() throws IOException
    {
        final UUID actor = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        final AuditEntry entry = writeOneEntry(actor, target, System.currentTimeMillis());

        final AuditIndex index = rebuildAndAwaitReady(90);

        final AuditQueryResult byActor = index.byActor(actor, 0, 20);
        assertTrue(byActor instanceof AuditQueryResult.Entries);
        assertEquals(List.of(AuditIndexEntry.summarize(entry)), ((AuditQueryResult.Entries) byActor).entries());

        final AuditQueryResult byTarget = index.byTarget(target);
        assertEquals(List.of(AuditIndexEntry.summarize(entry)), ((AuditQueryResult.Entries) byTarget).entries());

        final AuditQueryResult byId = index.byEntryId(entry.entryId());
        assertEquals(List.of(AuditIndexEntry.summarize(entry)), ((AuditQueryResult.Entries) byId).entries());
    }

    @Test
    void rebuildExcludesDayFilesOlderThanTheRetentionWindow() throws IOException
    {
        final UUID actor = UUID.randomUUID();
        final long ancientTimestamp = System.currentTimeMillis() - Duration.ofDays(400).toMillis();
        writeRawEntry(actor, ancientTimestamp);

        final AuditIndex index = rebuildAndAwaitReady(90);

        final AuditQueryResult result = index.byActor(actor, 0, 20);
        assertEquals(List.of(), ((AuditQueryResult.Entries) result).entries());
    }

    @Test
    void aMalformedLineDoesNotPreventOtherEntriesInTheSameFileFromBeingIndexed() throws IOException
    {
        final UUID actor = UUID.randomUUID();
        final long timestamp = System.currentTimeMillis();
        final AuditEntry good = new AuditEntry(Ulid.generate(), timestamp, actor, "Good", null, ActorRole.MEMBER,
                AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "test"), List.of(), new CompoundTag(),
                new CompoundTag(), false, null, null);

        final File file = AuditFiles.fileFor(auditDir, AuditFiles.dayOf(timestamp));
        Files.createDirectories(auditDir);
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8))
        {
            writer.write("{ this is not valid json");
            writer.write('\n');
            writer.write(AuditEntryJson.toJsonLine(good));
            writer.write('\n');
        }

        final AuditIndex index = rebuildAndAwaitReady(90);

        final AuditQueryResult result = index.byActor(actor, 0, 20);
        assertEquals(List.of(AuditIndexEntry.summarize(good)), ((AuditQueryResult.Entries) result).entries());
    }

    @Test
    void rebuildOnAMissingDirectoryProducesAnEmptyReadyIndex()
    {
        final AuditIndex index = rebuildAndAwaitReady(90);

        assertTrue(index.isReady());
        assertEquals(List.of(), ((AuditQueryResult.Entries) index.byTarget(UUID.randomUUID())).entries());
    }

    private AuditEntry writeOneEntry(final UUID actor, final UUID target, final long timestamp) throws IOException
    {
        final AuditEntry entry = new AuditEntry(Ulid.generate(), timestamp, actor, "Tester", null, ActorRole.MEMBER,
                AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "test"), List.of(target),
                new CompoundTag(), new CompoundTag(), false, null, null);
        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(entry);
        }
        return entry;
    }

    private void writeRawEntry(final UUID actor, final long timestamp) throws IOException
    {
        final AuditEntry entry = new AuditEntry(Ulid.generate(), timestamp, actor, "Ancient", null, ActorRole.MEMBER,
                AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "test"), List.of(),
                new CompoundTag(), new CompoundTag(), false, null, null);
        final File file = AuditFiles.fileFor(auditDir, LocalDate.now().minusDays(400));
        Files.createDirectories(auditDir);
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8))
        {
            writer.write(AuditEntryJson.toJsonLine(entry));
            writer.write('\n');
        }
    }

    private AuditIndex rebuildAndAwaitReady(final int retentionDays)
    {
        workerPool = new WorkerPool(2, 16);
        final AuditIndex index = new AuditIndex();
        index.rebuildAsync(auditDir, retentionDays, new ReentrantLock(), workerPool, Runnable::run);

        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!index.isReady())
        {
            if (System.nanoTime() > deadline)
            {
                fail("audit index rebuild did not finish in time");
            }
            try
            {
                Thread.sleep(10);
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for the audit index rebuild");
            }
        }
        return index;
    }

    private static void assertInstanceOfStillIndexing(final AuditQueryResult result)
    {
        assertTrue(result instanceof AuditQueryResult.StillIndexing, "expected StillIndexing, got " + result);
    }
}
