package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.state.War;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code ReleaseOccupation(cityId)}: unfreezes checkpoints and restores them to the city's owner,
 * without transferring ownership. Removes the city from {@code occupiedCityIds} — the counterpart to the
 * bug fixed in {@code CaptureTickListener.releaseOccupation}, which now does the same on lock expiry.
 */
public final class ReleaseOccupationClause implements PeaceClause
{
    public static final ResourceLocation ID = ResourceLocation.tryBuild("nationwars", "release_occupation");

    @Override
    public Optional<String> validate(final NationRegistry registry, final War war, final CompoundTag params)
    {
        final UUID cityId = params.getUUID("cityId");
        final City city = registry.cities().get(cityId);
        if (city == null || city.state() != CityState.OCCUPIED || !war.occupiedCityIds().contains(cityId))
        {
            return Optional.of("city is not currently occupied in this war");
        }
        return Optional.empty();
    }

    @Override
    public void apply(final NationRegistry registry, final MinecraftServer server, final War war, final CompoundTag params)
    {
        final UUID cityId = params.getUUID("cityId");
        final City city = registry.cities().get(cityId);
        if (city == null)
        {
            return;
        }

        registry.cities().put(cityId, new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(),
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

        final War current = registry.wars().getOrDefault(war.warId(), war);
        final Set<UUID> occupied = new HashSet<>(current.occupiedCityIds());
        occupied.remove(cityId);
        registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(), Set.copyOf(occupied),
                current.warScore(), current.suspendedSince(), current.contestedTimeMs(), current.settlementDeadline(),
                current.outcome(), current.memberTargetableAt()));
    }
}
