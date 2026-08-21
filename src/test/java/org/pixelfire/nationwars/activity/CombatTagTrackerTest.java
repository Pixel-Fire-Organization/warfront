package org.pixelfire.nationwars.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagTrackerTest
{
    @Test
    void untaggedPlayerIsNotTagged()
    {
        final CombatTagTracker tracker = new CombatTagTracker();

        assertFalse(tracker.isTagged(UUID.randomUUID(), 0L));
    }

    @Test
    void taggedPlayerIsTaggedUntilExpiry()
    {
        final CombatTagTracker tracker = new CombatTagTracker();
        final UUID player = UUID.randomUUID();

        tracker.tag(player, UUID.randomUUID(), 100L, 20L);

        assertTrue(tracker.isTagged(player, 119L));
        assertFalse(tracker.isTagged(player, 120L));
    }

    @Test
    void reTaggingRefreshesTheExpiry()
    {
        final CombatTagTracker tracker = new CombatTagTracker();
        final UUID player = UUID.randomUUID();

        tracker.tag(player, UUID.randomUUID(), 100L, 20L);
        tracker.tag(player, UUID.randomUUID(), 115L, 20L);

        assertTrue(tracker.isTagged(player, 130L));
    }

    @Test
    void clearRemovesTheTag()
    {
        final CombatTagTracker tracker = new CombatTagTracker();
        final UUID player = UUID.randomUUID();
        tracker.tag(player, UUID.randomUUID(), 100L, 20L);

        tracker.clear(player);

        assertFalse(tracker.isTagged(player, 110L));
    }
}
