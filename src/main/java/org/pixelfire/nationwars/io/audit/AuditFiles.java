package org.pixelfire.nationwars.io.audit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * File-naming and reading helpers shared by {@link AuditWriter} and {@link AuditIndex}. Every audit
 * day-file is, at rest between writer sessions, a single valid gzip member — never several
 * concatenated ones — specifically so this can use a plain {@link GZIPInputStream} rather than
 * needing to handle multi-member gzip streams, which the JDK's own {@code GZIPInputStream} does not
 * do on its own.
 */
final class AuditFiles
{
    private AuditFiles()
    {
    }

    static File fileFor(final Path auditDir, final LocalDate day)
    {
        return auditDir.resolve(day + ".jsonl.gz").toFile();
    }

    static LocalDate dayOf(final long timestampMillis)
    {
        return Instant.ofEpochMilli(timestampMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    static List<String> readAllLines(final File file) throws IOException
    {
        final List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.isBlank())
                {
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}
