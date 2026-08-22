package org.pixelfire.nationwars.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.capture.CaptureTickListener;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointSnapshot;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.world.CheckpointMoveGrace.PendingMove;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.block.CheckpointBlock;
import org.pixelfire.nationwars.world.block.CheckpointBlockEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Breaking a {@link CheckpointBlock} while its city is {@code ACTIVE} is allowed for any citizen of the
 * owning nation (or an ally, mirroring placement's {@code alliesCanPlaceCheckpoints}), unless it would
 * breach the city's tier minimum past founding grace. The block still breaks and drops
 * normally via its loot table; this listener only reacts to a successful break — releasing chunks no
 * longer covered by another checkpoint or the core, updating the registry, recording a
 * {@link CheckpointMoveGrace} entry, and writing a reversible audit entry with the position and claim set.
 */
public final class CheckpointBreakListener
{
    private final CheckpointMoveGrace moveGrace;
    private final CaptureTickListener captureTickListener;

    public CheckpointBreakListener(final CheckpointMoveGrace moveGrace, final CaptureTickListener captureTickListener)
    {
        this.moveGrace = moveGrace;
        this.captureTickListener = captureTickListener;
    }

    @SubscribeEvent
    public void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!(event.getState().getBlock() instanceof CheckpointBlock) || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        final BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof CheckpointBlockEntity blockEntity) || blockEntity.checkpointId() == null)
        {
            return;
        }

        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final Checkpoint checkpoint = registry.checkpoints().get(blockEntity.checkpointId());
        final City city = checkpoint == null ? null : registry.cities().get(checkpoint.cityId());
        if (checkpoint == null || city == null)
        {
            return;
        }

        if (city.state() == CityState.UNDER_SIEGE || city.state() == CityState.OCCUPIED)
        {
            // Purely cosmetic while a siege is live. Anyone can trigger it — including an
            // attacker swinging at the flag — since the Checkpoint record and its claims are untouched
            // either way; only the ACTIVE-state citizen/minimum checks below govern a real break.
            event.setCanceled(true);
            captureTickListener.triggerCosmeticBreak(level, checkpoint);
            return;
        }

        final Player breakingPlayer = event.getPlayer();
        final MinecraftServer server = level.getServer();
        final NationSnapshot nation = breakingPlayer instanceof ServerPlayer serverPlayer ? OpacNations.nationOf(server, serverPlayer) : null;
        final boolean citizenOrAlly = nation != null && (city.ownerNationId().equals(nation.nationId())
                || (NationWarsConfig.ALLIES_CAN_PLACE_CHECKPOINTS.get() && OpacNations.areAllies(server, nation.nationId(), city.ownerNationId())));

        if (!citizenOrAlly)
        {
            event.setCanceled(true);
            sendMessage(breakingPlayer, "You must be a citizen of that city's nation (or an ally, if allowed) to break its checkpoints.");
            return;
        }
        if (city.state() != CityState.ACTIVE)
        {
            event.setCanceled(true);
            sendMessage(breakingPlayer, "That city must be ACTIVE to break a checkpoint.");
            return;
        }
        if (wouldBreachMinimum(city))
        {
            event.setCanceled(true);
            sendMessage(breakingPlayer, "Breaking this checkpoint would drop the city below its tier minimum.");
            return;
        }

        breakCheckpoint(server, level, checkpoint, city, breakingPlayer, registry);
    }

    private boolean wouldBreachMinimum(final City city)
    {
        final long now = System.currentTimeMillis();
        final long graceEndsAt = city.foundedAt() + NationWarsConfig.FOUNDING_GRACE_PERIOD_SECONDS.get() * 1000L;
        if (now < graceEndsAt)
        {
            return false;
        }
        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());
        return city.checkpointIds().size() - 1 < tier.minCheckpoints();
    }

    private void breakCheckpoint(final MinecraftServer server, final ServerLevel level, final Checkpoint checkpoint,
            final City city, final Player breakingPlayer, final NationRegistry registry)
    {
        registry.stripedLocks().withLocks(() ->
        {
            registry.checkpoints().remove(checkpoint.checkpointId());
            final City current = registry.cities().get(city.cityId());
            final Set<UUID> checkpointIds = new HashSet<>(current.checkpointIds());
            checkpointIds.remove(checkpoint.checkpointId());
            registry.cities().put(city.cityId(), new City(
                    current.cityId(), current.name(), current.ownerNationId(), current.founderNationId(),
                    current.dimension(), current.corePos(), current.tier(), current.bankedPayment(),
                    Set.copyOf(checkpointIds), current.state(), current.occupiedByNationId(), current.occupiedSince(),
                    current.occupationLockUntil(), current.foundedAt(), current.lastTransferAt(), current.transferCount(),
                    current.pendingDisbandAt(), current.dormantSince()));
        }, city.cityId());

        releaseUncoveredChunks(server, level, checkpoint, city, registry);

        if (breakingPlayer != null)
        {
            final long expiresAt = System.currentTimeMillis() + NationWarsConfig.CHECKPOINT_MOVE_GRACE_SECONDS.get() * 1000L;
            moveGrace.record(breakingPlayer.getUUID(), new PendingMove(
                    checkpoint.checkpointId(), city.cityId(), checkpoint.holderNationId(), checkpoint.captureProgress(),
                    checkpoint.capturingNationId(), checkpoint.status(), expiresAt));
        }

        final CompoundTag before = CheckpointSnapshot.write(checkpoint);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                breakingPlayer != null ? breakingPlayer.getUUID() : null,
                breakingPlayer != null ? breakingPlayer.getGameProfile().getName() : "UNKNOWN",
                city.ownerNationId(), ActorRole.MEMBER, AuditSource.BLOCK,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "checkpoint_broken"),
                List.of(checkpoint.checkpointId(), city.cityId()), before, new CompoundTag(), true));
    }

    private void releaseUncoveredChunks(final MinecraftServer server, final ServerLevel level, final Checkpoint checkpoint,
            final City city, final NationRegistry registry)
    {
        final Set<ChunkPos> stillCovered = new HashSet<>();
        for (final Checkpoint other : registry.checkpoints().values())
        {
            if (other.cityId().equals(city.cityId()))
            {
                stillCovered.addAll(other.claimedChunks());
            }
        }
        final var coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        stillCovered.addAll(ClaimSetComputation.chunksFor(coreShape, new ChunkPos(city.corePos())));

        final Set<ChunkPos> toRelease = new HashSet<>(checkpoint.claimedChunks());
        toRelease.removeAll(stillCovered);
        if (!toRelease.isEmpty())
        {
            OpacNations.unclaimChunks(server, level.dimension().location(), toRelease);
        }
    }

    private static void sendMessage(final Player player, final String message)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        }
    }
}
