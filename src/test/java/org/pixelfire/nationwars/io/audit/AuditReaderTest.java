package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pixelfire.nationwars.io.WriterThread;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReaderTest
{
    @TempDir
    Path auditDir;

    @Test
    void readsBackTheFullEntryIncludingBeforeAndAfter() throws IOException
    {
        final CompoundTag before = new CompoundTag();
        before.putString("state", "ACTIVE");
        final AuditEntry entry = new AuditEntry(Ulid.generate(), System.currentTimeMillis(), UUID.randomUUID(), "Tester",
                null, ActorRole.STAFF, AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "city_disband"),
                List.of(UUID.randomUUID()), before, new CompoundTag(), true, null, null);

        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(entry);
        }

        final Optional<AuditEntry> found = AuditReader.readFull(auditDir, AuditIndexEntry.summarize(entry));

        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void returnsEmptyWhenTheDayFileDoesNotExist() throws IOException
    {
        final AuditIndexEntry summary = new AuditIndexEntry(Ulid.generate(), System.currentTimeMillis(), UUID.randomUUID(),
                "Ghost", ResourceLocation.tryBuild("nationwars", "nothing"), List.of(), false, null);

        assertTrue(AuditReader.readFull(auditDir, summary).isEmpty());
    }

    @Test
    void returnsEmptyWhenTheEntryIdIsNotInTheDayFile() throws IOException
    {
        final AuditEntry present = new AuditEntry(Ulid.generate(), System.currentTimeMillis(), UUID.randomUUID(), "Real",
                null, ActorRole.MEMBER, AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "checkpoint_place"),
                List.of(), new CompoundTag(), new CompoundTag(), false, null, null);

        try (WriterThread writerThread = new WriterThread(16))
        {
            final AuditWriter writer = new AuditWriter(auditDir, writerThread);
            writer.append(present);
        }

        final AuditIndexEntry missingSummary = new AuditIndexEntry(Ulid.generate(), present.timestamp(), UUID.randomUUID(),
                "Missing", ResourceLocation.tryBuild("nationwars", "nothing"), List.of(), false, null);

        assertTrue(AuditReader.readFull(auditDir, missingSummary).isEmpty());
    }
}
