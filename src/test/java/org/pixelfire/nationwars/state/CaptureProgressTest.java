package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureProgressTest
{
    private static final double BASE_RATE = 1.0 / 45.0;
    private static final double RECOVERY_RATE = 1.0 / 20.0;
    private static final double DECAY_RATE = 1.0 / 90.0;
    private static final double STACK_BONUS = 0.5;
    private static final double STACK_CAP = 3.0;

    @Test
    void attackersAloneIncreaseProgress()
    {
        final float result = CaptureProgress.step(0f, 1, 0, 45.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(1.0f, result, 0.001);
    }

    @Test
    void bothPresentFreezesProgress()
    {
        final float result = CaptureProgress.step(0.5f, 2, 1, 100.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(0.5f, result, 0.001);
    }

    @Test
    void defendersAloneRecoverProgress()
    {
        final float result = CaptureProgress.step(0.5f, 0, 1, 10.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(0.0f, result, 0.001);
    }

    @Test
    void nobodyPresentDecaysProgress()
    {
        final float result = CaptureProgress.step(1.0f, 0, 0, 90.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(0.0f, result, 0.001);
    }

    @Test
    void progressNeverExceedsOne()
    {
        final float result = CaptureProgress.step(0.99f, 5, 0, 1000.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(1.0f, result, 0.001);
    }

    @Test
    void progressNeverGoesNegative()
    {
        final float result = CaptureProgress.step(0.01f, 0, 1, 1000.0, BASE_RATE, RECOVERY_RATE, DECAY_RATE, STACK_BONUS, STACK_CAP);

        assertEquals(0.0f, result, 0.001);
    }

    @Test
    void attackerWeightScalesWithStackBonusUpToCap()
    {
        assertEquals(1.0, CaptureProgress.attackerWeight(1, STACK_BONUS, STACK_CAP), 0.001);
        assertEquals(1.5, CaptureProgress.attackerWeight(2, STACK_BONUS, STACK_CAP), 0.001);
        assertEquals(2.0, CaptureProgress.attackerWeight(3, STACK_BONUS, STACK_CAP), 0.001);
        assertEquals(STACK_CAP, CaptureProgress.attackerWeight(10, STACK_BONUS, STACK_CAP), 0.001);
    }

    @Test
    void statusIsContestedWhenBothPresent()
    {
        assertEquals(CheckpointStatus.CONTESTED, CaptureProgress.statusFor(1, 1));
    }

    @Test
    void statusIsCapturingWhenOnlyAttackersPresent()
    {
        assertEquals(CheckpointStatus.CAPTURING, CaptureProgress.statusFor(2, 0));
    }

    @Test
    void statusIsHeldOtherwise()
    {
        assertEquals(CheckpointStatus.HELD, CaptureProgress.statusFor(0, 0));
        assertEquals(CheckpointStatus.HELD, CaptureProgress.statusFor(0, 3));
    }
}
