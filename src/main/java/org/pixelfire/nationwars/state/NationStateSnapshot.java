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
 * Round-trips a {@link NationState} through NBT for persistence.
 */
public final class NationStateSnapshot
{
    private NationStateSnapshot()
    {
    }

    public static CompoundTag write(final NationState state)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("nationId", state.nationId());
        final ListTag cityIds = new ListTag();
        state.cityIds().forEach(id -> cityIds.add(StringTag.valueOf(id.toString())));
        tag.put("cityIds", cityIds);
        if (state.capitalCityId() != null)
        {
            tag.putUUID("capitalCityId", state.capitalCityId());
        }
        final ListTag activeWarIds = new ListTag();
        state.activeWarIds().forEach(id -> activeWarIds.add(StringTag.valueOf(id.toString())));
        tag.put("activeWarIds", activeWarIds);
        final ListTag warCooldowns = new ListTag();
        state.warCooldowns().forEach((nationId, expiresAt) ->
        {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("nationId", nationId);
            entry.putLong("expiresAt", expiresAt);
            warCooldowns.add(entry);
        });
        tag.put("warCooldowns", warCooldowns);
        tag.putLong("lastCityFoundedAt", state.lastCityFoundedAt());
        if (state.lockedByWarId() != null)
        {
            tag.putUUID("lockedByWarId", state.lockedByWarId());
        }
        return tag;
    }

    public static NationState read(final CompoundTag tag)
    {
        final Set<UUID> cityIds = new HashSet<>();
        for (final Tag element : tag.getList("cityIds", Tag.TAG_STRING))
        {
            cityIds.add(UUID.fromString(element.getAsString()));
        }
        final Set<UUID> activeWarIds = new HashSet<>();
        for (final Tag element : tag.getList("activeWarIds", Tag.TAG_STRING))
        {
            activeWarIds.add(UUID.fromString(element.getAsString()));
        }
        final Map<UUID, Long> warCooldowns = new HashMap<>();
        for (final Tag element : tag.getList("warCooldowns", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) element;
            warCooldowns.put(entry.getUUID("nationId"), entry.getLong("expiresAt"));
        }
        return new NationState(
                tag.getUUID("nationId"),
                Set.copyOf(cityIds),
                tag.contains("capitalCityId") ? tag.getUUID("capitalCityId") : null,
                Set.copyOf(activeWarIds),
                Map.copyOf(warCooldowns),
                tag.getLong("lastCityFoundedAt"),
                tag.contains("lockedByWarId") ? tag.getUUID("lockedByWarId") : null);
    }
}
