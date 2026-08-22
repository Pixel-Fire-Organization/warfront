package org.pixelfire.nationwars.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Stores its own id and a cached {@code cityId} so lookups don't need to search every city for the
 * one that owns this checkpoint. Inert with no matching {@code Checkpoint} record, same as
 * {@link CityCoreBlockEntity}.
 */
public class CheckpointBlockEntity extends BlockEntity
{
    private static final String KEY_CHECKPOINT_ID = "checkpointId";
    private static final String KEY_CITY_ID = "cityId";

    private UUID checkpointId;
    private UUID cityId;

    public CheckpointBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(NationWarsBlockEntities.CHECKPOINT.get(), pos, state);
    }

    public UUID checkpointId()
    {
        return checkpointId;
    }

    public UUID cityId()
    {
        return cityId;
    }

    public void setIds(final UUID checkpointId, final UUID cityId)
    {
        this.checkpointId = checkpointId;
        this.cityId = cityId;
        setChanged();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (checkpointId != null)
        {
            tag.putUUID(KEY_CHECKPOINT_ID, checkpointId);
        }
        if (cityId != null)
        {
            tag.putUUID(KEY_CITY_ID, cityId);
        }
    }

    @Override
    public void load(final CompoundTag tag)
    {
        super.load(tag);
        checkpointId = tag.hasUUID(KEY_CHECKPOINT_ID) ? tag.getUUID(KEY_CHECKPOINT_ID) : null;
        cityId = tag.hasUUID(KEY_CITY_ID) ? tag.getUUID(KEY_CITY_ID) : null;
    }
}
