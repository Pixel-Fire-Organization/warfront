package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarSnapshotTest
{
    @Test
    void roundTripsAWarWithPendingMembersScoresAndOutcome()
    {
        final UUID attackerPrimary = UUID.randomUUID();
        final UUID defenderPrimary = UUID.randomUUID();
        final UUID pendingAlly = UUID.randomUUID();
        final UUID cityId = UUID.randomUUID();

        final Coalition attackers = new Coalition(Set.of(attackerPrimary), Map.of(), attackerPrimary);
        final Coalition defenders = new Coalition(Set.of(defenderPrimary, pendingAlly),
                Map.of(pendingAlly, new PendingEntry(pendingAlly, 12345L, "alliance cascade")), defenderPrimary);

        final War war = new War(UUID.randomUUID(), attackers, defenders, WarPhase.ACTIVE, 1000L, 2000L, 900000L,
                Set.of(cityId), Set.of(), Map.of(attackerPrimary, 42L, defenderPrimary, -7L), 0L, 0L, 0L,
                WarOutcome.ATTACKER_TOTAL_VICTORY, Map.of(pendingAlly, 5000L));

        final War roundTripped = WarSnapshot.read(WarSnapshot.write(war));

        assertEquals(war, roundTripped);
    }

    @Test
    void roundTripsAWarWithNoOutcomeYet()
    {
        final UUID primary = UUID.randomUUID();
        final Coalition solo = new Coalition(Set.of(primary), Map.of(), primary);
        final War war = new War(UUID.randomUUID(), solo, solo, WarPhase.PREPARATION, 0L, 0L, 100L, Set.of(), Set.of(),
                Map.of(), 0L, 0L, 0L, null, Map.of());

        final War roundTripped = WarSnapshot.read(WarSnapshot.write(war));

        assertEquals(war, roundTripped);
    }
}
