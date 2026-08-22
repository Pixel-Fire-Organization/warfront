package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditEntryJsonTest
{
    @Test
    void roundTripsAFullyPopulatedEntry() throws IOException
    {
        final CompoundTag before = new CompoundTag();
        before.putString("state", "ACTIVE");
        final CompoundTag after = new CompoundTag();
        after.putString("state", "DORMANT");

        final AuditEntry original = new AuditEntry(
                Ulid.generate(), 1_700_000_000_000L, UUID.randomUUID(), "Aldric", UUID.randomUUID(), ActorRole.LEADER,
                AuditSource.COMMAND, ResourceLocation.tryBuild("nationwars", "city_disband"),
                List.of(UUID.randomUUID(), UUID.randomUUID()), before, after, true, "01ARZ3NDEKTSV4RRFFQ69G5FAV", null);

        final String line = AuditEntryJson.toJsonLine(original);
        final AuditEntry roundTripped = AuditEntryJson.fromJsonLine(line);

        assertEquals(original, roundTripped);
    }

    @Test
    void roundTripsNullOptionalFields() throws IOException
    {
        final AuditEntry original = AuditEntry.of(null, "SYSTEM", null, ActorRole.SYSTEM, AuditSource.AUTO,
                ResourceLocation.tryBuild("nationwars", "synthetic"), List.of(), new CompoundTag(), new CompoundTag(), false);

        final AuditEntry roundTripped = AuditEntryJson.fromJsonLine(AuditEntryJson.toJsonLine(original));

        assertEquals(original, roundTripped);
        assertNull(roundTripped.actorUuid());
        assertNull(roundTripped.actorNationId());
        assertNull(roundTripped.revertOf());
        assertNull(roundTripped.revertedBy());
    }

    @Test
    void toJsonLineProducesExactlyOneLine() throws IOException
    {
        final AuditEntry entry = AuditEntry.of(UUID.randomUUID(), "Test", null, ActorRole.MEMBER, AuditSource.COMMAND,
                ResourceLocation.tryBuild("nationwars", "checkpoint_place"), List.of(UUID.randomUUID()),
                new CompoundTag(), new CompoundTag(), false);

        final String line = AuditEntryJson.toJsonLine(entry);

        assertEquals(-1, line.indexOf('\n'), "a JSONL line must not contain an embedded newline");
    }

    @Test
    void rejectsAMalformedActionTypeOnRead()
    {
        final String malformed = "{\"entryId\":\"x\",\"timestamp\":0,\"actorName\":\"a\",\"actorRole\":\"SYSTEM\","
                + "\"source\":\"AUTO\",\"actionType\":\"not a valid id!!\",\"targets\":[],\"before\":\"{}\",\"after\":\"{}\","
                + "\"reversible\":false}";

        assertThrows(IOException.class, () -> AuditEntryJson.fromJsonLine(malformed));
    }
}
