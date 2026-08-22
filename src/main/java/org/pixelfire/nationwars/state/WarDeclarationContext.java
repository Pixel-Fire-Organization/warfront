package org.pixelfire.nationwars.state;

/**
 * Every input {@link WarDeclarationPreconditions#check} needs, snapshotted into primitives the same way
 * {@link FoundingContext} is. Fields for a nonexistent target (e.g. {@code targetIsSelf}) can be any
 * value when {@code targetExists} is false, since the checker never reaches them in that case.
 */
public record WarDeclarationContext(
        boolean senderIsNationOwner,
        boolean declarerHasAnyCity,
        boolean targetExists,
        boolean targetIsSelf,
        boolean targetIsMutualAlly,
        boolean targetHasEligibleCity,
        boolean targetWarReady,
        boolean declarerWarReady,
        boolean unsettledWarAlreadyExists,
        long now,
        long cooldownExpiresAt,
        boolean declarerLocked,
        boolean targetLocked,
        boolean declarerAtWarCap,
        boolean targetAtWarCap,
        boolean withinWarWindow)
{
}
