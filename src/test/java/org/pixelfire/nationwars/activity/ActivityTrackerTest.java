package org.pixelfire.nationwars.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityTrackerTest
{
    private static final long AFK_THRESHOLD_TICKS = 100L;
    private static final long SHIELD_TICKS = 60L;

    @Test
    void unknownPlayerIsTreatedAsAfk()
    {
        final ActivityTracker tracker = new ActivityTracker();

        assertEquals(PlayerActivityState.AFK, tracker.stateOf(UUID.randomUUID(), 0L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void justLoggedInIsShielded()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);

        assertEquals(PlayerActivityState.SHIELDED, tracker.stateOf(player, 30L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void activityAfterShieldKeepsReady()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);

        tracker.recordActivity(player, 70L, AFK_THRESHOLD_TICKS, 0L);

        assertEquals(PlayerActivityState.READY, tracker.stateOf(player, 100L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void goesAfkAfterThresholdWithNoActivity()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);

        assertEquals(PlayerActivityState.AFK, tracker.stateOf(player, SHIELD_TICKS + AFK_THRESHOLD_TICKS, AFK_THRESHOLD_TICKS));
    }

    @Test
    void manualAfkForcesAfkImmediately()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);
        tracker.recordActivity(player, 70L, AFK_THRESHOLD_TICKS, 0L);

        tracker.markManualAfk(player);

        assertEquals(PlayerActivityState.AFK, tracker.stateOf(player, 71L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void activityClearsManualAfk()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);
        tracker.markManualAfk(player);

        tracker.recordActivity(player, 70L, AFK_THRESHOLD_TICKS, 0L);

        assertEquals(PlayerActivityState.READY, tracker.stateOf(player, 71L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void returningFromAfkGrantsExitShield()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);

        final long afkAt = SHIELD_TICKS + AFK_THRESHOLD_TICKS;
        assertEquals(PlayerActivityState.AFK, tracker.stateOf(player, afkAt, AFK_THRESHOLD_TICKS));

        tracker.recordActivity(player, afkAt, AFK_THRESHOLD_TICKS, 40L);

        assertEquals(PlayerActivityState.SHIELDED, tracker.stateOf(player, afkAt + 10, AFK_THRESHOLD_TICKS));
    }

    @Test
    void logoutForgetsThePlayer()
    {
        final ActivityTracker tracker = new ActivityTracker();
        final UUID player = UUID.randomUUID();
        tracker.onLogin(player, 0L, SHIELD_TICKS);

        tracker.onLogout(player);

        assertEquals(PlayerActivityState.AFK, tracker.stateOf(player, 30L, AFK_THRESHOLD_TICKS));
    }
}
