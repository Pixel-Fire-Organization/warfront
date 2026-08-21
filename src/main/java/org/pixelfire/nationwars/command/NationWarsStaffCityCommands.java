package org.pixelfire.nationwars.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.world.ClaimSetComputation;
import org.pixelfire.nationwars.world.ClaimShape;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.block.NationWarsBlocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /nationwars staff city transfer|release|delete|revalidate}. These bypass every normal
 * precondition (war score, occupation lock, tier minimums) since they exist precisely for correcting a
 * city stuck by a bug or a dispute the normal pathways can't resolve.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsStaffCityCommands
{
    private NationWarsStaffCityCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("city")
                                .requires(NationWarsStaffCityCommands::hasStaffCityPermission)
                                .then(Commands.literal("transfer")
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .then(Commands.argument("nation", StringArgumentType.greedyString())
                                                        .executes(NationWarsStaffCityCommands::transfer))))
                                .then(Commands.literal("release")
                                        .then(Commands.argument("city", StringArgumentType.greedyString())
                                                .executes(NationWarsStaffCityCommands::release)))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("city", StringArgumentType.greedyString())
                                                .executes(NationWarsStaffCityCommands::delete)))
                                .then(Commands.literal("revalidate")
                                        .then(Commands.argument("city", StringArgumentType.greedyString())
                                                .executes(NationWarsStaffCityCommands::revalidate))))));
    }

    private static boolean hasStaffCityPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_CITY);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static City resolveCity(final CommandContext<CommandSourceStack> context, final String argumentName)
    {
        final String cityName = StringArgumentType.getString(context, argumentName);
        return NegotiationService.findCityByName(NationWarsMod.get().getNationRegistry(), cityName);
    }

    private static int transfer(final CommandContext<CommandSourceStack> context)
    {
        final City city = resolveCity(context, "city");
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final String nationName = StringArgumentType.getString(context, "nation");
        final UUID toNationId = OpacNations.findNationByName(context.getSource().getServer(), nationName);
        if (toNationId == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation."));
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final UUID fromNationId = city.ownerNationId();

        registry.globalWriteLock().lock();
        try
        {
            registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), toNationId, city.founderNationId(),
                    city.dimension(), city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), city.state(),
                    city.occupiedByNationId(), city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(),
                    System.currentTimeMillis(), city.transferCount() + 1, city.pendingDisbandAt(), city.dormantSince()));

            moveCityIdBetweenNationStates(registry, fromNationId, toNationId, city.cityId());

            final UUID newLeader = OpacNations.leaderUuidOf(context.getSource().getServer(), toNationId);
            if (newLeader != null)
            {
                final Set<ChunkPos> chunks = claimedChunksOf(registry, city);
                OpacNations.claimChunks(context.getSource().getServer(), city.dimension().location(), newLeader, chunks);
            }
        }
        finally
        {
            registry.globalWriteLock().unlock();
        }

        final CompoundTag after = new CompoundTag();
        after.putUUID("cityId", city.cityId());
        after.putUUID("toNationId", toNationId);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", toNationId, ActorRole.STAFF, AuditSource.COMMAND,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "staff_city_transfer"), List.of(city.cityId(), toNationId),
                new CompoundTag(), after, false));

        context.getSource().sendSuccess(() -> Component.literal("Transferred " + city.name() + " to " + nationName + "."), true);
        return 1;
    }

    private static void moveCityIdBetweenNationStates(final NationRegistry registry, final UUID fromNationId, final UUID toNationId,
            final UUID cityId)
    {
        final NationState from = registry.nationStates().get(fromNationId);
        if (from != null)
        {
            final Set<UUID> cityIds = new HashSet<>(from.cityIds());
            cityIds.remove(cityId);
            final UUID capital = cityId.equals(from.capitalCityId()) ? null : from.capitalCityId();
            registry.nationStates().put(fromNationId, new NationState(from.nationId(), Set.copyOf(cityIds), capital,
                    from.activeWarIds(), from.warCooldowns(), from.lastCityFoundedAt(), from.lockedByWarId()));
        }
        final NationState to = registry.nationStates().getOrDefault(toNationId, NationState.empty(toNationId));
        final Set<UUID> toCityIds = new HashSet<>(to.cityIds());
        toCityIds.add(cityId);
        registry.nationStates().put(toNationId, new NationState(to.nationId(), Set.copyOf(toCityIds), to.capitalCityId(),
                to.activeWarIds(), to.warCooldowns(), to.lastCityFoundedAt(), to.lockedByWarId()));
    }

    private static Set<ChunkPos> claimedChunksOf(final NationRegistry registry, final City city)
    {
        final Set<ChunkPos> chunks = new HashSet<>();
        final var coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        chunks.addAll(ClaimSetComputation.chunksFor(coreShape, new ChunkPos(city.corePos())));
        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint != null)
            {
                chunks.addAll(checkpoint.claimedChunks());
            }
        }
        return chunks;
    }

    private static int release(final CommandContext<CommandSourceStack> context)
    {
        final City city = resolveCity(context, "city");
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        if (city.state() != CityState.OCCUPIED)
        {
            context.getSource().sendFailure(Component.literal("That city is not occupied."));
            return 0;
        }

        registry.stripedLocks().withLocks(() ->
        {
            registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(),
                    city.dimension(), city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), CityState.UNDER_SIEGE,
                    null, 0L, 0L, city.foundedAt(), city.lastTransferAt(), city.transferCount(), city.pendingDisbandAt(),
                    city.dormantSince()));
            for (final UUID checkpointId : city.checkpointIds())
            {
                final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
                if (checkpoint != null)
                {
                    registry.checkpoints().put(checkpointId, new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                            checkpoint.dimension(), checkpoint.pos(), city.ownerNationId(), 0f, null, CheckpointStatus.HELD,
                            checkpoint.claimedChunks(), System.currentTimeMillis(), checkpoint.placedBy(), checkpoint.placedAt()));
                }
            }
        }, city.cityId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.STAFF,
                AuditSource.COMMAND, ResourceLocation.tryBuild(NationWarsMod.MODID, "staff_city_release"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));

        context.getSource().sendSuccess(() -> Component.literal("Released " + city.name() + " back to its owner."), true);
        return 1;
    }

    private static int delete(final CommandContext<CommandSourceStack> context)
    {
        final City city = resolveCity(context, "city");
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final MinecraftServer server = context.getSource().getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final ServerLevel level = server.getLevel(city.dimension());

        if (level != null)
        {
            final Set<ChunkPos> toRelease = claimedChunksOf(registry, city);
            OpacNations.unclaimChunks(server, level.dimension().location(), toRelease);

            final Block coreBlock = NationWarsBlocks.CITY_CORE.get();
            if (level.getBlockState(city.corePos()).is(coreBlock))
            {
                level.setBlock(city.corePos(), Blocks.AIR.defaultBlockState(), 3);
                Block.popResource(level, city.corePos(), new ItemStack(NationWarsBlocks.CITY_CORE_ITEM.get()));
            }
        }

        registry.stripedLocks().withLocks(() ->
        {
            registry.cities().remove(city.cityId());
            for (final UUID checkpointId : city.checkpointIds())
            {
                registry.checkpoints().remove(checkpointId);
            }
            final NationState current = registry.nationStates().get(city.ownerNationId());
            if (current != null)
            {
                final Set<UUID> cityIds = new HashSet<>(current.cityIds());
                cityIds.remove(city.cityId());
                final UUID capital = city.cityId().equals(current.capitalCityId()) ? null : current.capitalCityId();
                registry.nationStates().put(city.ownerNationId(), new NationState(current.nationId(), Set.copyOf(cityIds), capital,
                        current.activeWarIds(), current.warCooldowns(), current.lastCityFoundedAt(), current.lockedByWarId()));
            }
        }, city.cityId(), city.ownerNationId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.STAFF,
                AuditSource.COMMAND, ResourceLocation.tryBuild(NationWarsMod.MODID, "staff_city_delete"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));

        context.getSource().sendSuccess(() -> Component.literal("Deleted " + city.name() + "."), true);
        return 1;
    }

    /**
     * Checks a bounded, safely-repairable subset of the spec's invariants for one city: checkpoint
     * belongs-to-exactly-one-city bidirectionally (auto-repaired here), and the tier checkpoint
     * bounds / owner-party-liveness / min-core-distance checks (reported only, since fixing those
     * means transferring, deleting or waiting out dormancy — actions staff already have their own
     * commands for). The periodic server-wide sweep is a separate, broader mechanism.
     */
    private static int revalidate(final CommandContext<CommandSourceStack> context)
    {
        final City city = resolveCity(context, "city");
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final List<String> findings = new ArrayList<>();

        final Set<UUID> actualCheckpointIds = new HashSet<>();
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (checkpoint.cityId().equals(city.cityId()))
            {
                actualCheckpointIds.add(checkpoint.checkpointId());
            }
        }
        if (!actualCheckpointIds.equals(city.checkpointIds()))
        {
            registry.stripedLocks().withLocks(() -> registry.cities().put(city.cityId(), new City(city.cityId(), city.name(),
                    city.ownerNationId(), city.founderNationId(), city.dimension(), city.corePos(), city.tier(),
                    city.bankedPayment(), Set.copyOf(actualCheckpointIds), city.state(), city.occupiedByNationId(),
                    city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(),
                    city.transferCount(), city.pendingDisbandAt(), city.dormantSince())), city.cityId());
            findings.add("repaired checkpointIds (was " + city.checkpointIds().size() + ", now " + actualCheckpointIds.size() + ")");

            NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                    AuditSource.COMMAND, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_repair_checkpoint_set"),
                    List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
        }

        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());
        final long graceEndsAt = city.foundedAt() + NationWarsConfig.FOUNDING_GRACE_PERIOD_SECONDS.get() * 1000L;
        if (System.currentTimeMillis() >= graceEndsAt
                && (actualCheckpointIds.size() < tier.minCheckpoints() || actualCheckpointIds.size() > tier.maxCheckpoints()))
        {
            findings.add("checkpoint count " + actualCheckpointIds.size() + " is outside tier bounds ["
                    + tier.minCheckpoints() + ", " + tier.maxCheckpoints() + "]");
        }

        if (!OpacNations.nationExists(context.getSource().getServer(), city.ownerNationId()))
        {
            findings.add("owner nation " + city.ownerNationId() + " does not resolve to a live party");
        }

        for (final City other : registry.cities().values())
        {
            if (other.cityId().equals(city.cityId()) || !other.dimension().equals(city.dimension()))
            {
                continue;
            }
            final BlockPos delta = city.corePos().subtract(other.corePos());
            final double distance = Math.sqrt(delta.getX() * (double) delta.getX() + delta.getZ() * (double) delta.getZ());
            if (distance < NationWarsConfig.MIN_CORE_DISTANCE.get())
            {
                findings.add("core distance to '" + other.name() + "' (" + (long) distance + ") is below minCoreDistance");
            }
        }

        if (findings.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.literal(city.name() + ": no invariant issues found."), false);
        }
        else
        {
            for (final String finding : findings)
            {
                context.getSource().sendSuccess(() -> Component.literal(city.name() + ": " + finding), false);
            }
        }
        return 1;
    }
}
