package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarJoinContext;
import org.pixelfire.nationwars.state.WarJoinFailureReason;
import org.pixelfire.nationwars.state.WarJoinPreconditions;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /war join <warId> attackers}: voluntary attacker-side enlistment, subject to the same
 * cooldown/lock checks as a declaration — the joiner's own eligibility only, since the
 * target's readiness was already established when the war was declared.
 */
public final class WarJoinService
{
    private WarJoinService()
    {
    }

    public static Optional<WarJoinFailureReason> join(final MinecraftServer server, final ServerPlayer joiningPlayer, final War war)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final NationSnapshot joiner = OpacNations.nationOf(server, joiningPlayer);
        final long now = System.currentTimeMillis();

        if (joiner == null)
        {
            return Optional.of(WarJoinFailureReason.NOT_NATION_OWNER);
        }

        final boolean alreadyIn = war.attackers().members().contains(joiner.nationId())
                || war.defenders().members().contains(joiner.nationId())
                || war.attackers().pendingMembers().containsKey(joiner.nationId())
                || war.defenders().pendingMembers().containsKey(joiner.nationId());
        final boolean joinable = war.phase() == WarPhase.PREPARATION || war.phase() == WarPhase.ACTIVE || war.phase() == WarPhase.SUSPENDED;

        final NationState joinerState = registry.nationStates().getOrDefault(joiner.nationId(), NationState.empty(joiner.nationId()));
        final long cooldownExpiresAt = joinerState.warCooldowns().getOrDefault(war.defenders().primaryNationId(), 0L);
        final boolean unsettledWarExists = WarDeclarationService.findUnsettledWar(registry, joiner.nationId(),
                war.defenders().primaryNationId()) != null;
        final boolean atWarCap = WarDeclarationService.countUnsettledWars(registry, joiner.nationId())
                >= NationWarsConfig.MAX_CONCURRENT_WARS.get();

        final WarJoinContext context = new WarJoinContext(joiner.isOwner(), alreadyIn, joinable, unsettledWarExists,
                now, cooldownExpiresAt, joinerState.lockedByWarId() != null, atWarCap);

        final Optional<WarJoinFailureReason> failure = WarJoinPreconditions.check(context);
        if (failure.isPresent())
        {
            return failure;
        }

        commit(registry, war, joiner.nationId());
        return Optional.empty();
    }

    private static void commit(final NationRegistry registry, final War war, final UUID joinerId)
    {
        registry.stripedLocks().withLocks(() ->
        {
            final War current = registry.wars().get(war.warId());
            if (current == null)
            {
                return;
            }
            final Set<UUID> members = new HashSet<>(current.attackers().members());
            members.add(joinerId);
            final Coalition newAttackers = new Coalition(Set.copyOf(members), current.attackers().pendingMembers(),
                    current.attackers().primaryNationId());
            registry.wars().put(war.warId(), new War(current.warId(), newAttackers, current.defenders(), current.phase(),
                    current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                    current.occupiedCityIds(), current.warScore(), current.suspendedSince(), current.contestedTimeMs(),
                    current.settlementDeadline(), current.outcome(), current.memberTargetableAt()));

            final NationState currentState = registry.nationStates().getOrDefault(joinerId, NationState.empty(joinerId));
            final Set<UUID> activeWarIds = new HashSet<>(currentState.activeWarIds());
            activeWarIds.add(war.warId());
            registry.nationStates().put(joinerId, new NationState(joinerId, currentState.cityIds(), currentState.capitalCityId(),
                    Set.copyOf(activeWarIds), currentState.warCooldowns(), currentState.lastCityFoundedAt(), currentState.lockedByWarId()));
        }, war.warId(), joinerId);

        final CompoundTag after = new CompoundTag();
        after.putUUID("nationId", joinerId);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", joinerId, ActorRole.LEADER, AuditSource.COMMAND,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "war_joined"), List.of(war.warId(), joinerId),
                new CompoundTag(), after, false));
    }
}
