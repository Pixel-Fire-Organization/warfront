package org.pixelfire.nationwars.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Round-trips a {@link City} through NBT for persistence, mirroring {@link CheckpointSnapshot}.
 */
public final class CitySnapshot
{
    private CitySnapshot()
    {
    }

    public static CompoundTag write(final City city)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("cityId", city.cityId());
        tag.putString("name", city.name());
        tag.putUUID("ownerNationId", city.ownerNationId());
        tag.putUUID("founderNationId", city.founderNationId());
        tag.putString("dimension", city.dimension().location().toString());
        tag.putInt("posX", city.corePos().getX());
        tag.putInt("posY", city.corePos().getY());
        tag.putInt("posZ", city.corePos().getZ());
        tag.putInt("tier", city.tier());
        tag.putLong("bankedPayment", city.bankedPayment());
        final ListTag checkpointIds = new ListTag();
        city.checkpointIds().forEach(id -> checkpointIds.add(StringTag.valueOf(id.toString())));
        tag.put("checkpointIds", checkpointIds);
        tag.putString("state", city.state().name());
        if (city.occupiedByNationId() != null)
        {
            tag.putUUID("occupiedByNationId", city.occupiedByNationId());
        }
        tag.putLong("occupiedSince", city.occupiedSince());
        tag.putLong("occupationLockUntil", city.occupationLockUntil());
        tag.putLong("foundedAt", city.foundedAt());
        tag.putLong("lastTransferAt", city.lastTransferAt());
        tag.putInt("transferCount", city.transferCount());
        tag.putLong("pendingDisbandAt", city.pendingDisbandAt());
        tag.putLong("dormantSince", city.dormantSince());
        return tag;
    }

    public static City read(final CompoundTag tag)
    {
        final Set<UUID> checkpointIds = new HashSet<>();
        for (final Tag element : tag.getList("checkpointIds", Tag.TAG_STRING))
        {
            checkpointIds.add(UUID.fromString(element.getAsString()));
        }
        final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension")));
        return new City(
                tag.getUUID("cityId"),
                tag.getString("name"),
                tag.getUUID("ownerNationId"),
                tag.getUUID("founderNationId"),
                dimension,
                new BlockPos(tag.getInt("posX"), tag.getInt("posY"), tag.getInt("posZ")),
                tag.getInt("tier"),
                tag.getLong("bankedPayment"),
                Set.copyOf(checkpointIds),
                CityState.valueOf(tag.getString("state")),
                tag.contains("occupiedByNationId") ? tag.getUUID("occupiedByNationId") : null,
                tag.getLong("occupiedSince"),
                tag.getLong("occupationLockUntil"),
                tag.getLong("foundedAt"),
                tag.getLong("lastTransferAt"),
                tag.getInt("transferCount"),
                tag.getLong("pendingDisbandAt"),
                tag.getLong("dormantSince"));
    }
}
