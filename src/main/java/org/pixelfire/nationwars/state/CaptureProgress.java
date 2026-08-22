package org.pixelfire.nationwars.state;

/**
 * The four-way progress rule from spec's capture table, plus the attacker stacking weight. Pure over
 * counts and a time delta so it's testable without a running server; clamped to [0, 1] since a caller
 * only cares about crossing 1 (flip) or sitting at 0.
 */
public final class CaptureProgress
{
    private CaptureProgress()
    {
    }

    public static double attackerWeight(final int attackers, final double attackerStackBonus, final double attackerStackCap)
    {
        final double weight = 1 + attackerStackBonus * (attackers - 1);
        return Math.min(Math.max(weight, 0.0), attackerStackCap);
    }

    public static float step(final float progress, final int attackers, final int defenders, final double dtSeconds,
            final double baseCaptureRate, final double defenderRecoveryRate, final double decayRate,
            final double attackerStackBonus, final double attackerStackCap)
    {
        final double delta;
        if (attackers > 0 && defenders == 0)
        {
            delta = baseCaptureRate * attackerWeight(attackers, attackerStackBonus, attackerStackCap) * dtSeconds;
        }
        else if (attackers > 0)
        {
            delta = 0.0;
        }
        else if (defenders > 0)
        {
            delta = -defenderRecoveryRate * dtSeconds;
        }
        else
        {
            delta = -decayRate * dtSeconds;
        }
        return (float) Math.min(1.0, Math.max(0.0, progress + delta));
    }

    public static CheckpointStatus statusFor(final int attackers, final int defenders)
    {
        if (attackers > 0 && defenders > 0)
        {
            return CheckpointStatus.CONTESTED;
        }
        if (attackers > 0)
        {
            return CheckpointStatus.CAPTURING;
        }
        return CheckpointStatus.HELD;
    }
}
