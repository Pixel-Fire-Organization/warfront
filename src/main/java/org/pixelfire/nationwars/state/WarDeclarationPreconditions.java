package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * The eleven war declaration checks, checked strictly in order so a rejection always names the first
 * one that actually failed.
 */
public final class WarDeclarationPreconditions
{
    private WarDeclarationPreconditions()
    {
    }

    public static Optional<WarDeclarationFailureReason> check(final WarDeclarationContext ctx)
    {
        if (!ctx.senderIsNationOwner())
        {
            return Optional.of(WarDeclarationFailureReason.NOT_NATION_OWNER);
        }
        if (!ctx.declarerHasAnyCity())
        {
            return Optional.of(WarDeclarationFailureReason.DECLARER_HAS_NO_CITY);
        }
        if (!ctx.targetExists())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_NOT_FOUND);
        }
        if (ctx.targetIsSelf())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_IS_SELF);
        }
        if (ctx.targetIsMutualAlly())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_IS_MUTUAL_ALLY);
        }
        if (!ctx.targetHasEligibleCity())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_HAS_NO_ELIGIBLE_CITY);
        }
        if (!ctx.targetWarReady())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_NOT_WAR_READY);
        }
        if (!ctx.declarerWarReady())
        {
            return Optional.of(WarDeclarationFailureReason.DECLARER_NOT_WAR_READY);
        }
        if (ctx.unsettledWarAlreadyExists())
        {
            return Optional.of(WarDeclarationFailureReason.UNSETTLED_WAR_ALREADY_EXISTS);
        }
        if (ctx.now() < ctx.cooldownExpiresAt())
        {
            return Optional.of(WarDeclarationFailureReason.COOLDOWN_ACTIVE);
        }
        if (ctx.declarerLocked())
        {
            return Optional.of(WarDeclarationFailureReason.DECLARER_LOCKED);
        }
        if (ctx.targetLocked())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_LOCKED);
        }
        if (ctx.declarerAtWarCap())
        {
            return Optional.of(WarDeclarationFailureReason.DECLARER_AT_WAR_CAP);
        }
        if (ctx.targetAtWarCap())
        {
            return Optional.of(WarDeclarationFailureReason.TARGET_AT_WAR_CAP);
        }
        if (!ctx.withinWarWindow())
        {
            return Optional.of(WarDeclarationFailureReason.OUTSIDE_WAR_WINDOW);
        }
        return Optional.empty();
    }
}
