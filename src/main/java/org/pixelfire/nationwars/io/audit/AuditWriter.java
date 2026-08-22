package org.pixelfire.nationwars.io.audit;

import org.pixelfire.nationwars.io.WriterThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Appends {@link AuditEntry} records to {@code <auditDir>/YYYY-MM-DD.jsonl.gz}, one file per UTC day,
 * through the shared {@link WriterThread} so a caller never blocks on disk I/O.
 *
 * <p>Every write reads the day file's existing content, appends the new entry, and rewrites the whole
 * file as one fresh gzip stream, rather than holding a stream open across writes — costs O(entries²)
 * bytes per day, fine for a log that sees events per day rather than per tick, and in exchange the file
 * is always a valid, closed gzip member at rest, not just at day rollover or shutdown.
 *
 * <p>{@link #fileLock()} — not {@code WriterThread} — is the actual correctness guarantee.
 * {@code WriterThread} only serializes submissions while its queue isn't full; once saturated, its
 * caller-runs fallback executes a write on the calling thread while the dedicated writer thread may
 * still be draining other queued writes, so two threads can truncate-and-rewrite the same file at once.
 * {@link AuditIndex}'s startup rebuild reads these same files from an unrelated worker-pool thread and
 * has the identical exposure. {@link #fileLock()} is shared across all three paths for exactly that
 * reason.
 */
public final class AuditWriter
{
    private final Path auditDir;
    private final WriterThread writer;
    private final Lock fileLock = new ReentrantLock();

    public AuditWriter(final Path auditDir, final WriterThread writer)
    {
        this.auditDir = auditDir;
        this.writer = writer;
    }

    /**
     * The lock guarding every read or write of a file under this writer's audit directory. Share this
     * with anything else that reads those files directly (see {@link AuditIndex#rebuildAsync}) so it
     * can never race a write, including one running via {@link WriterThread}'s caller-runs fallback.
     */
    public Lock fileLock()
    {
        return fileLock;
    }

    /**
     * Enqueues an entry for appending. Returns immediately; the entry is not necessarily on disk yet
     * when this method returns.
     */
    public void append(final AuditEntry entry)
    {
        writer.submit(() ->
        {
            fileLock.lock();
            try
            {
                appendOnWriterThread(entry);
            }
            finally
            {
                fileLock.unlock();
            }
        });
    }

    /**
     * Reads one full entry back and delivers it via {@code resultExecutor} — typically
     * {@code server::execute} so a command handler only ever touches the result on the main thread.
     */
    public void readFull(final AuditIndexEntry summary, final Executor resultExecutor, final Consumer<AuditEntry> onResult)
    {
        writer.submit(() ->
        {
            fileLock.lock();
            try
            {
                AuditReader.readFull(auditDir, summary).ifPresent(entry -> resultExecutor.execute(() -> onResult.accept(entry)));
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException("failed to read audit entry " + summary.entryId(), e);
            }
            finally
            {
                fileLock.unlock();
            }
        });
    }

    private void appendOnWriterThread(final AuditEntry entry)
    {
        try
        {
            Files.createDirectories(auditDir);
            final File file = AuditFiles.fileFor(auditDir, AuditFiles.dayOf(entry.timestamp()));

            final List<String> lines = new ArrayList<>(file.exists() ? AuditFiles.readAllLines(file) : List.of());
            lines.add(AuditEntryJson.toJsonLine(entry));

            writeAll(file, lines);
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException("failed to append audit entry " + entry.entryId(), e);
        }
    }

    private static void writeAll(final File file, final List<String> lines) throws IOException
    {
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file, false)), StandardCharsets.UTF_8))
        {
            for (final String line : lines)
            {
                out.write(line);
                out.write('\n');
            }
        }
    }
}
