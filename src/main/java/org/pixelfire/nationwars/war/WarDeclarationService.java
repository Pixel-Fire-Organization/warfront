package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarDeclarationContext;
import org.pixelfire.nationwars.state.WarDeclarationFailureReason;
import org.pixelfire.nationwars.state.WarDeclarationPreconditions;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.state.WarWindow;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /war declare}: the eleven checks, then committing the new {@link War} and updating both
 * nations' {@code activeWarIds}.
 */
public final class WarDeclarationService
{
    private WarDeclarationService()
    {
    }

    public static Optional<WarDeclarationFailureReason> declare(final MinecraftServer server, final ServerPlayer declarer,
            final UUID targetNationId)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final NationSnapshot declarerNation = OpacNations.nationOf(server, declarer);
        final long now = System.currentTimeMillis();

        final WarDeclarationContext context = buildContext(server, registry, declarerNation, targetNationId, now);
        final Optional<WarDeclarationFailureReason> failure = WarDeclarationPreconditions.check(context);
        if (failure.isPresent())
        {
            return failure;
        }

        commit(server, registry, declarerNation.nationId(), targetNationId, now);
        return Optional.empty();
    }

    private static WarDeclarationContext buildContext(final MinecraftServer server, final NationRegistry registry,
            final NationSnapshot declarerNation, final UUID targetNationId, final long now)
    {
        if (declarerNation == null)
        {
            return new WarDeclarationContext(false, false, false, false, false, false, false, false, false, now, 0L,
                    false, false, false, false, true);
        }
        final UUID declarerId = declarerNation.nationId();
        final boolean targetExists = targetNationId != null && OpacNations.nationExists(server, targetNationId);
        final boolean targetIsSelf = targetExists && targetNationId.equals(declarerId);
        final boolean targetIsMutualAlly = targetExists && !targetIsSelf && OpacNations.areAllies(server, declarerId, targetNationId);

        final long foundingGraceMillis = NationWarsConfig.FOUNDING_GRACE_PERIOD_SECONDS.get() * 1000L;
        final boolean declarerHasAnyCity = !citiesOf(registry, declarerId).isEmpty();
        final boolean targetHasEligibleCity = targetExists && citiesOf(registry, targetNationId).stream()
                .anyMatch(city -> city.state() != CityState.DORMANT && now - city.foundedAt() >= foundingGraceMillis);

        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final var activityTracker = NationWarsMod.get().getActivityTracker();
        final long currentTick = server.overworld().getGameTime();
        final boolean declarerWarReady = Readiness.isNationReady(server, declarerId, activityTracker, currentTick, afkThresholdTicks);
        final boolean targetWarReady = targetExists
                && Readiness.isNationReady(server, targetNationId, activityTracker, currentTick, afkThresholdTicks);

        final boolean unsettledWarExists = targetExists && findUnsettledWar(registry, declarerId, targetNationId) != null;

        final NationState declarerState = registry.nationStates().getOrDefault(declarerId, NationState.empty(declarerId));
        final NationState targetState = targetExists
                ? registry.nationStates().getOrDefault(targetNationId, NationState.empty(targetNationId))
                : NationState.empty(UUID.randomUUID());
        final long cooldownExpiresAt = declarerState.warCooldowns().getOrDefault(targetNationId, 0L);

        final int maxConcurrentWars = NationWarsConfig.MAX_CONCURRENT_WARS.get();
        final boolean declarerAtWarCap = countUnsettledWars(registry, declarerId) >= maxConcurrentWars;
        final boolean targetAtWarCap = targetExists && countUnsettledWars(registry, targetNationId) >= maxConcurrentWars;

        final int minuteOfDay = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).getHour() * 60
                + Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).getMinute();
        final boolean withinWarWindow = WarWindow.isWithin(
                NationWarsConfig.WAR_WINDOW_START.get(), NationWarsConfig.WAR_WINDOW_END.get(), minuteOfDay);

        return new WarDeclarationContext(
                declarerNation.isOwner(), declarerHasAnyCity, targetExists, targetIsSelf, targetIsMutualAlly,
                targetHasEligibleCity, targetWarReady, declarerWarReady, unsettledWarExists, now, cooldownExpiresAt,
                declarerState.lockedByWarId() != null, targetState.lockedByWarId() != null,
                declarerAtWarCap, targetAtWarCap, withinWarWindow);
    }

    private static List<City> citiesOf(final NationRegistry registry, final UUID nationId)
    {
        return registry.cities().values().stream().filter(city -> city.ownerNationId().equals(nationId)).toList();
    }

    static War findUnsettledWar(final NationRegistry registry, final UUID nationA, final UUID nationB)
    {
        for (final War war : registry.wars().values())
        {
            if (war.phase() == WarPhase.ENDED)
            {
                continue;
            }
            final boolean aAttacksB = war.attackers().members().contains(nationA) && war.defenders().members().contains(nationB);
            final boolean bAttacksA = war.attackers().members().contains(nationB) && war.defenders().members().contains(nationA);
            if (aAttacksB || bAttacksA)
            {
                return war;
            }
        }
        return null;
    }

    static int countUnsettledWars(final NationRegistry registry, final UUID nationId)
    {
        int count = 0;
        for (final War war : registry.wars().values())
        {
            if (war.phase() != WarPhase.ENDED
                    && (war.attackers().members().contains(nationId) || war.defenders().members().contains(nationId)))
            {
                count++;
            }
        }
        return count;
    }

    private static void commit(final MinecraftServer server, final NationRegistry registry, final UUID declarerId,
            final UUID targetId, final long now)
    {
        final UUID warId = UUID.randomUUID();
        final long warExpiresAt = now + NationWarsConfig.WAR_DURATION_SECONDS.get() * 1000L;

        final Coalition defenders = CoalitionAssembly.assembleDefenders(server, targetId, now);
        final Coalition attackers = Coalition.ofPrimary(declarerId);

        final Set<UUID> targetCityIds = defenders.members().stream()
                .flatMap(nationId -> citiesOf(registry, nationId).stream())
                .filter(city -> city.state() != CityState.DORMANT)
                .filter(city -> now >= city.occupationLockUntil())
                .map(City::cityId)
                .collect(Collectors.toUnmodifiableSet());

        final War war = new War(warId, attackers, defenders, WarPhase.PREPARATION,
                now, 0L, warExpiresAt, targetCityIds, Set.of(), Map.of(), 0L, 0L, 0L, null, Map.of());

        final Set<UUID> allMembers = new HashSet<>(defenders.members());
        allMembers.add(declarerId);

        registry.stripedLocks().withLocks(() ->
        {
            registry.wars().put(warId, war);
            for (final UUID nationId : allMembers)
            {
                addActiveWar(registry, nationId, warId);
            }
        }, allMembers.toArray(UUID[]::new));

        final CompoundTag after = new CompoundTag();
        after.putUUID("warId", warId);
        after.putUUID("defenderNationId", targetId);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", declarerId, ActorRole.LEADER, AuditSource.COMMAND,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "war_declared"), List.of(warId, declarerId, targetId),
                new CompoundTag(), after, false));
    }

    private static void addActiveWar(final NationRegistry registry, final UUID nationId, final UUID warId)
    {
        final NationState current = registry.nationStates().getOrDefault(nationId, NationState.empty(nationId));
        final Set<UUID> activeWarIds = new HashSet<>(current.activeWarIds());
        activeWarIds.add(warId);
        registry.nationStates().put(nationId, new NationState(nationId, current.cityIds(), current.capitalCityId(),
                Set.copyOf(activeWarIds), current.warCooldowns(), current.lastCityFoundedAt(), current.lockedByWarId()));
    }
}
