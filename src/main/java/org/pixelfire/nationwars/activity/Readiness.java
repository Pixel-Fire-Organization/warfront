package org.pixelfire.nationwars.activity;

import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.UUID;

/**
 * A nation is war-ready if any of its online members is Ready. Computed on demand, never cached.
 * Coalition readiness (any member nation war-ready) isn't implemented yet since no coalition/war record
 * exists to call it against — it's a one-line reduction over this once Stage 13/14 land.
 */
public final class Readiness
{
    private Readiness()
    {
    }

    public static boolean isNationReady(final MinecraftServer server, final UUID nationId, final ActivityTracker tracker,
            final long currentTick, final long afkThresholdTicks)
    {
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId);
        if (party == null)
        {
            return false;
        }
        return party.getOnlineMemberStream()
                .anyMatch(player -> tracker.stateOf(player.getUUID(), currentTick, afkThresholdTicks) == PlayerActivityState.READY);
    }
}
