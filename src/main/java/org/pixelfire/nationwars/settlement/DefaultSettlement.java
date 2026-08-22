package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The {@code settlementWindow} backstop's default outcome: every occupied city transfers to
 * its occupier, plus a ceasefire. Also usable directly as the staff {@code apply-occupations} command,
 * since both are the exact same clause list.
 */
public final class DefaultSettlement
{
    private DefaultSettlement()
    {
    }

    public static List<StagedClause> applyOccupationsClauses(final NationRegistry registry, final War war)
    {
        final List<StagedClause> clauses = new ArrayList<>();
        for (final UUID cityId : war.occupiedCityIds())
        {
            final City city = registry.cities().get(cityId);
            if (city == null)
            {
                continue;
            }
            final boolean ownerIsDefender = war.defenders().members().contains(city.ownerNationId());
            final UUID occupierPrimary = ownerIsDefender ? war.attackers().primaryNationId() : war.defenders().primaryNationId();

            final CompoundTag params = new CompoundTag();
            params.putUUID("cityId", cityId);
            params.putUUID("toNationId", occupierPrimary);
            clauses.add(new StagedClause(TransferCityClause.ID, params));
        }
        clauses.add(ceasefire());
        return clauses;
    }

    /**
     * {@code status-quo}: releases every occupation without transferring anything, plus a ceasefire.
     */
    public static List<StagedClause> statusQuoClauses(final War war)
    {
        final List<StagedClause> clauses = new ArrayList<>();
        for (final UUID cityId : war.occupiedCityIds())
        {
            final CompoundTag params = new CompoundTag();
            params.putUUID("cityId", cityId);
            clauses.add(new StagedClause(ReleaseOccupationClause.ID, params));
        }
        clauses.add(ceasefire());
        return clauses;
    }

    private static StagedClause ceasefire()
    {
        final CompoundTag params = new CompoundTag();
        params.putLong("durationHours", NationWarsConfig.DEFAULT_POST_WAR_COOLDOWN_HOURS.get());
        return new StagedClause(CeasefireClause.ID, params);
    }
}
