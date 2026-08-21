package org.pixelfire.nationwars.io.audit;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevertDependencyCheckTest
{
    private static final ResourceLocation ACTION = ResourceLocation.tryBuild("nationwars", "test_action");
    private final UUID target = UUID.randomUUID();

    private AuditIndexEntry entryAt(final String id, final long timestamp, final String revertOf)
    {
        return new AuditIndexEntry(id, timestamp, null, "actor", ACTION, List.of(target), true, revertOf);
    }

    @Test
    void noLaterEntriesMeansNothingBlocks()
    {
        final AuditIndexEntry entry = entryAt("A", 100L, null);

        final List<String> blocking = RevertDependencyCheck.blockingEntries(entry, List.of(entry));

        assertTrue(blocking.isEmpty());
    }

    @Test
    void aLaterUnrelatedEntryBlocks()
    {
        final AuditIndexEntry entry = entryAt("A", 100L, null);
        final AuditIndexEntry later = entryAt("B", 200L, null);

        final List<String> blocking = RevertDependencyCheck.blockingEntries(entry, List.of(entry, later));

        assertEquals(List.of("B"), blocking);
    }

    @Test
    void theRevertOfTheEntryItselfDoesNotBlock()
    {
        final AuditIndexEntry entry = entryAt("A", 100L, null);
        final AuditIndexEntry itsOwnRevert = entryAt("B", 200L, "A");

        final List<String> blocking = RevertDependencyCheck.blockingEntries(entry, List.of(entry, itsOwnRevert));

        assertTrue(blocking.isEmpty());
    }

    @Test
    void aLaterEntryThatWasItselfUndoneDoesNotBlock()
    {
        final AuditIndexEntry entry = entryAt("A", 100L, null);
        final AuditIndexEntry later = entryAt("B", 200L, null);
        final AuditIndexEntry undoOfLater = entryAt("C", 300L, "B");

        final List<String> blocking = RevertDependencyCheck.blockingEntries(entry, List.of(entry, later, undoOfLater));

        assertTrue(blocking.isEmpty());
    }

    @Test
    void earlierEntriesNeverBlock()
    {
        final AuditIndexEntry entry = entryAt("A", 200L, null);
        final AuditIndexEntry earlier = entryAt("B", 100L, null);

        final List<String> blocking = RevertDependencyCheck.blockingEntries(entry, List.of(entry, earlier));

        assertTrue(blocking.isEmpty());
    }
}
