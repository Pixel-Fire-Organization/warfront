package org.pixelfire.nationwars.world;

import org.junit.jupiter.api.Test;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.world.CheckpointMoveGrace.PendingMove;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointMoveGraceTest
{
    @Test
    void noPendingMoveIsEmpty()
    {
        final CheckpointMoveGrace grace = new CheckpointMoveGrace();

        assertTrue(grace.claim(UUID.randomUUID(), UUID.randomUUID(), 0L).isEmpty());
    }

    @Test
    void withinWindowForTheSameCityReturnsTheMove()
    {
        final CheckpointMoveGrace grace = new CheckpointMoveGrace();
        final UUID player = UUID.randomUUID();
        final UUID cityId = UUID.randomUUID();
        final PendingMove move = new PendingMove(UUID.randomUUID(), cityId, UUID.randomUUID(), 0.5f, null, CheckpointStatus.HELD, 1000L);

        grace.record(player, move);

        assertEquals(Optional.of(move), grace.claim(player, cityId, 500L));
    }

    @Test
    void expiredEntryIsNotReturned()
    {
        final CheckpointMoveGrace grace = new CheckpointMoveGrace();
        final UUID player = UUID.randomUUID();
        final UUID cityId = UUID.randomUUID();
        grace.record(player, new PendingMove(UUID.randomUUID(), cityId, UUID.randomUUID(), 0.5f, null, CheckpointStatus.HELD, 1000L));

        assertTrue(grace.claim(player, cityId, 1500L).isEmpty());
    }

    @Test
    void differentCityIsNotReturned()
    {
        final CheckpointMoveGrace grace = new CheckpointMoveGrace();
        final UUID player = UUID.randomUUID();
        grace.record(player, new PendingMove(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0.5f, null, CheckpointStatus.HELD, 1000L));

        assertTrue(grace.claim(player, UUID.randomUUID(), 500L).isEmpty());
    }

    @Test
    void claimingConsumesTheEntry()
    {
        final CheckpointMoveGrace grace = new CheckpointMoveGrace();
        final UUID player = UUID.randomUUID();
        final UUID cityId = UUID.randomUUID();
        grace.record(player, new PendingMove(UUID.randomUUID(), cityId, UUID.randomUUID(), 0.5f, null, CheckpointStatus.HELD, 1000L));

        grace.claim(player, cityId, 500L);

        assertTrue(grace.claim(player, cityId, 500L).isEmpty());
    }
}
