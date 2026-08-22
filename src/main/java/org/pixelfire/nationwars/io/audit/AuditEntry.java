package org.pixelfire.nationwars.io.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * One privileged action, with enough state to undo it later. Append-only: a correction is a new
 * entry, never an edit of this one.
 *
 * @param entryId       a ULID, sortable by creation time
 * @param actorUuid     {@code null} only for {@link ActorRole#SYSTEM} actions nobody triggered directly
 * @param actorName     stored separately from {@code actorUuid} so old entries stay readable after a
 *                      name change
 * @param before        a snapshot scoped to just what changed, before the action
 * @param after         the same scope, after the action
 * @param revertOf      if this entry is itself a revert, the {@code entryId} of the entry it reverts
 * @param revertedBy    the {@code entryId} of the entry that reverted this one, if any
 */
public record AuditEntry(
        String entryId,
        long timestamp,
        UUID actorUuid,
        String actorName,
        UUID actorNationId,
        ActorRole actorRole,
        AuditSource source,
        ResourceLocation actionType,
        List<UUID> targets,
        CompoundTag before,
        CompoundTag after,
        boolean reversible,
        String revertOf,
        String revertedBy)
{
    /**
     * A convenience for the common case: a freshly-created entry that is not itself a revert and has
     * not (yet) been reverted by anything.
     */
    public static AuditEntry of(final UUID actorUuid, final String actorName, final UUID actorNationId, final ActorRole actorRole,
            final AuditSource source, final ResourceLocation actionType, final List<UUID> targets,
            final CompoundTag before, final CompoundTag after, final boolean reversible)
    {
        return new AuditEntry(Ulid.generate(), System.currentTimeMillis(), actorUuid, actorName, actorNationId, actorRole,
                source, actionType, List.copyOf(targets), before, after, reversible, null, null);
    }
}
