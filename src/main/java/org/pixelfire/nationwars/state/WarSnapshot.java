package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Round-trips a {@link War} (including its two {@link Coalition}s) through NBT for persistence.
 */
public final class WarSnapshot
{
    private WarSnapshot()
    {
    }

    public static CompoundTag write(final War war)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", war.warId());
        tag.put("attackers", writeCoalition(war.attackers()));
        tag.put("defenders", writeCoalition(war.defenders()));
        tag.putString("phase", war.phase().name());
        tag.putLong("declaredAt", war.declaredAt());
        tag.putLong("activeAt", war.activeAt());
        tag.putLong("warExpiresAt", war.warExpiresAt());
        tag.put("targetCityIds", writeUuidSet(war.targetCityIds()));
        tag.put("occupiedCityIds", writeUuidSet(war.occupiedCityIds()));
        tag.put("warScore", writeUuidLongMap(war.warScore()));
        tag.putLong("suspendedSince", war.suspendedSince());
        tag.putLong("contestedTimeMs", war.contestedTimeMs());
        tag.putLong("settlementDeadline", war.settlementDeadline());
        if (war.outcome() != null)
        {
            tag.putString("outcome", war.outcome().name());
        }
        tag.put("memberTargetableAt", writeUuidLongMap(war.memberTargetableAt()));
        return tag;
    }

    public static War read(final CompoundTag tag)
    {
        return new War(
                tag.getUUID("warId"),
                readCoalition(tag.getCompound("attackers")),
                readCoalition(tag.getCompound("defenders")),
                WarPhase.valueOf(tag.getString("phase")),
                tag.getLong("declaredAt"),
                tag.getLong("activeAt"),
                tag.getLong("warExpiresAt"),
                readUuidSet(tag.getList("targetCityIds", Tag.TAG_STRING)),
                readUuidSet(tag.getList("occupiedCityIds", Tag.TAG_STRING)),
                readUuidLongMap(tag.getList("warScore", Tag.TAG_COMPOUND)),
                tag.getLong("suspendedSince"),
                tag.getLong("contestedTimeMs"),
                tag.getLong("settlementDeadline"),
                tag.contains("outcome") ? WarOutcome.valueOf(tag.getString("outcome")) : null,
                readUuidLongMap(tag.getList("memberTargetableAt", Tag.TAG_COMPOUND)));
    }

    private static CompoundTag writeCoalition(final Coalition coalition)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("primaryNationId", coalition.primaryNationId());
        tag.put("members", writeUuidSet(coalition.members()));
        final ListTag pending = new ListTag();
        for (final PendingEntry entry : coalition.pendingMembers().values())
        {
            final CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("nationId", entry.nationId());
            entryTag.putLong("scheduledAt", entry.scheduledAt());
            entryTag.putString("reason", entry.reason());
            pending.add(entryTag);
        }
        tag.put("pendingMembers", pending);
        return tag;
    }

    private static Coalition readCoalition(final CompoundTag tag)
    {
        final Map<UUID, PendingEntry> pendingMembers = new HashMap<>();
        for (final Tag element : tag.getList("pendingMembers", Tag.TAG_COMPOUND))
        {
            final CompoundTag entryTag = (CompoundTag) element;
            final PendingEntry entry = new PendingEntry(entryTag.getUUID("nationId"), entryTag.getLong("scheduledAt"),
                    entryTag.getString("reason"));
            pendingMembers.put(entry.nationId(), entry);
        }
        return new Coalition(readUuidSet(tag.getList("members", Tag.TAG_STRING)), Map.copyOf(pendingMembers),
                tag.getUUID("primaryNationId"));
    }

    private static ListTag writeUuidSet(final Set<UUID> ids)
    {
        final ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static Set<UUID> readUuidSet(final ListTag list)
    {
        final Set<UUID> ids = new HashSet<>();
        for (final Tag element : list)
        {
            ids.add(UUID.fromString(element.getAsString()));
        }
        return Set.copyOf(ids);
    }

    private static ListTag writeUuidLongMap(final Map<UUID, Long> map)
    {
        final ListTag list = new ListTag();
        map.forEach((id, value) ->
        {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putLong("value", value);
            list.add(entry);
        });
        return list;
    }

    private static Map<UUID, Long> readUuidLongMap(final ListTag list)
    {
        final Map<UUID, Long> map = new HashMap<>();
        for (final Tag element : list)
        {
            final CompoundTag entry = (CompoundTag) element;
            map.put(entry.getUUID("id"), entry.getLong("value"));
        }
        return Map.copyOf(map);
    }
}
