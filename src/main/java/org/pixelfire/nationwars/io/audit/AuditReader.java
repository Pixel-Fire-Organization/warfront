package org.pixelfire.nationwars.io.audit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads one full {@link AuditEntry} (including {@code before}/{@code after}, which the index doesn't
 * keep) back off disk. {@link AuditIndex} already knows which entries exist and when, so this only
 * needs a summary to find the right day file and confirm the id inside it.
 */
public final class AuditReader
{
    private AuditReader()
    {
    }

    public static Optional<AuditEntry> readFull(final Path auditDir, final AuditIndexEntry summary) throws IOException
    {
        final File file = AuditFiles.fileFor(auditDir, AuditFiles.dayOf(summary.timestamp()));
        if (!file.exists())
        {
            return Optional.empty();
        }
        for (final String line : AuditFiles.readAllLines(file))
        {
            final AuditEntry entry = AuditEntryJson.fromJsonLine(line);
            if (entry.entryId().equals(summary.entryId()))
            {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
