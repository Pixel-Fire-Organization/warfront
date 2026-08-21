package org.pixelfire.nationwars.world;

import net.minecraft.ChatFormatting;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.block.NationWarsBlocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces the tier minimum: a city below {@code minCheckpoints(tier)} past its founding
 * grace becomes {@code DORMANT}, and one that stays {@code DORMANT} past {@code dormantCityExpiry} is
 * removed with its core dropped. Runs on {@code nationValidationInterval} rather than every tick, since
 * this is exactly the kind of invariant check that config's comment on that setting describes. Under
 * normal play a checkpoint break that would breach the minimum is refused outright
 * ({@link CheckpointBreakListener}), so this sweep exists as the safety net for a city dropped below
 * minimum by some other means.
 */
public final class CityDormancyListener
{
    private int tickCounter;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        final int intervalTicks = NationWarsConfig.NATION_VALIDATION_INTERVAL_SECONDS.get() * 20;
        if (++tickCounter < intervalTicks)
        {
            return;
        }
        tickCounter = 0;

        sweep(server);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final MinecraftServer server = player.getServer();
        final NationSnapshot nation = OpacNations.nationOf(server, player);
        if (nation == null)
        {
            return;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final NationState nationState = registry.nationStates().get(nation.nationId());
        if (nationState == null)
        {
            return;
        }
        for (final UUID cityId : nationState.cityIds())
        {
            final City city = registry.cities().get(cityId);
            if (city != null && city.state() == CityState.DORMANT)
            {
                player.sendSystemMessage(Component.literal(
                        "Your city '" + city.name() + "' is DORMANT (below its checkpoint minimum) and will be "
                                + "removed if it isn't fixed in time.").withStyle(ChatFormatting.RED));
            }
        }
    }

    private void sweep(final MinecraftServer server)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();
        for (final City city : new ArrayList<>(registry.cities().values()))
        {
            if (city.state() == CityState.ACTIVE)
            {
                maybeMarkDormant(registry, city, now);
            }
            else if (city.state() == CityState.DORMANT && city.dormantSince() > 0
                    && now - city.dormantSince() >= NationWarsConfig.DORMANT_CITY_EXPIRY_SECONDS.get() * 1000L)
            {
                removeExpiredCity(server, registry, city);
            }
        }
    }

    private void maybeMarkDormant(final NationRegistry registry, final City city, final long now)
    {
        final long graceEndsAt = city.foundedAt() + NationWarsConfig.FOUNDING_GRACE_PERIOD_SECONDS.get() * 1000L;
        if (now < graceEndsAt)
        {
            return;
        }
        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());
        if (city.checkpointIds().size() >= tier.minCheckpoints())
        {
            return;
        }

        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null && current.state() == CityState.ACTIVE)
            {
                registry.cities().put(city.cityId(), withState(current, CityState.DORMANT, now));
            }
        }, city.cityId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_dormant"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void removeExpiredCity(final MinecraftServer server, final NationRegistry registry, final City city)
    {
        final ServerLevel level = server.getLevel(city.dimension());
        if (level == null)
        {
            return;
        }

        final Set<ChunkPos> toRelease = new HashSet<>();
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (checkpoint.cityId().equals(city.cityId()))
            {
                toRelease.addAll(checkpoint.claimedChunks());
            }
        }
        final var coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        toRelease.addAll(ClaimSetComputation.chunksFor(coreShape, new ChunkPos(city.corePos())));
        OpacNations.unclaimChunks(server, level.dimension().location(), toRelease);

        final Block coreBlock = NationWarsBlocks.CITY_CORE.get();
        if (level.getBlockState(city.corePos()).is(coreBlock))
        {
            level.setBlock(city.corePos(), Blocks.AIR.defaultBlockState(), 3);
            Block.popResource(level, city.corePos(), new ItemStack(NationWarsBlocks.CITY_CORE_ITEM.get()));
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
                registry.nationStates().put(city.ownerNationId(), new NationState(
                        current.nationId(), Set.copyOf(cityIds), capital, current.activeWarIds(),
                        current.warCooldowns(), current.lastCityFoundedAt(), current.lockedByWarId()));
            }
        }, city.cityId(), city.ownerNationId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_removed_dormant_expiry"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
        NationWarsMod.get().forceSave();
    }

    private static City withState(final City city, final CityState state, final long dormantSince)
    {
        return new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(), city.dimension(),
                city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), state, city.occupiedByNationId(),
                city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(),
                city.transferCount(), city.pendingDisbandAt(), dormantSince);
    }
}
