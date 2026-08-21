package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.War;

/**
 * S2C: a war's phase, deadline and target-city progress — sent on any change and every 30 s regardless,
 * so a HUD countdown stays accurate even if nothing else about the war moved.
 */
public record SyncWarStatePacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncWarStatePacket of(final War war)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", war.warId());
        tag.putString("phase", war.phase().name());
        tag.putLong("warExpiresAt", war.warExpiresAt());
        tag.putLong("suspendedSince", war.suspendedSince());
        tag.putLong("settlementDeadline", war.settlementDeadline());
        tag.putInt("targetCities", war.targetCityIds().size());
        tag.putInt("occupiedCities", war.occupiedCityIds().size());
        if (war.outcome() != null)
        {
            tag.putString("outcome", war.outcome().name());
        }
        return new SyncWarStatePacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncWarStatePacket decode(final FriendlyByteBuf buf)
    {
        return new SyncWarStatePacket(buf.readNbt());
    }
}
