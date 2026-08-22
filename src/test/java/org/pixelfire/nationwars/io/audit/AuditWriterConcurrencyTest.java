package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pixelfire.nationwars.io.WriterThread;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A saturated queue forces {@link WriterThread}'s caller-runs fallback to execute a write on the
 * calling thread while the dedicated writer thread may still be draining other queued writes — two
 * threads touching the same day file at once unless something serializes them beyond WriterThread's
 * own single-thread guarantee.
 */
class AuditWriterConcurrencyTest
{
    @TempDir
    Path auditDir;

    @Test
    void concurrentAppendsUnderQueueSaturationLoseNoEntry() throws InterruptedException, IOException
    {
        final int threadCount = 8;
        final int perThread = 25;
        final long timestamp = System.currentTimeMillis();

        final List<AuditEntry> submitted = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Capacity 1 makes saturation (and therefore the caller-runs fallback) trigger constantly
        // under concurrent load from several threads.
        try (WriterThread writerThread = new WriterThread(1))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch done = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++)
            {
                pool.submit(() ->
                {
                    try
                    {
                        for (int i = 0; i < perThread; i++)
                        {
                            final AuditEntry entry = syntheticEntry(timestamp);
                            submitted.add(entry);
                            writer.append(entry);
                        }
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(30, TimeUnit.SECONDS), "all submitting threads should finish quickly");
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        final List<String> lines = AuditFiles.readAllLines(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(timestamp)));
        final Set<String> writtenIds = lines.stream()
                .map(line ->
                {
                    try
                    {
                        return AuditEntryJson.fromJsonLine(line).entryId();
                    }
                    catch (final IOException e)
                    {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toSet());
        final Set<String> submittedIds = submitted.stream().map(AuditEntry::entryId).collect(Collectors.toSet());

        assertEquals(threadCount * perThread, submitted.size());
        assertEquals(submittedIds, writtenIds, "every submitted entry must land on disk exactly once — none lost, none duplicated");
        assertEquals(threadCount * perThread, lines.size(), "the file must contain exactly one line per submitted entry");
    }

    private static AuditEntry syntheticEntry(final long timestamp)
    {
        return new AuditEntry(Ulid.generate(), timestamp, UUID.randomUUID(), "Tester", null, ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild("nationwars", "concurrency_test"), List.of(),
                new CompoundTag(), new CompoundTag(), false, null, null);
    }
}
