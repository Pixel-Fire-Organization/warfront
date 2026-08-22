package org.pixelfire.nationwars.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyColumnScannerTest
{
    @Test
    void aSnapshotOfAllAirAnalyzesAsClear()
    {
        final boolean[][] allAir = { { true, true, true }, { true, true, true } };

        assertTrue(SkyColumnScanner.analyze(new SkyColumnScanner.ColumnSnapshot(allAir)));
    }

    @Test
    void oneObstructedBlockAnalyzesAsNotClear()
    {
        final boolean[][] oneObstruction = { { true, true, true }, { true, false, true } };

        assertFalse(SkyColumnScanner.analyze(new SkyColumnScanner.ColumnSnapshot(oneObstruction)));
    }

    @Test
    void anEmptySnapshotAnalyzesAsClear()
    {
        final boolean[][] empty = { { }, { } };

        assertTrue(SkyColumnScanner.analyze(new SkyColumnScanner.ColumnSnapshot(empty)));
    }
}
