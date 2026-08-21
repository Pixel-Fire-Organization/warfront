package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S: the packet-native equivalent of {@code /war declare <nation>} — the server re-runs the exact
 * same {@link org.pixelfire.nationwars.war.WarDeclarationService} checks either entry point uses.
 */
public record DeclareWarPacket(CompoundTag data) implements NationWarsPacket
{
    public static DeclareWarPacket of(final String targetNationName)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putString("targetNationName", targetNationName);
        return new DeclareWarPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static DeclareWarPacket decode(final FriendlyByteBuf buf)
    {
        return new DeclareWarPacket(buf.readNbt());
    }

    public String targetNationName()
    {
        return data.getString("targetNationName");
    }
}
