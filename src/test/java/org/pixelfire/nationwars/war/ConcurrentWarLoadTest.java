package org.pixelfire.nationwars.war;

import org.junit.jupiter.api.Test;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Populates {@code maxConcurrentWars} (50, by default) directly into a {@link NationRegistry} and times
 * the per-war decision logic every tick listener in this mod repeats — {@link WarScore#applyAward} under
 * the same striped-lock path {@code CaptureTickListener}/{@code WarLifecycleListener} use — as a stand-in
 * for the spec's tick budget.
 *
 * <p>This cannot include the parts of a real tick that call into OPAC or the loaded world (chunk
 * presence checks, claim queries): those need a running server and real players, which this
 * environment cannot provide. What it does measure is whether the registry-level iteration and
 * striped-lock contention across 50 concurrent wars stays cheap — the part that scales with
 * {@code maxConcurrentWars} specifically, as opposed to the part that scales with player count.
 */
class ConcurrentWarLoadTest
{
    private static final int WAR_COUNT = 50;

    @Test
    void fiftyConcurrentWarsScoreAwardsCompleteWellWithinAPerTickBudget()
    {
        final NationRegistry registry = new NationRegistry(64);
        final List<UUID> warIds = new ArrayList<>();
        final List<UUID> nationIds = new ArrayList<>();

        for (int i = 0; i < WAR_COUNT; i++)
        {
            final UUID attackerPrimary = UUID.randomUUID();
            final UUID defenderPrimary = UUID.randomUUID();
            final War war = new War(UUID.randomUUID(), Coalition.ofPrimary(attackerPrimary), Coalition.ofPrimary(defenderPrimary),
                    WarPhase.ACTIVE, 0L, 0L, Long.MAX_VALUE, Set.of(), Set.of(), Map.of(), 0L, 0L, 0L, null, Map.of());
            registry.wars().put(war.warId(), war);
            warIds.add(war.warId());
            nationIds.add(attackerPrimary);
        }

        final long startNanos = System.nanoTime();
        for (int i = 0; i < warIds.size(); i++)
        {
            WarScore.award(registry, warIds.get(i), nationIds.get(i), 10L);
        }
        final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertTrue(elapsedMs < 50, "expected " + WAR_COUNT + " war-score awards to complete in well under 50ms, took " + elapsedMs + "ms");
        for (int i = 0; i < warIds.size(); i++)
        {
            assertTrue(registry.wars().get(warIds.get(i)).warScore().getOrDefault(nationIds.get(i), 0L) == 10L);
        }
    }
}
