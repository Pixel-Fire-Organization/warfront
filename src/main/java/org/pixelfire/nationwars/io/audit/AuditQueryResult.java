package org.pixelfire.nationwars.io.audit;

import java.util.List;

/**
 * The result of an {@link AuditIndex} query: either the index wasn't ready yet ({@link StillIndexing}),
 * or it was, and here are the (possibly empty) matches ({@link Entries}). A sealed type rather than an
 * empty list for "not ready yet" so a caller can't mistake "still indexing" for "no matches".
 */
public sealed interface AuditQueryResult
{
    record StillIndexing() implements AuditQueryResult
    {
    }

    record Entries(List<AuditIndexEntry> entries) implements AuditQueryResult
    {
    }
}
