package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S: asks the server for a fresh {@link SyncCityPacket} for one named city, e.g. when the client
 * opens a city info panel it has no cached state for yet.
 */
public record RequestCityInfoPacket(CompoundTag data) implements NationWarsPacket
{
    public static RequestCityInfoPacket of(final String cityName)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putString("cityName", cityName);
        return new RequestCityInfoPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static RequestCityInfoPacket decode(final FriendlyByteBuf buf)
    {
        return new RequestCityInfoPacket(buf.readNbt());
    }

    public String cityName()
    {
        return data.getString("cityName");
    }
}
