package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.war.WarScore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code Tribute(from, to, value)}: {@code value} is deducted from the payer's cities' banked payment,
 * highest tier first. "Priced the same way" as {@code TransferCity} is read here as: the
 * recipient also spends {@code value} of their own war score to receive it, the same affordability rule
 * that gates a city transfer — not stated as an explicit clause parameter, but the only reading that
 * keeps tribute from being free spoils for whichever side proposes it.
 */
public final class TributeClause implements PeaceClause
{
    public static final ResourceLocation ID = ResourceLocation.tryBuild("nationwars", "tribute");

    @Override
    public Optional<String> validate(final NationRegistry registry, final War war, final CompoundTag params, final boolean staffImposed)
    {
        final UUID toNationId = params.getUUID("to");
        final long value = params.getLong("value");

        final long available = payerCities(registry, params.getUUID("from")).stream().mapToLong(City::bankedPayment).sum();
        if (available < value)
        {
            return Optional.of("payer cannot cover this tribute (needs " + value + ", has " + available
                    + " banked, short " + (value - available) + ")");
        }
        if (staffImposed)
        {
            return Optional.empty();
        }
        final long recipientScore = war.warScore().getOrDefault(toNationId, 0L);
        if (recipientScore < value)
        {
            return Optional.of("recipient lacks war score to receive this tribute (needs " + value
                    + ", has " + recipientScore + ", short " + (value - recipientScore) + ")");
        }
        return Optional.empty();
    }

    @Override
    public void apply(final NationRegistry registry, final MinecraftServer server, final War war, final CompoundTag params,
            final boolean staffImposed)
    {
        final UUID toNationId = params.getUUID("to");
        long remaining = params.getLong("value");

        for (final City city : payerCities(registry, params.getUUID("from")))
        {
            if (remaining <= 0)
            {
                break;
            }
            final long deduction = Math.min(remaining, city.bankedPayment());
            if (deduction > 0)
            {
                registry.cities().put(city.cityId(), new City(city.cityId(), city.name(), city.ownerNationId(),
                        city.founderNationId(), city.dimension(), city.corePos(), city.tier(), city.bankedPayment() - deduction,
                        city.checkpointIds(), city.state(), city.occupiedByNationId(), city.occupiedSince(),
                        city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(), city.transferCount(),
                        city.pendingDisbandAt(), city.dormantSince()));
                remaining -= deduction;
            }
        }

        if (!staffImposed)
        {
            registry.wars().put(war.warId(), WarScore.applyAward(registry.wars().getOrDefault(war.warId(), war),
                    toNationId, -params.getLong("value")));
        }
    }

    private static List<City> payerCities(final NationRegistry registry, final UUID nationId)
    {
        return registry.cities().values().stream()
                .filter(city -> city.ownerNationId().equals(nationId))
                .sorted(Comparator.comparingInt(City::tier).reversed())
                .toList();
    }
}
