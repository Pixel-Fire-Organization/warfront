package org.pixelfire.nationwars.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityStateMachineTest
{
    private static final UUID PLAYER = UUID.randomUUID();
    private static final long AFK_THRESHOLD_TICKS = 100L;

    @Test
    void shieldedBeforeShieldExpires()
    {
        final PlayerActivityData data = new PlayerActivityData(PLAYER, 0L, 60L, 60L, false);

        assertEquals(PlayerActivityState.SHIELDED, ActivityStateMachine.compute(data, 30L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void readyJustAfterShieldExpiresWithRecentActivity()
    {
        final PlayerActivityData data = new PlayerActivityData(PLAYER, 0L, 60L, 60L, false);

        assertEquals(PlayerActivityState.READY, ActivityStateMachine.compute(data, 61L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void standingStillThroughTheShieldGoesAfkAtShieldPlusThreshold()
    {
        final PlayerActivityData data = new PlayerActivityData(PLAYER, 0L, 60L, 60L, false);

        assertEquals(PlayerActivityState.READY, ActivityStateMachine.compute(data, 60L + AFK_THRESHOLD_TICKS - 1, AFK_THRESHOLD_TICKS));
        assertEquals(PlayerActivityState.AFK, ActivityStateMachine.compute(data, 60L + AFK_THRESHOLD_TICKS, AFK_THRESHOLD_TICKS));
    }

    @Test
    void manualAfkOverridesEverythingElse()
    {
        final PlayerActivityData data = new PlayerActivityData(PLAYER, 0L, 60L, 1000L, true);

        assertEquals(PlayerActivityState.AFK, ActivityStateMachine.compute(data, 1001L, AFK_THRESHOLD_TICKS));
    }

    @Test
    void recentActivityKeepsReadyPastTheOriginalShieldWindow()
    {
        final PlayerActivityData data = new PlayerActivityData(PLAYER, 0L, 60L, 5000L, false);

        assertEquals(PlayerActivityState.READY, ActivityStateMachine.compute(data, 5050L, AFK_THRESHOLD_TICKS));
    }
}
