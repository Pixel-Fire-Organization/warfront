package org.pixelfire.nationwars.war;

import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.AllianceCascade;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.PendingEntry;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the defender coalition at declaration time: the target plus every mutual ally out to
 * {@code allianceCascadeDepth} hops. A cascaded ally with no Ready player right now joins
 * {@code pendingMembers} instead of {@code members} — entry triggers later when one of its citizens logs
 * in and clears the shield. Attacker-side allies are never auto-enrolled; only voluntary
 * {@code /war join} adds to that side.
 */
public final class CoalitionAssembly
{
    private CoalitionAssembly()
    {
    }

    public static Coalition assembleDefenders(final MinecraftServer server, final UUID targetNationId, final long now)
    {
        final int cascadeDepth = NationWarsConfig.ALLIANCE_CASCADE_DEPTH.get();
        final Set<UUID> cascaded = AllianceCascade.expand(targetNationId, cascadeDepth,
                nationId -> OpacNations.mutualAlliesOf(server, nationId));

        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();

        final Map<UUID, PendingEntry> pendingMembers = new HashMap<>();
        final Set<UUID> members = new HashSet<>();
        members.add(targetNationId);

        for (final UUID allyId : cascaded)
        {
            if (Readiness.isNationReady(server, allyId, tracker, currentTick, afkThresholdTicks))
            {
                members.add(allyId);
            }
            else
            {
                pendingMembers.put(allyId, new PendingEntry(allyId, now, "ALLY_OF " + targetNationId));
            }
        }

        return new Coalition(Set.copyOf(members), Map.copyOf(pendingMembers), targetNationId);
    }
}
