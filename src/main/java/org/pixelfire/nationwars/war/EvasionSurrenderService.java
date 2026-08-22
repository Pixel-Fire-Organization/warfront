package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.settlement.TransferCityClause;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the automatic evasion surrender for one nation. Unlike {@code /war surrender}, this
 * doesn't necessarily end the whole war: a coalition member other than the primary simply exits and the
 * fight continues for its allies. A primary evasion-surrendering is treated the same as the war's last
 * active member doing so, since the primary's identity is fixed for the war's whole life and nothing
 * else in the model can take over negotiating in its place.
 */
public final class EvasionSurrenderService
{
    private EvasionSurrenderService()
    {
    }

    public static void applyEvasionSurrender(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID nationId)
    {
        final War current = registry.wars().getOrDefault(war.warId(), war);
        final boolean isDefender = current.defenders().members().contains(nationId);
        final Coalition ownSide = isDefender ? current.defenders() : current.attackers();
        final Coalition otherSide = isDefender ? current.attackers() : current.defenders();
        final UUID opponentPrimary = otherSide.primaryNationId();
        final boolean endsWar = ownSide.members().size() <= 1 || nationId.equals(ownSide.primaryNationId());

        final Set<UUID> transferredCityIds = transferOccupiedCities(server, registry, current, nationId, opponentPrimary);

        if (endsWar)
        {
            final War afterTransfer = registry.wars().getOrDefault(war.warId(), current);
            WarTermination.conclude(registry, afterTransfer, WarOutcome.EVASION_SURRENDER, System.currentTimeMillis());
        }
        else
        {
            exitCoalition(registry, war.warId(), isDefender, nationId, opponentPrimary, transferredCityIds);
        }

        final CompoundTag after = new CompoundTag();
        after.putBoolean("endedWar", endsWar);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", nationId, ActorRole.SYSTEM, AuditSource.AUTO,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "war_evasion_surrender"), List.of(war.warId(), nationId),
                new CompoundTag(), after, false));
    }

    private static Set<UUID> transferOccupiedCities(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID nationId, final UUID opponentPrimary)
    {
        final Set<UUID> transferred = new HashSet<>();
        final TransferCityClause transferClause = new TransferCityClause();
        for (final UUID cityId : Set.copyOf(war.occupiedCityIds()))
        {
            final City city = registry.cities().get(cityId);
            if (city == null || !city.ownerNationId().equals(nationId))
            {
                continue;
            }
            final CompoundTag params = new CompoundTag();
            params.putUUID("cityId", cityId);
            params.putUUID("toNationId", opponentPrimary);
            transferClause.apply(registry, server, registry.wars().getOrDefault(war.warId(), war), params, true);
            transferred.add(cityId);
        }
        return transferred;
    }

    private static void exitCoalition(final NationRegistry registry, final UUID warId, final boolean isDefender,
            final UUID nationId, final UUID opponentPrimary, final Set<UUID> transferredCityIds)
    {
        final long cooldownExpiresAt = System.currentTimeMillis() + NationWarsConfig.DEFAULT_POST_WAR_COOLDOWN_HOURS.get() * 3_600_000L;

        registry.stripedLocks().withLocks(() ->
        {
            final War latest = registry.wars().get(warId);
            if (latest == null)
            {
                return;
            }
            final Set<UUID> occupied = new HashSet<>(latest.occupiedCityIds());
            occupied.removeAll(transferredCityIds);

            final Coalition updatedSide = removeMember(isDefender ? latest.defenders() : latest.attackers(), nationId);
            registry.wars().put(warId, new War(latest.warId(),
                    isDefender ? latest.attackers() : updatedSide,
                    isDefender ? updatedSide : latest.defenders(),
                    latest.phase(), latest.declaredAt(), latest.activeAt(), latest.warExpiresAt(), latest.targetCityIds(),
                    Set.copyOf(occupied), latest.warScore(), latest.suspendedSince(), latest.contestedTimeMs(),
                    latest.settlementDeadline(), latest.outcome(), latest.memberTargetableAt()));

            WarTermination.releaseNation(registry, nationId, warId, opponentPrimary, cooldownExpiresAt);
        }, warId, nationId);
    }

    private static Coalition removeMember(final Coalition coalition, final UUID nationId)
    {
        final Set<UUID> members = new HashSet<>(coalition.members());
        members.remove(nationId);
        return new Coalition(Set.copyOf(members), coalition.pendingMembers(), coalition.primaryNationId());
    }
}
