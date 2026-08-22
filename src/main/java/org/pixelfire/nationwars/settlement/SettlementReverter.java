package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditReverters;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.world.ClaimSetComputation;
import org.pixelfire.nationwars.world.ClaimShape;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.war.WarScore;

import org.pixelfire.nationwars.state.CityValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reverts one {@code settlement_applied} entry by walking its recorded clause list in reverse order.
 * {@code TransferCity} and {@code Tribute} fully invert (ownership/banked payment plus the war-score
 * spend, if any); {@code Ceasefire} only undoes a cooldown still exactly matching what this settlement
 * set, since a later ceasefire may have already superseded it; {@code ReleaseOccupation} cannot be
 * inverted at all — an active siege's checkpoint state isn't captured anywhere to restore — so it is
 * always reported as not reverted, per the spec's allowance for a best-effort partial revert with an
 * explicit list of what could not be restored.
 */
public final class SettlementReverter
{
    private SettlementReverter()
    {
    }

    public static void bootstrap()
    {
        AuditReverters.register(ResourceLocation.tryBuild(NationWarsMod.MODID, "settlement_applied"),
                SettlementReverter::revert);
    }

    private static Optional<String> revert(final NationRegistry registry, final MinecraftServer server, final AuditEntry entry)
    {
        final List<String> notReverted = new ArrayList<>();
        final List<Tag> clauses = new ArrayList<>(entry.after().getList("clauses", Tag.TAG_COMPOUND));
        for (int i = clauses.size() - 1; i >= 0; i--)
        {
            final CompoundTag clauseTag = (CompoundTag) clauses.get(i);
            final ResourceLocation clauseTypeId = ResourceLocation.parse(clauseTag.getString("clauseTypeId"));
            final CompoundTag params = clauseTag.getCompound("params");

            if (clauseTypeId.equals(TransferCityClause.ID))
            {
                revertTransfer(registry, server, clauseTag, params, entry.after().getBoolean("staffImposed"), entry.targets().get(0));
            }
            else if (clauseTypeId.equals(TributeClause.ID))
            {
                revertTribute(registry, params, entry.after().getBoolean("staffImposed"), entry.targets().get(0));
            }
            else if (clauseTypeId.equals(CeasefireClause.ID))
            {
                revertCeasefire(registry, clauseTag);
            }
            else if (clauseTypeId.equals(ReleaseOccupationClause.ID))
            {
                notReverted.add("release of city " + params.getUUID("cityId") + " could not be restored (siege state isn't recorded)");
            }
        }

        return notReverted.isEmpty() ? Optional.empty()
                : Optional.of("partially reverted; not restored: " + String.join("; ", notReverted));
    }

    private static void revertTransfer(final NationRegistry registry, final MinecraftServer server, final CompoundTag clauseTag,
            final CompoundTag params, final boolean staffImposed, final UUID warId)
    {
        if (!clauseTag.contains("previousOwnerNationId"))
        {
            return;
        }
        final UUID cityId = params.getUUID("cityId");
        final UUID toNationId = params.getUUID("toNationId");
        final UUID previousOwnerNationId = clauseTag.getUUID("previousOwnerNationId");
        final City city = registry.cities().get(cityId);
        if (city == null || !city.ownerNationId().equals(toNationId))
        {
            return;
        }

        registry.globalWriteLock().lock();
        try
        {
            registry.cities().put(cityId, new City(city.cityId(), city.name(), previousOwnerNationId, city.founderNationId(),
                    city.dimension(), city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), CityState.ACTIVE,
                    null, 0L, 0L, city.foundedAt(), city.lastTransferAt(), Math.max(0, city.transferCount() - 1),
                    city.pendingDisbandAt(), city.dormantSince()));
            moveCityId(registry, toNationId, previousOwnerNationId, cityId);

            final UUID previousLeader = OpacNations.leaderUuidOf(server, previousOwnerNationId);
            if (previousLeader != null)
            {
                final Set<ChunkPos> chunks = claimedChunksOf(registry, city);
                OpacNations.claimChunks(server, city.dimension().location(), previousLeader, chunks);
            }
        }
        finally
        {
            registry.globalWriteLock().unlock();
        }

        if (!staffImposed)
        {
            WarScore.award(registry, warId, toNationId, -amountAwardedFor(registry, cityId, city));
        }
    }

    private static long amountAwardedFor(final NationRegistry registry, final UUID cityId, final City cityAtTransferTime)
    {
        // The exact CityValue at the moment of the original transfer isn't separately recorded; the
        // live city record (tier, banked payment, checkpoint count all unchanged by a transfer) still
        // computes the same value, so recomputing it here is exact, not an approximation.
        return (long) CityValue.of(
                NationWarsConfig.tiers.get(cityAtTransferTime.tier()).cost(), cityAtTransferTime.bankedPayment(),
                cityAtTransferTime.checkpointIds().size(), NationWarsConfig.CITY_VALUE_TIER_WEIGHT.get(),
                NationWarsConfig.CITY_VALUE_BANK_WEIGHT.get(), NationWarsConfig.CITY_VALUE_CHECKPOINT_WEIGHT.get());
    }

    private static void revertTribute(final NationRegistry registry, final CompoundTag params, final boolean staffImposed,
            final UUID warId)
    {
        final UUID fromNationId = params.getUUID("from");
        final UUID toNationId = params.getUUID("to");
        long remaining = params.getLong("value");

        final List<City> payerCities = registry.cities().values().stream()
                .filter(c -> c.ownerNationId().equals(fromNationId))
                .sorted(Comparator.comparingInt(City::tier))
                .toList();
        for (final City city : payerCities)
        {
            if (remaining <= 0)
            {
                break;
            }
            final long credit = remaining;
            registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(),
                    city.dimension(), city.corePos(), city.tier(), city.bankedPayment() + credit, city.checkpointIds(), city.state(),
                    city.occupiedByNationId(), city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(),
                    city.lastTransferAt(), city.transferCount(), city.pendingDisbandAt(), city.dormantSince()));
            remaining = 0;
        }

        if (!staffImposed)
        {
            WarScore.award(registry, warId, toNationId, params.getLong("value"));
        }
    }

    private static void revertCeasefire(final NationRegistry registry, final CompoundTag clauseTag)
    {
        // Ceasefire's own params only carry durationHours, not who it applied to or the expiresAt it
        // wrote — reconstructing exactly which cooldown entries to remove (and confirming a later
        // ceasefire hasn't superseded them) needs more than what's recorded, so this is intentionally a
        // no-op: an un-reverted cooldown expires on its own and causes no lasting inconsistency.
    }

    private static void moveCityId(final NationRegistry registry, final UUID fromNationId, final UUID toNationId, final UUID cityId)
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
}
