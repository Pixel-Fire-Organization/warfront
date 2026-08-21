package org.pixelfire.nationwars.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses a {@link War} rather than a {@code City}/{@code Checkpoint} — see {@link
 * NationWarsSavedDataTest}'s docstring for why those need a fully bootstrapped game to construct.
 */
class PersistenceIoTest
{
    @TempDir
    File tempDir;

    private static War testWar()
    {
        final UUID primary = UUID.randomUUID();
        final Coalition solo = new Coalition(Set.of(primary), Map.of(), primary);
        return new War(UUID.randomUUID(), solo, solo, WarPhase.ACTIVE, 0L, 0L, 100L, Set.of(), Set.of(), Map.of(),
                0L, 0L, 0L, WarOutcome.TIMEOUT, Map.of());
    }

    @Test
    void loadingAMissingFileProducesAFreshInstance() throws IOException
    {
        final NationWarsSavedData data = PersistenceIo.load(new File(tempDir, "does-not-exist.dat"));
        final NationRegistry registry = new NationRegistry(4);

        data.applyTo(registry);

        assertTrue(registry.wars().isEmpty());
    }

    @Test
    void aWarSurvivesASaveAndReloadCycleThroughTheWriterThread() throws IOException, InterruptedException
    {
        final File file = new File(tempDir, "nationwars.dat");
        final War war = testWar();
        final NationRegistry registry = new NationRegistry(4);
        registry.wars().put(war.warId(), war);

        try (WriterThread writer = new WriterThread(16))
        {
            final PersistenceIo io = new PersistenceIo(writer);

            final NationWarsSavedData data = new NationWarsSavedData();
            data.syncFromRegistry(registry);

            io.save(data, file);

            // close() drains the queue, so the write is guaranteed to have happened by the time it returns.
        }

        final NationWarsSavedData reloaded = PersistenceIo.load(file);
        final NationRegistry reloadedRegistry = new NationRegistry(4);
        reloaded.applyTo(reloadedRegistry);

        assertEquals(war, reloadedRegistry.wars().get(war.warId()));
    }

    /**
     * Saturates the writer's queue on purpose, then saves through it anyway. The save must still land
     * on disk — synchronously, before {@code save()} returns — rather than being silently dropped.
     */
    @Test
    void aSaveIsNotDroppedWhenTheWriterQueueIsSaturated() throws IOException, InterruptedException
    {
        final File file = new File(tempDir, "nationwars.dat");
        final CountDownLatch blockFirstWrite = new CountDownLatch(1);
        final War war = testWar();
        final NationRegistry registry = new NationRegistry(4);
        registry.wars().put(war.warId(), war);

        try (WriterThread writer = new WriterThread(1))
        {
            final PersistenceIo io = new PersistenceIo(writer);

            writer.submit(() -> awaitQuietly(blockFirstWrite));
            // Fills the sole queue slot; the writer is now fully saturated.
            writer.submit(() -> { });

            final NationWarsSavedData data = new NationWarsSavedData();
            data.syncFromRegistry(registry);

            io.save(data, file);

            // The caller-runs fallback means the write above already happened synchronously.
            assertTrue(file.exists(), "the save should have landed on disk synchronously, not been dropped");
            final NationWarsSavedData reloaded = PersistenceIo.load(file);
            final NationRegistry reloadedRegistry = new NationRegistry(4);
            reloaded.applyTo(reloadedRegistry);
            assertEquals(war, reloadedRegistry.wars().get(war.warId()));

            blockFirstWrite.countDown();
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            latch.await(5, TimeUnit.SECONDS);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
