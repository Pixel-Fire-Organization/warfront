package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * {@code /war join <warId> attackers}: the same cooldown and lock checks as a declaration, applied to
 * the joining nation only — the target's own war-readiness isn't re-checked, since joining an already
 * moving war isn't the same act as starting one.
 */
public final class WarJoinPreconditions
{
    private WarJoinPreconditions()
    {
    }

    public static Optional<WarJoinFailureReason> check(final WarJoinContext ctx)
    {
        if (!ctx.senderIsNationOwner())
        {
            return Optional.of(WarJoinFailureReason.NOT_NATION_OWNER);
        }
        if (ctx.alreadyInThisWar())
        {
            return Optional.of(WarJoinFailureReason.ALREADY_IN_THIS_WAR);
        }
        if (!ctx.warJoinable())
        {
            return Optional.of(WarJoinFailureReason.WAR_NOT_JOINABLE);
        }
        if (ctx.unsettledWarAlreadyExists())
        {
            return Optional.of(WarJoinFailureReason.UNSETTLED_WAR_ALREADY_EXISTS);
        }
        if (ctx.now() < ctx.cooldownExpiresAt())
        {
            return Optional.of(WarJoinFailureReason.COOLDOWN_ACTIVE);
        }
        if (ctx.joinerLocked())
        {
            return Optional.of(WarJoinFailureReason.JOINER_LOCKED);
        }
        if (ctx.joinerAtWarCap())
        {
            return Optional.of(WarJoinFailureReason.JOINER_AT_WAR_CAP);
        }
        return Optional.empty();
    }
}
