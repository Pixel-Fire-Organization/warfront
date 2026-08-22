package org.pixelfire.nationwars.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierValidationTest
{
    private static final List<TierDefinition> DEFAULT_TIERS = List.of(
            new TierDefinition(5, 0, 1, 5),
            new TierDefinition(8, 128, 5, 8),
            new TierDefinition(13, 512, 8, 13),
            new TierDefinition(21, 2048, 13, 21));

    @Test
    void defaultLadderIsValid()
    {
        assertDoesNotThrow(() -> TierValidation.validateLadder(DEFAULT_TIERS));
    }

    @Test
    void ladderRejectsGapBetweenTiers()
    {
        final List<TierDefinition> tiers = List.of(
                new TierDefinition(5, 0, 1, 5),
                new TierDefinition(8, 128, 6, 8));

        final ConfigValidationException ex = assertThrows(ConfigValidationException.class, () -> TierValidation.validateLadder(tiers));
        assertTrue(ex.getMessage().contains("tier 2"), "expected the offending tier to be named: " + ex.getMessage());
    }

    @Test
    void ladderRejectsEmptyList()
    {
        assertThrows(ConfigValidationException.class, () -> TierValidation.validateLadder(List.of()));
    }

    @Test
    void ladderRejectsMinAboveMaxWithinATier()
    {
        final List<TierDefinition> tiers = List.of(new TierDefinition(5, 0, 6, 5));
        assertThrows(ConfigValidationException.class, () -> TierValidation.validateLadder(tiers));
    }

    @Test
    void defaultSpacingIsFeasible()
    {
        assertDoesNotThrow(() -> TierValidation.validateSpacingFeasibility(DEFAULT_TIERS, 3.0));
    }

    @Test
    void spacingRejectsTierThatCannotFitItsOwnMaximum()
    {
        // radius 5, maxCheckpoints 5 tolerates spacing up to ~5.88; 6 is infeasible.
        final List<TierDefinition> tiers = List.of(new TierDefinition(5, 0, 1, 5));

        final ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> TierValidation.validateSpacingFeasibility(tiers, 6.0));
        assertTrue(ex.getMessage().contains("tier 1"), "expected the offending tier to be named: " + ex.getMessage());
    }

    @Test
    void spacingIgnoresTiersWithAtMostOneCheckpoint()
    {
        final List<TierDefinition> tiers = List.of(new TierDefinition(1, 0, 1, 1));
        assertDoesNotThrow(() -> TierValidation.validateSpacingFeasibility(tiers, 1000.0));
    }

    @Test
    void minCoreDistanceUnchangedWhenAlreadyAboveFloor()
    {
        final AtomicReference<String> warning = new AtomicReference<>();
        final int result = TierValidation.clampMinCoreDistance(192, DEFAULT_TIERS, warning::set);

        assertEquals(192, result);
        assertNull(warning.get());
    }

    @Test
    void minCoreDistanceClampedWhenBelowFloor()
    {
        final AtomicReference<String> warning = new AtomicReference<>();
        // maxTierRadius = 21 -> floor = 2*21+8 = 50
        final int result = TierValidation.clampMinCoreDistance(40, DEFAULT_TIERS, warning::set);

        assertEquals(51, result);
        assertTrue(warning.get() != null && warning.get().contains("minCoreDistance"));
    }
}
