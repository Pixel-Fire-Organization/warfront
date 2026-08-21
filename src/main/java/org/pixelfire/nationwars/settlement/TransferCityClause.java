package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.CityValue;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.war.WarScore;
import org.pixelfire.nationwars.world.ClaimSetComputation;
import org.pixelfire.nationwars.world.ClaimShape;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code TransferCity(cityId, toNationId)}: the recipient must have enough war score to cover
 * {@link CityValue}; ownership, tier, banked payment, and checkpoints carry over unchanged, with a fresh
 * occupation lock so the loser can't instantly counter-attack.
 */
public final class TransferCityClause implements PeaceClause
{
    public static final ResourceLocation ID = ResourceLocation.tryBuild("nationwars", "transfer_city");

    @Override
    public Optional<String> validate(final NationRegistry registry, final War war, final CompoundTag params)
    {
        final UUID cityId = params.getUUID("cityId");
        final UUID toNationId = params.getUUID("toNationId");
        final City city = registry.cities().get(cityId);
        if (city == null)
        {
            return Optional.of("city no longer exists");
        }
        if (!war.attackers().members().contains(city.ownerNationId()) && !war.defenders().members().contains(city.ownerNationId()))
        {
            return Optional.of("city is not owned by a belligerent of this war");
        }
        final double cityValue = valueOf(city);
        final long recipientScore = war.warScore().getOrDefault(toNationId, 0L);
        if (recipientScore < cityValue)
        {
            return Optional.of("recipient lacks war score to cover this city (needs " + (long) cityValue
                    + ", has " + recipientScore + ", short " + ((long) cityValue - recipientScore) + ")");
        }
        return Optional.empty();
    }

    @Override
    public void apply(final NationRegistry registry, final MinecraftServer server, final War war, final CompoundTag params)
    {
        final UUID cityId = params.getUUID("cityId");
        final UUID toNationId = params.getUUID("toNationId");
        final City city = registry.cities().get(cityId);
        if (city == null)
        {
            return;
        }
        final long cityValue = (long) valueOf(city);
        final long now = System.currentTimeMillis();
        final long lockUntil = now + NationWarsConfig.OCCUPATION_LOCK_DURATION_SECONDS.get() * 1000L;

        registry.wars().put(war.warId(), WarScore.applyAward(registry.wars().getOrDefault(war.warId(), war), toNationId, -cityValue));

        registry.cities().put(cityId, new City(city.cityId(), city.name(), toNationId, city.founderNationId(), city.dimension(),
                city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), CityState.ACTIVE, null, 0L, lockUntil,
                city.foundedAt(), now, city.transferCount() + 1, city.pendingDisbandAt(), city.dormantSince()));

        final UUID newLeader = OpacNations.leaderUuidOf(server, toNationId);
        if (newLeader != null)
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
            OpacNations.claimChunks(server, city.dimension().location(), newLeader, chunks);
        }
    }

    private static double valueOf(final City city)
    {
        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());
        return CityValue.of(tier.cost(), city.bankedPayment(), city.checkpointIds().size(),
                NationWarsConfig.CITY_VALUE_TIER_WEIGHT.get(), NationWarsConfig.CITY_VALUE_BANK_WEIGHT.get(),
                NationWarsConfig.CITY_VALUE_CHECKPOINT_WEIGHT.get());
    }
}
