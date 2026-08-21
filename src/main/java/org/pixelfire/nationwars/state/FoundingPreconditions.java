package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * The ten founding preconditions (spec §8.1), checked strictly in order so a rejection always names
 * the first one that actually failed.
 */
public final class FoundingPreconditions
{
    private FoundingPreconditions()
    {
    }

    public static Optional<FoundingFailureReason> check(final FoundingContext ctx)
    {
        if (!ctx.playerInNation())
        {
            return Optional.of(FoundingFailureReason.NOT_IN_A_NATION);
        }
        if (ctx.memberRankOrdinal() < ctx.requiredRankOrdinal())
        {
            return Optional.of(FoundingFailureReason.RANK_TOO_LOW);
        }
        if (!ctx.dimensionEligible())
        {
            return Optional.of(FoundingFailureReason.DIMENSION_INELIGIBLE);
        }
        if (!ctx.skyColumnClear())
        {
            return Optional.of(FoundingFailureReason.SKY_COLUMN_OBSTRUCTED);
        }
        if (!ctx.surfaceRequirementMet())
        {
            return Optional.of(FoundingFailureReason.SURFACE_REQUIREMENT_NOT_MET);
        }
        if (ctx.nearestOtherCoreDistance() < ctx.minCoreDistance())
        {
            return Optional.of(FoundingFailureReason.TOO_CLOSE_TO_ANOTHER_CORE);
        }
        final int cityCap = Math.min(ctx.maxCitiesPerNation(), ctx.maxCitiesPerMember() * ctx.memberCount());
        if (ctx.existingCityCount() >= cityCap)
        {
            return Optional.of(FoundingFailureReason.CITY_LIMIT_REACHED);
        }
        if (ctx.secondsSinceLastFounded() < ctx.cityFoundCooldownSeconds())
        {
            return Optional.of(FoundingFailureReason.FOUNDING_COOLDOWN_ACTIVE);
        }
        if (ctx.chunkClaimedByOtherNation())
        {
            return Optional.of(FoundingFailureReason.CHUNK_ALREADY_CLAIMED);
        }
        if (ctx.nationLocked())
        {
            return Optional.of(FoundingFailureReason.NATION_LOCKED);
        }
        if (ctx.nationInUnsettledWar() && !ctx.allowFoundingDuringWar())
        {
            return Optional.of(FoundingFailureReason.NATION_AT_WAR);
        }
        return Optional.empty();
    }
}
