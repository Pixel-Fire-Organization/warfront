package org.pixelfire.nationwars.state;

/**
 * Every input {@link FoundingPreconditions#check} needs, snapshotted from the main thread (OPAC calls,
 * block/world reads) into primitives so the precondition logic itself stays a pure, world-free function.
 *
 * @param memberRankOrdinal        the founding player's {@code PartyMemberRank} ordinal in their nation
 * @param requiredRankOrdinal      {@code cityFoundRank}'s ordinal
 * @param nearestOtherCoreDistance horizontal distance, in blocks, to the closest other core in this
 *                                 dimension; {@code Double.MAX_VALUE} if none exists
 * @param secondsSinceLastFounded  time since this nation's {@code lastCityFoundedAt}
 */
public record FoundingContext(
        boolean playerInNation,
        int memberRankOrdinal,
        int requiredRankOrdinal,
        boolean dimensionEligible,
        boolean skyColumnClear,
        boolean surfaceRequirementMet,
        double nearestOtherCoreDistance,
        int minCoreDistance,
        int existingCityCount,
        int maxCitiesPerNation,
        int memberCount,
        int maxCitiesPerMember,
        long secondsSinceLastFounded,
        long cityFoundCooldownSeconds,
        boolean chunkClaimedByOtherNation,
        boolean nationLocked,
        boolean nationInUnsettledWar,
        boolean allowFoundingDuringWar)
{
}
