package org.pixelfire.nationwars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.state.City;

/**
 * S2C: one city's HUD-relevant state (join or on change). {@code held}/{@code total} let the HUD show
 * "3/5 checkpoints" without the client needing its own copy of every {@link
 * org.pixelfire.nationwars.state.Checkpoint}.
 */
public record SyncCityPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncCityPacket of(final City city, final int heldCheckpoints, final int totalCheckpoints)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("cityId", city.cityId());
        tag.putString("name", city.name());
        tag.putUUID("ownerNationId", city.ownerNationId());
        tag.putInt("tier", city.tier());
        tag.putString("state", city.state().name());
        if (city.occupiedByNationId() != null)
        {
            tag.putUUID("occupiedByNationId", city.occupiedByNationId());
        }
        tag.putLong("occupationLockUntil", city.occupationLockUntil());
        tag.putInt("posX", city.corePos().getX());
        tag.putInt("posY", city.corePos().getY());
        tag.putInt("posZ", city.corePos().getZ());
        tag.putInt("heldCheckpoints", heldCheckpoints);
        tag.putInt("totalCheckpoints", totalCheckpoints);
        return new SyncCityPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncCityPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncCityPacket(buf.readNbt());
    }

    public java.util.UUID cityId()
    {
        return data.getUUID("cityId");
    }

    public String name()
    {
        return data.getString("name");
    }

    public java.util.UUID ownerNationId()
    {
        return data.getUUID("ownerNationId");
    }

    public int tier()
    {
        return data.getInt("tier");
    }

    public TierDefinition tierDefinition()
    {
        return NationWarsConfig.tiers.get(tier());
    }

    public String state()
    {
        return data.getString("state");
    }

    public java.util.UUID occupiedByNationId()
    {
        return data.contains("occupiedByNationId") ? data.getUUID("occupiedByNationId") : null;
    }

    public long occupationLockUntil()
    {
        return data.getLong("occupationLockUntil");
    }

    public BlockPos corePos()
    {
        return new BlockPos(data.getInt("posX"), data.getInt("posY"), data.getInt("posZ"));
    }

    public int heldCheckpoints()
    {
        return data.getInt("heldCheckpoints");
    }

    public int totalCheckpoints()
    {
        return data.getInt("totalCheckpoints");
    }
}
