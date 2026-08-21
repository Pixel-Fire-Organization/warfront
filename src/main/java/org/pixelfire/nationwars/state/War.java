package org.pixelfire.nationwars.state;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @param declaredAt, activeAt   {@code activeAt} is 0 until the war first reaches {@code ACTIVE}
 * @param warExpiresAt           wall-clock deadline; never pauses, including through {@code SUSPENDED}
 * @param suspendedSince         0 unless currently {@code SUSPENDED}
 * @param contestedTimeMs        reserved for capture bookkeeping (Stage 16); unused until then
 * @param settlementDeadline     0 if the backstop is disabled
 * @param outcome                {@code null} until the war reaches a terminal outcome
 * @param memberTargetableAt     a cascaded ally's own {@code PREPARATION} deadline, keyed by nation id;
 *                               absent for the two primaries and anyone already past it — not part of
 *                               the original data model, added because a pending member's private prep
 *                               window can't be timed without it
 *
 *                               <p>{@code stagedSettlement}/{@code appliedSettlement} from the data model
 *                               aren't representable yet — there is no {@code PeaceSettlement} type until
 *                               settlement is implemented — and are added then rather than typed as
 *                               {@code Object} in the meantime.
 */
public record War(
        UUID warId,
        Coalition attackers,
        Coalition defenders,
        WarPhase phase,
        long declaredAt,
        long activeAt,
        long warExpiresAt,
        Set<UUID> targetCityIds,
        Set<UUID> occupiedCityIds,
        Map<UUID, Long> warScore,
        long suspendedSince,
        long contestedTimeMs,
        long settlementDeadline,
        WarOutcome outcome,
        Map<UUID, Long> memberTargetableAt)
{
}
