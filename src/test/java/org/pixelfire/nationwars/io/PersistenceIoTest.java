package org.pixelfire.nationwars.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceIoTest
{
    @TempDir
    File tempDir;

    @Test
    void loadingAMissingFileProducesAFreshInstance() throws IOException
    {
        final NationWarsSavedData data = PersistenceIo.load(new File(tempDir, "does-not-exist.dat"));

        assertEquals("", data.dummyPayload());
    }

    @Test
    void aDummyPayloadSurvivesASaveAndReloadCycleThroughTheWriterThread() throws IOException, InterruptedException
    {
        final File file = new File(tempDir, "nationwars.dat");

        try (WriterThread writer = new WriterThread(16))
        {
            final PersistenceIo io = new PersistenceIo(writer);

            final NationWarsSavedData data = new NationWarsSavedData();
            data.setDummyPayload("a dummy payload surviving a save/reload cycle");

            io.save(data, file);

            // close() drains the queue, so the write is guaranteed to have happened by the time it returns.
        }

        final NationWarsSavedData reloaded = PersistenceIo.load(file);
        assertEquals("a dummy payload surviving a save/reload cycle", reloaded.dummyPayload());
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

        try (WriterThread writer = new WriterThread(1))
        {
            final PersistenceIo io = new PersistenceIo(writer);

            writer.submit(() -> awaitQuietly(blockFirstWrite));
            // Fills the sole queue slot; the writer is now fully saturated.
            writer.submit(() -> { });

            final NationWarsSavedData data = new NationWarsSavedData();
            data.setDummyPayload("saved under saturation");

            io.save(data, file);

            // The caller-runs fallback means the write above already happened synchronously.
            assertTrue(file.exists(), "the save should have landed on disk synchronously, not been dropped");
            final NationWarsSavedData reloaded = PersistenceIo.load(file);
            assertEquals("saved under saturation", reloaded.dummyPayload());

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
