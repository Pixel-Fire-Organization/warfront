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
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.network.CitySyncHelper;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.FoundingContext;
import org.pixelfire.nationwars.state.FoundingFailureReason;
import org.pixelfire.nationwars.state.FoundingPreconditions;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.block.CityCoreBlock;
import org.pixelfire.nationwars.world.block.CityCoreBlockEntity;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Placing a {@link CityCoreBlock} is the founding action: this listener runs the ten
 * preconditions against the placement and either cancels it with the failing reason, or commits
 * the new {@link City}. OPAC is snapshotted into primitives here, on the main thread, before handing
 * anything to the world-free {@link FoundingPreconditions} check.
 */
public final class CityFoundingListener
{
    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getPlacedBlock().getBlock() instanceof CityCoreBlock))
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

        final FoundingContext context = buildContext(server, level, pos, nation);
        final Optional<FoundingFailureReason> failure = FoundingPreconditions.check(context);
        if (failure.isPresent())
        {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(failure.get().message()).withStyle(ChatFormatting.RED));
            return;
        }

        foundCity(server, level, pos, player, nation);
    }

    private FoundingContext buildContext(final MinecraftServer server, final ServerLevel level, final BlockPos pos,
            final NationSnapshot nation)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final PartyMemberRank requiredRank = parseRank(NationWarsConfig.CITY_FOUND_RANK.get());

        final boolean dimensionEligible = DimensionEligibility.isEligible(
                level.dimensionType().hasSkyLight(), level.dimensionType().hasCeiling(),
                level.dimension().location().toString(),
                List.copyOf(NationWarsConfig.ALLOWED_DIMENSIONS.get()), List.copyOf(NationWarsConfig.BLOCKED_DIMENSIONS.get()));

        final int worldSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1;

        double nearestOtherCoreDistance = Double.MAX_VALUE;
        int existingCityCount = 0;
        for (final City city : registry.cities().values())
        {
            if (!city.dimension().equals(level.dimension()))
            {
                continue;
            }
            final double dx = city.corePos().getX() - pos.getX();
            final double dz = city.corePos().getZ() - pos.getZ();
            nearestOtherCoreDistance = Math.min(nearestOtherCoreDistance, Math.sqrt(dx * dx + dz * dz));
            if (nation != null && city.ownerNationId().equals(nation.nationId()))
            {
                existingCityCount++;
            }
        }

        final NationState nationState = nation == null ? null
                : registry.nationStates().getOrDefault(nation.nationId(), NationState.empty(nation.nationId()));

        return new FoundingContext(
                nation != null,
                nation != null ? nation.rankOrdinal() : 0,
                requiredRank.ordinal(),
                dimensionEligible,
                SkyColumnScanner.isColumnClear(level, pos),
                SurfaceRequirement.isMet(NationWarsConfig.REQUIRE_SURFACE_PLACEMENT.get(), pos.getY(), worldSurfaceY,
                        NationWarsConfig.SURFACE_TOLERANCE.get()),
                nearestOtherCoreDistance,
                NationWarsConfig.effectiveMinCoreDistance,
                existingCityCount,
                NationWarsConfig.MAX_CITIES_PER_NATION.get(),
                nation != null ? nation.memberCount() : 0,
                NationWarsConfig.MAX_CITIES_PER_MEMBER.get(),
                nationState != null ? (System.currentTimeMillis() - nationState.lastCityFoundedAt()) / 1000L : 0L,
                NationWarsConfig.CITY_FOUND_COOLDOWN_SECONDS.get(),
                nation != null && OpacNations.isChunkClaimedByOtherNation(
                        server, level.dimension().location(), pos, nation.nationId()),
                nationState != null && nationState.lockedByWarId() != null,
                nationState != null && !nationState.activeWarIds().isEmpty(),
                NationWarsConfig.ALLOW_FOUNDING_DURING_WAR.get());
    }

    private void foundCity(final MinecraftServer server, final ServerLevel level, final BlockPos pos,
            final ServerPlayer player, final NationSnapshot nation)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();
        final UUID cityId = UUID.randomUUID();
        final String name = defaultCityName(registry, nation.nationName());

        final City city = new City(cityId, name, nation.nationId(), nation.nationId(), level.dimension(), pos,
                0, 0L, Set.of(), CityState.ACTIVE, null, 0L, 0L, now, 0L, 0, 0L, 0L);

        registry.stripedLocks().withLocks(() ->
        {
            registry.cities().put(cityId, city);
            final NationState current = registry.nationStates().getOrDefault(nation.nationId(), NationState.empty(nation.nationId()));
            final Set<UUID> cityIds = new HashSet<>(current.cityIds());
            cityIds.add(cityId);
            final UUID capitalCityId = current.capitalCityId() == null ? cityId : current.capitalCityId();
            registry.nationStates().put(nation.nationId(), new NationState(
                    nation.nationId(), Set.copyOf(cityIds), capitalCityId, current.activeWarIds(),
                    current.warCooldowns(), now, current.lockedByWarId()));
        }, nation.nationId());

        if (level.getBlockEntity(pos) instanceof CityCoreBlockEntity blockEntity)
        {
            blockEntity.setCityId(cityId);
        }

        NationWarsMod.get().getColumnRegistry().register(level.dimension(), pos);

        final ClaimShape coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        final Set<ChunkPos> claimedChunks = ClaimSetComputation.chunksFor(coreShape, new ChunkPos(pos));
        OpacNations.claimChunks(server, level.dimension().location(), nation.leaderUuid(), claimedChunks);

        final CompoundTag after = new CompoundTag();
        after.putUUID("cityId", cityId);
        after.putString("name", name);
        after.putUUID("ownerNationId", nation.nationId());
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                player.getUUID(), player.getGameProfile().getName(), nation.nationId(),
                nation.isOwner() ? ActorRole.LEADER : ActorRole.MEMBER, AuditSource.BLOCK,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "city_founded"), List.of(cityId),
                new CompoundTag(), after, false));

        CitySyncHelper.broadcast(server, registry, city);

        player.sendSystemMessage(Component.literal("Founded the city of " + name + ".").withStyle(ChatFormatting.GREEN));
    }

    /**
     * Tries each configured default name in order, first come first served; falls back to
     * "&lt;Party&gt; City" (numbered if that's taken too) once the whole list is exhausted. Either way
     * the result is guaranteed unique (case-insensitive) among existing cities — the founder can always
     * rename from the City Core GUI afterward.
     */
    private static String defaultCityName(final NationRegistry registry, final String nationName)
    {
        for (final String candidate : NationWarsConfig.CITY_DEFAULT_NAMES.get())
        {
            if (NegotiationService.findCityByName(registry, candidate) == null)
            {
                return candidate;
            }
        }

        final String base = fallbackBaseName(nationName);
        String candidate = base;
        int suffix = 2;
        while (NegotiationService.findCityByName(registry, candidate) != null)
        {
            candidate = base + " " + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String fallbackBaseName(final String nationName)
    {
        final String candidate = nationName.trim() + " City";
        if (candidate.length() <= 24)
        {
            return candidate.length() < 3 ? "New City" : candidate;
        }
        return candidate.substring(0, 24);
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
