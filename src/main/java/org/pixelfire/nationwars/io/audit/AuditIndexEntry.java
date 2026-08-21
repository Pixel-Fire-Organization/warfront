package org.pixelfire.nationwars.io.audit;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Everything the in-memory index needs to answer a query without decompressing anything: enough to
 * list and filter entries, but not the {@code before}/{@code after} snapshots, which are the bulk of
 * an entry's size. Fetching those (for {@code /nationwars staff log show}) means going back to the
 * day file this summary points at.
 */
public record AuditIndexEntry(
        String entryId,
        long timestamp,
        UUID actorUuid,
        String actorName,
        ResourceLocation actionType,
        List<UUID> targets,
        boolean reversible)
{
    public static AuditIndexEntry summarize(final AuditEntry entry)
    {
        return new AuditIndexEntry(entry.entryId(), entry.timestamp(), entry.actorUuid(), entry.actorName(),
                entry.actionType(), entry.targets(), entry.reversible());
    }
}
