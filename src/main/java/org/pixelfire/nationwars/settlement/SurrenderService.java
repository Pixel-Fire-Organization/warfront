package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /war surrender}: transfers every occupied city the surrendering nation owns to its occupier and
 * applies a ceasefire, atomically via {@link SettlementApplier}. Surrendering with nothing occupied
 * still applies — it concedes the war at the cost of the cooldown only.
 */
public final class SurrenderService
{
    private SurrenderService()
    {
    }

    public static Optional<String> surrender(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID surrenderingNationId)
    {
        if (!war.attackers().members().contains(surrenderingNationId) && !war.defenders().members().contains(surrenderingNationId))
        {
            return Optional.of("Your nation is not a belligerent in this war.");
        }

        final boolean surrendererIsDefender = war.defenders().members().contains(surrenderingNationId);
        final UUID occupierPrimary = surrendererIsDefender ? war.attackers().primaryNationId() : war.defenders().primaryNationId();

        final List<StagedClause> clauses = new ArrayList<>();
        for (final UUID cityId : war.occupiedCityIds())
        {
            final City city = registry.cities().get(cityId);
            if (city != null && city.ownerNationId().equals(surrenderingNationId))
            {
                final CompoundTag params = new CompoundTag();
                params.putUUID("cityId", cityId);
                params.putUUID("toNationId", occupierPrimary);
                clauses.add(new StagedClause(TransferCityClause.ID, params));
            }
        }

        final CompoundTag ceasefireParams = new CompoundTag();
        ceasefireParams.putLong("durationHours", NationWarsConfig.DEFAULT_POST_WAR_COOLDOWN_HOURS.get());
        clauses.add(new StagedClause(CeasefireClause.ID, ceasefireParams));

        return SettlementApplier.apply(server, registry, war, clauses, WarOutcome.SURRENDER);
    }
}
