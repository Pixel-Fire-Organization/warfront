package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S2C: the receiving player's own readiness state and shield countdown, sent on every state
 * transition.
 */
public record SyncReadinessPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncReadinessPacket of(final String state, final long shieldExpiresTick, final long currentTick)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putString("state", state);
        tag.putLong("shieldTicksRemaining", Math.max(0L, shieldExpiresTick - currentTick));
        return new SyncReadinessPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncReadinessPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncReadinessPacket(buf.readNbt());
    }
}
