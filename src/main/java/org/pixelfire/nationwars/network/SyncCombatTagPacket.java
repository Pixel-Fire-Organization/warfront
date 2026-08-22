package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S2C: the receiving player's own combat-tag start and countdown.
 */
public record SyncCombatTagPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncCombatTagPacket of(final boolean tagged, final long expiresTick, final long currentTick)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("tagged", tagged);
        tag.putLong("ticksRemaining", tagged ? Math.max(0L, expiresTick - currentTick) : 0L);
        return new SyncCombatTagPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncCombatTagPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncCombatTagPacket(buf.readNbt());
    }
}
