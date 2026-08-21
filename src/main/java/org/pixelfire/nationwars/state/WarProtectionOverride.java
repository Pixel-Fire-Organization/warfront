package org.pixelfire.nationwars.state;

/**
 * The pure derived function from spec's scope table: allowed only during the {@code ACTIVE} phase, only
 * between opposing coalition members, only inside the claim union of a targeted city, only for an
 * action the config lists. Nothing here is stored — the caller re-evaluates it per event, so there is
 * nothing to restore if the server crashes mid-war.
 */
public final class WarProtectionOverride
{
    private WarProtectionOverride()
    {
    }

    public static boolean isAllowed(final WarProtectionContext ctx)
    {
        return ctx.warActive() && ctx.opposingCoalitions() && ctx.chunkInTargetCityClaims() && ctx.actionOverridden();
    }
}
