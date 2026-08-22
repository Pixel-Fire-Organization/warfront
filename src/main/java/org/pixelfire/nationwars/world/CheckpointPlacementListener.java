package org.pixelfire.nationwars.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointFailureReason;
import org.pixelfire.nationwars.state.CheckpointPlacementContext;
import org.pixelfire.nationwars.state.CheckpointPlacementPreconditions;
import org.pixelfire.nationwars.state.CheckpointSnapshot;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.world.CheckpointMoveGrace.PendingMove;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.block.CheckpointBlock;
import org.pixelfire.nationwars.world.block.CheckpointBlockEntity;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Placing a {@link CheckpointBlock} is the placement action: resolves which city (if any)
 * the position falls within tier radius of, runs the eight preconditions against it, and either cancels
 * with the failing reason or commits the new {@link Checkpoint} — reviving one from
 * {@link CheckpointMoveGrace} instead of minting a fresh id if the same player is re-placing within
 * {@code checkpointMoveGrace} of breaking one for the same city.
 */
public final class CheckpointPlacementListener
{
    private final CheckpointMoveGrace moveGrace;

    public CheckpointPlacementListener(final CheckpointMoveGrace moveGrace)
    {
        this.moveGrace = moveGrace;
    }

    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getPlacedBlock().getBlock() instanceof CheckpointBlock))
        {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level))
        {
            event.setCanceled(true);
            return;
        }

        final MinecraftServer server = level.getServer();
        final NationSnapshot nation = OpacNations.nationOf(server, player);
        final BlockPos pos = event.getPos();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();

        final City matchedCity = findMatchingCity(registry, level, pos);
        final int matchingCoreCount = countMatchingCities(registry, level, pos);

        final CheckpointPlacementContext context = buildContext(server, level, pos, nation, matchedCity, matchingCoreCount);
        final Optional<CheckpointFailureReason> failure = CheckpointPlacementPreconditions.check(context);
        if (failure.isPresent())
        {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(failure.get().message()).withStyle(ChatFormatting.RED));
            return;
        }

        placeCheckpoint(server, level, pos, player, nation, matchedCity, registry);
    }

    private CheckpointPlacementContext buildContext(final MinecraftServer server, final ServerLevel level, final BlockPos pos,
            final NationSnapshot nation, final City matchedCity, final int matchingCoreCount)
    {
        if (matchedCity == null)
        {
            return new CheckpointPlacementContext(matchingCoreCount, false, 0, 0, false, false, false, 0, 0,
                    0.0, 0.0, 0.0, 0.0, false);
        }

        final PartyMemberRank requiredRank = parseRank(NationWarsConfig.CHECKPOINT_PLACE_RANK.get());
        final boolean citizenOrAlly = nation != null && (matchedCity.ownerNationId().equals(nation.nationId())
                || (NationWarsConfig.ALLIES_CAN_PLACE_CHECKPOINTS.get()
                        && OpacNations.areAllies(server, nation.nationId(), matchedCity.ownerNationId())));

        final TierDefinition tier = NationWarsConfig.tiers.get(matchedCity.tier());

        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        double nearestOtherCheckpointDistance = Double.MAX_VALUE;
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (!checkpoint.cityId().equals(matchedCity.cityId()))
            {
                continue;
            }
            final double dx = checkpoint.pos().getX() - pos.getX();
            final double dz = checkpoint.pos().getZ() - pos.getZ();
            nearestOtherCheckpointDistance = Math.min(nearestOtherCheckpointDistance, Math.sqrt(dx * dx + dz * dz));
        }

        final double coreDx = matchedCity.corePos().getX() - pos.getX();
        final double coreDz = matchedCity.corePos().getZ() - pos.getZ();
        final double coreDistance = Math.sqrt(coreDx * coreDx + coreDz * coreDz);

        final int worldSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1;

        final var claimShape = ClaimShape.parse(NationWarsConfig.CHECKPOINT_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        final Set<ChunkPos> claimChunks = ClaimSetComputation.chunksFor(claimShape, new ChunkPos(pos));
        final boolean anyClaimed = nation == null || OpacNations.isAnyChunkClaimedByOtherNation(
                server, level.dimension().location(), claimChunks, nation.nationId());

        return new CheckpointPlacementContext(
                matchingCoreCount,
                citizenOrAlly,
                nation != null ? nation.rankOrdinal() : 0,
                requiredRank.ordinal(),
                matchedCity.state() == CityState.ACTIVE,
                SkyColumnScanner.isColumnClear(level, pos),
                SurfaceRequirement.isMet(NationWarsConfig.REQUIRE_SURFACE_PLACEMENT.get(), pos.getY(), worldSurfaceY,
                        NationWarsConfig.SURFACE_TOLERANCE.get()),
                matchedCity.checkpointIds().size(),
                tier.maxCheckpoints(),
                nearestOtherCheckpointDistance,
                NationWarsConfig.MIN_CHECKPOINT_SPACING.get(),
                coreDistance,
                NationWarsConfig.MIN_CORE_CLEARANCE.get(),
                anyClaimed);
    }

    private void placeCheckpoint(final MinecraftServer server, final ServerLevel level, final BlockPos pos,
            final ServerPlayer player, final NationSnapshot nation, final City matchedCity, final NationRegistry registry)
    {
        final long now = System.currentTimeMillis();
        final Optional<PendingMove> revived = moveGrace.claim(player.getUUID(), matchedCity.cityId(), now);

        final UUID checkpointId = revived.map(PendingMove::checkpointId).orElseGet(UUID::randomUUID);
        final UUID holderNationId = revived.map(PendingMove::holderNationId).orElse(matchedCity.ownerNationId());
        final float captureProgress = revived.map(PendingMove::captureProgress).orElse(0f);
        final UUID capturingNationId = revived.map(PendingMove::capturingNationId).orElse(null);
        final CheckpointStatus status = revived.map(PendingMove::status).orElse(CheckpointStatus.HELD);

        final var claimShape = ClaimShape.parse(NationWarsConfig.CHECKPOINT_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        final Set<ChunkPos> claimedChunks = ClaimSetComputation.chunksFor(claimShape, new ChunkPos(pos));

        final Checkpoint checkpoint = new Checkpoint(checkpointId, matchedCity.cityId(), level.dimension(), pos,
                holderNationId, captureProgress, capturingNationId, status, claimedChunks, now, player.getUUID(), now);

        registry.stripedLocks().withLocks(() ->
        {
            registry.checkpoints().put(checkpointId, checkpoint);
            final City current = registry.cities().get(matchedCity.cityId());
            final Set<UUID> checkpointIds = new HashSet<>(current.checkpointIds());
            checkpointIds.add(checkpointId);
            registry.cities().put(matchedCity.cityId(), new City(
                    current.cityId(), current.name(), current.ownerNationId(), current.founderNationId(),
                    current.dimension(), current.corePos(), current.tier(), current.bankedPayment(),
                    Set.copyOf(checkpointIds), current.state(), current.occupiedByNationId(), current.occupiedSince(),
                    current.occupationLockUntil(), current.foundedAt(), current.lastTransferAt(), current.transferCount(),
                    current.pendingDisbandAt(), current.dormantSince()));
        }, matchedCity.cityId());

        if (level.getBlockEntity(pos) instanceof CheckpointBlockEntity blockEntity)
        {
            blockEntity.setIds(checkpointId, matchedCity.cityId());
        }

        final UUID leaderUuid = OpacNations.leaderUuidOf(server, matchedCity.ownerNationId());
        if (leaderUuid != null)
        {
            OpacNations.claimChunks(server, level.dimension().location(), leaderUuid, claimedChunks);
            claimEnclosedGapChunks(server, level, matchedCity, registry, leaderUuid, pos);
        }

        final CompoundTag after = CheckpointSnapshot.write(checkpoint);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                player.getUUID(), player.getGameProfile().getName(), matchedCity.ownerNationId(),
                ActorRole.MEMBER, AuditSource.BLOCK,
                ResourceLocation.tryBuild(NationWarsMod.MODID, revived.isPresent() ? "checkpoint_moved" : "checkpoint_placed"),
                List.of(checkpointId, matchedCity.cityId()), new CompoundTag(), after, true));

        player.sendSystemMessage(Component.literal("Checkpoint placed.").withStyle(ChatFormatting.GREEN));
    }

    /**
     * A 2x2 group of checkpoint-chunk cells fully occupied by checkpoints encloses the gap chunks
     * between them; once the last of the four is placed, those gap chunks are absorbed into the city
     * automatically instead of staying permanently unclaimed no-mans-land inside the city's own bounds.
     */
    private void claimEnclosedGapChunks(final MinecraftServer server, final ServerLevel level, final City city,
            final NationRegistry registry, final UUID leaderUuid, final BlockPos newCheckpointPos)
    {
        final ChunkPos coreChunk = new ChunkPos(city.corePos());
        final Optional<CheckpointChunkGrid.Cell> newCell = CheckpointChunkGrid.resolveCell(coreChunk, new ChunkPos(newCheckpointPos));
        if (newCell.isEmpty())
        {
            return;
        }

        final Set<CheckpointChunkGrid.Cell> occupiedCells = new HashSet<>();
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (checkpoint.cityId().equals(city.cityId()))
            {
                CheckpointChunkGrid.resolveCell(coreChunk, new ChunkPos(checkpoint.pos())).ifPresent(occupiedCells::add);
            }
        }

        for (final CheckpointChunkGrid.Cell base : CheckpointChunkGrid.adjacentGapGroupBases(newCell.get()))
        {
            final boolean fullyEnclosed = occupiedCells.contains(base)
                    && occupiedCells.contains(new CheckpointChunkGrid.Cell(base.i() + 1, base.j()))
                    && occupiedCells.contains(new CheckpointChunkGrid.Cell(base.i(), base.j() + 1))
                    && occupiedCells.contains(new CheckpointChunkGrid.Cell(base.i() + 1, base.j() + 1));
            if (fullyEnclosed)
            {
                final Set<ChunkPos> gapChunks = CheckpointChunkGrid.gapChunksBetween(coreChunk, base.i(), base.j());
                OpacNations.claimChunks(server, level.dimension().location(), leaderUuid, gapChunks);
            }
        }
    }

    private static City findMatchingCity(final NationRegistry registry, final ServerLevel level, final BlockPos pos)
    {
        City match = null;
        for (final City city : registry.cities().values())
        {
            if (isWithinTierRadius(city, level, pos))
            {
                match = city;
            }
        }
        return match;
    }

    private static int countMatchingCities(final NationRegistry registry, final ServerLevel level, final BlockPos pos)
    {
        int count = 0;
        for (final City city : registry.cities().values())
        {
            if (isWithinTierRadius(city, level, pos))
            {
                count++;
            }
        }
        return count;
    }

    private static boolean isWithinTierRadius(final City city, final ServerLevel level, final BlockPos pos)
    {
        if (!city.dimension().equals(level.dimension()))
        {
            return false;
        }
        final Optional<CheckpointChunkGrid.Cell> cell = CheckpointChunkGrid.resolveCell(new ChunkPos(city.corePos()), new ChunkPos(pos));
        if (cell.isEmpty())
        {
            return false;
        }
        final int radius = NationWarsConfig.tiers.get(city.tier()).radius();
        return cell.get().distanceFromOrigin() <= radius;
    }

    private static PartyMemberRank parseRank(final String name)
    {
        try
        {
            return PartyMemberRank.valueOf(name);
        }
        catch (final IllegalArgumentException e)
        {
            return PartyMemberRank.MEMBER;
        }
    }
}
