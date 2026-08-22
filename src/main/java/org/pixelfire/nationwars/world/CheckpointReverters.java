package org.pixelfire.nationwars.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditReverters;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointSnapshot;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.world.block.CheckpointBlockEntity;
import org.pixelfire.nationwars.world.block.NationWarsBlocks;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reverters for the checkpoint lifecycle actions ({@code checkpoint_placed}/{@code checkpoint_moved}
 * undo the placement; {@code checkpoint_broken} restores it), registered once at startup.
 */
public final class CheckpointReverters
{
    private CheckpointReverters()
    {
    }

    public static void bootstrap()
    {
        AuditReverters.register(ResourceLocation.tryBuild(NationWarsMod.MODID, "checkpoint_placed"),
                CheckpointReverters::revertPlacement);
        AuditReverters.register(ResourceLocation.tryBuild(NationWarsMod.MODID, "checkpoint_moved"),
                CheckpointReverters::revertPlacement);
        AuditReverters.register(ResourceLocation.tryBuild(NationWarsMod.MODID, "checkpoint_broken"),
                CheckpointReverters::revertBreak);
    }

    private static Optional<String> revertPlacement(final NationRegistry registry, final MinecraftServer server,
            final AuditEntry entry)
    {
        final Checkpoint placed = CheckpointSnapshot.read(entry.after());
        final Checkpoint current = registry.checkpoints().get(placed.checkpointId());
        if (current == null)
        {
            return Optional.of("checkpoint no longer exists");
        }
        final City city = registry.cities().get(placed.cityId());

        registry.stripedLocks().withLocks(() ->
        {
            registry.checkpoints().remove(placed.checkpointId());
            if (city != null)
            {
                final Set<UUID> checkpointIds = new HashSet<>(city.checkpointIds());
                checkpointIds.remove(placed.checkpointId());
                registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), city.ownerNationId(),
                        city.founderNationId(), city.dimension(), city.corePos(), city.tier(), city.bankedPayment(),
                        Set.copyOf(checkpointIds), city.state(), city.occupiedByNationId(), city.occupiedSince(),
                        city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(), city.transferCount(),
                        city.pendingDisbandAt(), city.dormantSince()));
            }
        }, placed.cityId());

        if (city != null)
        {
            releaseUncoveredChunks(server, placed, city, registry);
        }
        removeBlockIfPresent(server, placed);
        return Optional.empty();
    }

    private static Optional<String> revertBreak(final NationRegistry registry, final MinecraftServer server, final AuditEntry entry)
    {
        final Checkpoint broken = CheckpointSnapshot.read(entry.before());
        if (registry.checkpoints().containsKey(broken.checkpointId()))
        {
            return Optional.of("a checkpoint with this id already exists");
        }
        final City city = registry.cities().get(broken.cityId());
        if (city == null)
        {
            return Optional.of("the checkpoint's city no longer exists");
        }

        registry.stripedLocks().withLocks(() ->
        {
            registry.checkpoints().put(broken.checkpointId(), broken);
            final Set<UUID> checkpointIds = new HashSet<>(city.checkpointIds());
            checkpointIds.add(broken.checkpointId());
            registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), city.ownerNationId(),
                    city.founderNationId(), city.dimension(), city.corePos(), city.tier(), city.bankedPayment(),
                    Set.copyOf(checkpointIds), city.state(), city.occupiedByNationId(), city.occupiedSince(),
                    city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(), city.transferCount(),
                    city.pendingDisbandAt(), city.dormantSince()));
        }, broken.cityId());

        final UUID leader = OpacNations.leaderUuidOf(server, city.ownerNationId());
        if (leader != null)
        {
            OpacNations.claimChunks(server, broken.dimension().location(), leader, broken.claimedChunks());
        }

        final ServerLevel level = server.getLevel(broken.dimension());
        if (level == null || !level.isLoaded(broken.pos()) || !level.getBlockState(broken.pos()).isAir())
        {
            return Optional.of("registry state restored, but the physical checkpoint block could not be "
                    + "replaced (chunk unloaded or position occupied) — place it manually at " + broken.pos());
        }
        level.setBlock(broken.pos(), NationWarsBlocks.CHECKPOINT.get().defaultBlockState(), 3);
        if (level.getBlockEntity(broken.pos()) instanceof CheckpointBlockEntity blockEntity)
        {
            blockEntity.setIds(broken.checkpointId(), broken.cityId());
        }
        return Optional.empty();
    }

    private static void removeBlockIfPresent(final MinecraftServer server, final Checkpoint placed)
    {
        final ServerLevel level = server.getLevel(placed.dimension());
        if (level == null || !level.isLoaded(placed.pos()))
        {
            return;
        }
        if (level.getBlockState(placed.pos()).is(NationWarsBlocks.CHECKPOINT.get()))
        {
            level.setBlock(placed.pos(), Blocks.AIR.defaultBlockState(), 3);
            Block.popResource(level, placed.pos(), new ItemStack(NationWarsBlocks.CHECKPOINT_ITEM.get()));
        }
    }

    private static void releaseUncoveredChunks(final MinecraftServer server, final Checkpoint removed, final City city,
            final NationRegistry registry)
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

        final Set<ChunkPos> toRelease = new HashSet<>(removed.claimedChunks());
        toRelease.removeAll(stillCovered);
        if (!toRelease.isEmpty())
        {
            OpacNations.unclaimChunks(server, removed.dimension().location(), toRelease);
        }
    }
}
