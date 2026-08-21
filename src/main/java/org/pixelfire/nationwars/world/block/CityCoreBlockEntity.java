package org.pixelfire.nationwars.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A block entity with no matching {@code City} record in the registry is inert; only the founding
 * command path (later stages) creates one and assigns a {@code cityId}.
 */
public class CityCoreBlockEntity extends BlockEntity
{
    private static final String KEY_CITY_ID = "cityId";

    private UUID cityId;

    public CityCoreBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(NationWarsBlockEntities.CITY_CORE.get(), pos, state);
    }

    public UUID cityId()
    {
        return cityId;
    }

    public void setCityId(final UUID cityId)
    {
        this.cityId = cityId;
        setChanged();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (cityId != null)
        {
            tag.putUUID(KEY_CITY_ID, cityId);
        }
    }

    @Override
    public void load(final CompoundTag tag)
    {
        super.load(tag);
        cityId = tag.hasUUID(KEY_CITY_ID) ? tag.getUUID(KEY_CITY_ID) : null;
    }
}
