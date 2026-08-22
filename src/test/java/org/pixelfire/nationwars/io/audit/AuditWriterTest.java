package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pixelfire.nationwars.io.WriterThread;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditWriterTest
{
    @TempDir
    Path auditDir;

    @Test
    void anAppendedEntrySurvivesAndIsReadableAfterClose() throws IOException
    {
        final AuditEntry entry = syntheticEntry(System.currentTimeMillis());

        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(entry);
        }

        final File file = AuditFiles.fileFor(auditDir, AuditFiles.dayOf(entry.timestamp()));
        final List<String> lines = AuditFiles.readAllLines(file);

        assertEquals(1, lines.size());
        assertEquals(entry, AuditEntryJson.fromJsonLine(lines.get(0)));
    }

    @Test
    void multipleEntriesOnTheSameDayShareOneFileInOrder() throws IOException
    {
        final long baseTime = System.currentTimeMillis();
        final AuditEntry first = syntheticEntry(baseTime);
        final AuditEntry second = syntheticEntry(baseTime + 1000);

        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(first);
            writer.append(second);
        }

        final List<String> lines = AuditFiles.readAllLines(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(baseTime)));

        assertEquals(2, lines.size());
        assertEquals(first, AuditEntryJson.fromJsonLine(lines.get(0)));
        assertEquals(second, AuditEntryJson.fromJsonLine(lines.get(1)));
    }

    /**
     * Simulates a server restart on the same day: a second, independent {@link AuditWriter} resumes
     * writing to a file an earlier session already created and closed. The day file must end up
     * containing both sessions' entries and stay readable by a plain {@code GZIPInputStream} — i.e.
     * it must not become a multi-member gzip stream.
     */
    @Test
    void resumingOnTheSameDayAfterARestartKeepsAllEntriesInOneValidGzipMember() throws IOException
    {
        final long day = System.currentTimeMillis();
        final AuditEntry beforeRestart = syntheticEntry(day);
        final AuditEntry afterRestart = syntheticEntry(day + 1000);

        try (WriterThread firstSession = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, firstSession);
            writer.append(beforeRestart);
        }

        try (WriterThread secondSession = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, secondSession);
            writer.append(afterRestart);
        }

        final List<String> lines = AuditFiles.readAllLines(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(day)));

        assertEquals(2, lines.size());
        assertEquals(beforeRestart, AuditEntryJson.fromJsonLine(lines.get(0)));
        assertEquals(afterRestart, AuditEntryJson.fromJsonLine(lines.get(1)));
    }

    @Test
    void entriesOnDifferentDaysGoToDifferentFiles() throws IOException
    {
        final long day1 = System.currentTimeMillis();
        final long day2 = day1 + Duration.ofDays(2).toMillis();
        final AuditEntry entryDay1 = syntheticEntry(day1);
        final AuditEntry entryDay2 = syntheticEntry(day2);

        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(entryDay1);
            writer.append(entryDay2);
        }

        assertTrue(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(day1)).exists());
        assertTrue(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(day2)).exists());
        assertEquals(1, AuditFiles.readAllLines(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(day1))).size());
        assertEquals(1, AuditFiles.readAllLines(AuditFiles.fileFor(auditDir, AuditFiles.dayOf(day2))).size());
    }

    private static AuditEntry syntheticEntry(final long timestamp)
    {
        return new AuditEntry(Ulid.generate(), timestamp, UUID.randomUUID(), "Tester", null, ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild("nationwars", "test_entry"), List.of(UUID.randomUUID()),
                new CompoundTag(), new CompoundTag(), false, null, null);
    }
}
