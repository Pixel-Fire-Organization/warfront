package org.pixelfire.nationwars.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Common shape every packet in {@code nationwars:main} shares: encode itself onto the wire. Decoding is
 * a static factory per packet type (registered individually in {@link NationWarsNetwork}), matching
 * {@link net.minecraftforge.network.simple.SimpleChannel.MessageBuilder}'s functional-interface style.
 */
public interface NationWarsPacket
{
    void encode(FriendlyByteBuf buf);
}
