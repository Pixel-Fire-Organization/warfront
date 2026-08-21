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
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.settlement.CeasefireClause;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.settlement.TransferCityClause;
import org.pixelfire.nationwars.settlement.TributeClause;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceSettlement;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code /war negotiate} command fallback. There is no dedicated-packet GUI screen —
 * that's client rendering work (Stage 21) — so this is the whole negotiation surface for now, not a
 * fallback for one that already exists.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsNegotiationCommands
{
    private NationWarsNegotiationCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("war")
                .then(Commands.literal("negotiate")
                        .then(Commands.argument("warId", UuidArgument.uuid())
                                .then(Commands.literal("offer")
                                        .then(Commands.literal("city")
                                                .then(Commands.argument("city", StringArgumentType.greedyString())
                                                        .executes(context -> offerOrDemandCity(context, true))))
                                        .then(Commands.literal("tribute")
                                                .then(Commands.argument("value", LongArgumentType.longArg(1))
                                                        .executes(NationWarsNegotiationCommands::offerTribute))))
                                .then(Commands.literal("demand")
                                        .then(Commands.literal("city")
                                                .then(Commands.argument("city", StringArgumentType.greedyString())
                                                        .executes(context -> offerOrDemandCity(context, false)))))
                                .then(Commands.literal("ceasefire")
                                        .then(Commands.argument("hours", LongArgumentType.longArg(1))
                                                .executes(NationWarsNegotiationCommands::ceasefire)))
                                .then(Commands.literal("review").executes(NationWarsNegotiationCommands::review))
                                .then(Commands.literal("send").executes(NationWarsNegotiationCommands::send))
                                .then(Commands.literal("clear").executes(NationWarsNegotiationCommands::clear))
                                .then(Commands.literal("accept").executes(NationWarsNegotiationCommands::accept))
                                .then(Commands.literal("reject").executes(NationWarsNegotiationCommands::reject))
                                .then(Commands.literal("counter").executes(NationWarsNegotiationCommands::counter)))));
    }

    private record Actor(ServerPlayer player, War war, NationSnapshot nation)
    {
    }

    private static Optional<Actor> resolveActor(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (player == null || war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return Optional.empty();
        }
        final NationSnapshot nation = OpacNations.nationOf(context.getSource().getServer(), player);
        final boolean belligerent = nation != null
                && (war.attackers().members().contains(nation.nationId()) || war.defenders().members().contains(nation.nationId()));
        if (!belligerent)
        {
            context.getSource().sendFailure(Component.literal("Your nation is not a belligerent in this war."));
            return Optional.empty();
        }
        return Optional.of(new Actor(player, war, nation));
    }

    private static int offerOrDemandCity(final CommandContext<CommandSourceStack> context, final boolean offering)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final String cityName = StringArgumentType.getString(context, "city");
        final City city = NegotiationService.findCityByName(registry, cityName);
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        final boolean isAttacker = a.war().attackers().members().contains(a.nation().nationId());
        final UUID otherPrimary = isAttacker ? a.war().defenders().primaryNationId() : a.war().attackers().primaryNationId();
        final UUID toNationId = offering ? otherPrimary : a.nation().nationId();

        final CompoundTag params = new CompoundTag();
        params.putUUID("cityId", city.cityId());
        params.putUUID("toNationId", toNationId);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(a.war().warId(), a.nation().nationId(),
                new StagedClause(TransferCityClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal((offering ? "Offering " : "Demanding ") + city.name() + "."), false);
        return 1;
    }

    private static int offerTribute(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final long value = LongArgumentType.getLong(context, "value");
        final boolean isAttacker = a.war().attackers().members().contains(a.nation().nationId());
        final UUID otherPrimary = isAttacker ? a.war().defenders().primaryNationId() : a.war().attackers().primaryNationId();

        final CompoundTag params = new CompoundTag();
        params.putUUID("from", a.nation().nationId());
        params.putUUID("to", otherPrimary);
        params.putLong("value", value);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(a.war().warId(), a.nation().nationId(),
                new StagedClause(TributeClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal("Offering " + value + " tribute."), false);
        return 1;
    }

    private static int ceasefire(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final long hours = LongArgumentType.getLong(context, "hours");
        final CompoundTag params = new CompoundTag();
        params.putLong("durationHours", hours);
        NationWarsMod.get().getNegotiationDraftTracker().addClause(a.war().warId(), a.nation().nationId(),
                new StagedClause(CeasefireClause.ID, params));
        context.getSource().sendSuccess(() -> Component.literal("Ceasefire of " + hours + "h added to your draft."), false);
        return 1;
    }

    private static int review(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final PeaceSettlement active = NationWarsMod.get().getNationRegistry().settlements().get(a.war().warId());
        if (active != null && !active.anyRejected())
        {
            context.getSource().sendSuccess(() -> Component.literal("Active offer: " + active.clauses().size()
                    + " clause(s), ratifications: " + active.ratifications()), false);
        }
        else
        {
            final List<StagedClause> draft = NationWarsMod.get().getNegotiationDraftTracker().get(a.war().warId(), a.nation().nationId());
            context.getSource().sendSuccess(() -> Component.literal("Your draft: " + draft.size() + " clause(s)."), false);
        }
        return 1;
    }

    private static int send(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final Optional<String> failure = NegotiationService.send(NationWarsMod.get().getNationRegistry(), a.war(),
                a.nation().nationId(), NationWarsMod.get().getNegotiationDraftTracker());
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Offer sent."), true);
        return 1;
    }

    private static int clear(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        NationWarsMod.get().getNegotiationDraftTracker().clear(a.war().warId(), a.nation().nationId());
        context.getSource().sendSuccess(() -> Component.literal("Draft cleared."), false);
        return 1;
    }

    private static int accept(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final Optional<String> failure = NegotiationService.accept(context.getSource().getServer(),
                NationWarsMod.get().getNationRegistry(), a.war(), a.nation().nationId());
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Signed."), true);
        return 1;
    }

    private static int reject(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final Optional<String> failure = NegotiationService.reject(NationWarsMod.get().getNationRegistry(), a.war(), a.nation().nationId());
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Rejected."), true);
        return 1;
    }

    private static int counter(final CommandContext<CommandSourceStack> context)
    {
        final Optional<Actor> actor = resolveActor(context);
        if (actor.isEmpty())
        {
            return 0;
        }
        final Actor a = actor.get();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        NegotiationService.reject(registry, a.war(), a.nation().nationId());
        final Optional<String> failure = NegotiationService.send(registry, a.war(), a.nation().nationId(),
                NationWarsMod.get().getNegotiationDraftTracker());
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Countered with your own offer."), true);
        return 1;
    }
}
