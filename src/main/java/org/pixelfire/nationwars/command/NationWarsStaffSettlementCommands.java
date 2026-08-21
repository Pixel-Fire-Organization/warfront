package org.pixelfire.nationwars.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.settlement.CeasefireClause;
import org.pixelfire.nationwars.settlement.DefaultSettlement;
import org.pixelfire.nationwars.settlement.SettlementApplier;
import org.pixelfire.nationwars.settlement.TransferCityClause;
import org.pixelfire.nationwars.settlement.TributeClause;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /nationwars staff war settle|finalize}. Staff resolve wars only when the
 * belligerents cannot, so this ignores war score limits entirely ({@code staffImposed = true}) while
 * still enforcing every structural check {@link org.pixelfire.nationwars.state.PeaceClause} does. Staged
 * clauses live in the same draft tracker as player negotiation, keyed under a nil-UUID sentinel nation id
 * since no OPAC party ever has that id — the whole registry has no persistence yet, so this draft, like a
 * player's, doesn't survive a restart either.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsStaffSettlementCommands
{
    private static final UUID STAFF_DRAFT_KEY = new UUID(0L, 0L);

    private NationWarsStaffSettlementCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("war")
                                .then(Commands.literal("settle")
                                        .requires(NationWarsStaffSettlementCommands::hasStaffSettlePermission)
                                        .then(Commands.argument("warId", UuidArgument.uuid())
                                                .executes(NationWarsStaffSettlementCommands::review)
                                                .then(Commands.literal("apply-occupations")
                                                        .executes(NationWarsStaffSettlementCommands::applyOccupations))
                                                .then(Commands.literal("status-quo")
                                                        .executes(NationWarsStaffSettlementCommands::statusQuo))
                                                .then(Commands.literal("transfer")
                                                        .then(Commands.argument("city", StringArgumentType.string())
                                                                .then(Commands.argument("nation", StringArgumentType.greedyString())
                                                                        .executes(NationWarsStaffSettlementCommands::transfer))))
                                                .then(Commands.literal("tribute")
                                                        .then(Commands.argument("from", StringArgumentType.string())
                                                                .then(Commands.argument("to", StringArgumentType.string())
                                                                        .then(Commands.argument("value", LongArgumentType.longArg(1))
                                                                                .executes(NationWarsStaffSettlementCommands::tribute)))))
                                                .then(Commands.literal("ceasefire")
                                                        .then(Commands.argument("hours", LongArgumentType.longArg(1))
                                                                .executes(NationWarsStaffSettlementCommands::ceasefire)))
                                                .then(Commands.literal("review").executes(NationWarsStaffSettlementCommands::review))
                                                .then(Commands.literal("clear").executes(NationWarsStaffSettlementCommands::clear))))
                                .then(Commands.literal("finalize")
                                        .requires(NationWarsStaffSettlementCommands::hasStaffSettlePermission)
                                        .then(Commands.argument("warId", UuidArgument.uuid())
                                                .executes(NationWarsStaffSettlementCommands::finalizeSettlement))))));
    }

    private static boolean hasStaffSettlePermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_SETTLE);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static Optional<War> resolveWar(final CommandContext<CommandSourceStack> context)
    {
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return Optional.empty();
        }
        return Optional.of(war);
    }

    private static int applyOccupations(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final List<StagedClause> clauses = DefaultSettlement.applyOccupationsClauses(registry, war.get());
        replaceDraft(war.get().warId(), clauses);
        context.getSource().sendSuccess(() -> Component.literal("Staged: every occupied city transfers to its occupier."), true);
        return 1;
    }

    private static int statusQuo(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final List<StagedClause> clauses = DefaultSettlement.statusQuoClauses(war.get());
        replaceDraft(war.get().warId(), clauses);
        context.getSource().sendSuccess(() -> Component.literal("Staged: every occupation released."), true);
        return 1;
    }

    private static int transfer(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final String cityName = StringArgumentType.getString(context, "city");
        final City city = registry.cities().values().stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(cityName))
                .findFirst().orElse(null);
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final String nationName = StringArgumentType.getString(context, "nation");
        final UUID toNationId = OpacNations.findNationByName(context.getSource().getServer(), nationName);
        if (toNationId == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation."));
            return 0;
        }
        final CompoundTag params = new CompoundTag();
        params.putUUID("cityId", city.cityId());
        params.putUUID("toNationId", toNationId);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(war.get().warId(), STAFF_DRAFT_KEY,
                new StagedClause(TransferCityClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal("Staged: transfer " + city.name() + " to " + nationName + "."), true);
        return 1;
    }

    private static int tribute(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final String fromName = StringArgumentType.getString(context, "from");
        final String toName = StringArgumentType.getString(context, "to");
        final UUID fromNationId = OpacNations.findNationByName(context.getSource().getServer(), fromName);
        final UUID toNationId = OpacNations.findNationByName(context.getSource().getServer(), toName);
        if (fromNationId == null || toNationId == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation."));
            return 0;
        }
        final long value = LongArgumentType.getLong(context, "value");
        final CompoundTag params = new CompoundTag();
        params.putUUID("from", fromNationId);
        params.putUUID("to", toNationId);
        params.putLong("value", value);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(war.get().warId(), STAFF_DRAFT_KEY,
                new StagedClause(TributeClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal("Staged: tribute of " + value + " from " + fromName + " to " + toName + "."), true);
        return 1;
    }

    private static int ceasefire(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final long hours = LongArgumentType.getLong(context, "hours");
        final CompoundTag params = new CompoundTag();
        params.putLong("durationHours", hours);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(war.get().warId(), STAFF_DRAFT_KEY,
                new StagedClause(CeasefireClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal("Staged: ceasefire of " + hours + "h."), true);
        return 1;
    }

    private static int review(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        final List<StagedClause> draft = NationWarsMod.get().getNegotiationDraftTracker().get(war.get().warId(), STAFF_DRAFT_KEY);
        context.getSource().sendSuccess(() -> Component.literal("Staged settlement for " + war.get().warId() + ": "
                + draft.size() + " clause(s)."), false);
        return 1;
    }

    private static int clear(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> war = resolveWar(context);
        if (war.isEmpty())
        {
            return 0;
        }
        NationWarsMod.get().getNegotiationDraftTracker().clear(war.get().warId(), STAFF_DRAFT_KEY);
        context.getSource().sendSuccess(() -> Component.literal("Staged settlement cleared."), false);
        return 1;
    }

    private static int finalizeSettlement(final CommandContext<CommandSourceStack> context)
    {
        final Optional<War> warOptional = resolveWar(context);
        if (warOptional.isEmpty())
        {
            return 0;
        }
        final War war = warOptional.get();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final List<StagedClause> clauses = NationWarsMod.get().getNegotiationDraftTracker().get(war.warId(), STAFF_DRAFT_KEY);
        if (clauses.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("Nothing staged — use /nationwars staff war settle first."));
            return 0;
        }
        final WarOutcome outcome = war.outcome() != null ? war.outcome() : WarOutcome.STAFF_SETTLEMENT;
        final Optional<String> failure = SettlementApplier.apply(context.getSource().getServer(), registry, war, clauses, outcome, true);
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get()));
            return 0;
        }
        NationWarsMod.get().getNegotiationDraftTracker().clear(war.warId(), STAFF_DRAFT_KEY);
        context.getSource().sendSuccess(() -> Component.literal("Settlement finalized for war " + war.warId() + "."), true);
        return 1;
    }

    private static void replaceDraft(final UUID warId, final List<StagedClause> clauses)
    {
        NationWarsMod.get().getNegotiationDraftTracker().clear(warId, STAFF_DRAFT_KEY);
        for (final StagedClause clause : clauses)
        {
            NationWarsMod.get().getNegotiationDraftTracker().addClause(warId, STAFF_DRAFT_KEY, clause);
        }
    }
}
