package org.pixelfire.nationwars.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.util.UUID;

/**
 * The vanilla-client fallback's city side: {@code /city info|list|checkpoints} put every city HUD fact
 * (tier, checkpoints held/total, occupation state and countdown) in reach of a plain chat command.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsCityCommands
{
    private NationWarsCityCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("city")
                .then(Commands.literal("info")
                        .executes(NationWarsCityCommands::infoOwnCapital)
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(NationWarsCityCommands::infoNamed)))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, null))
                        .then(Commands.argument("nation", StringArgumentType.greedyString())
                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "nation")))))
                .then(Commands.literal("checkpoints")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(NationWarsCityCommands::checkpoints))));
    }

    private static int infoOwnCapital(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null)
        {
            context.getSource().sendFailure(Component.literal("Specify a city name."));
            return 0;
        }
        final NationSnapshot nation = OpacNations.nationOf(context.getSource().getServer(), player);
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final var nationState = nation == null ? null : registry.nationStates().get(nation.nationId());
        if (nationState == null || nationState.capitalCityId() == null)
        {
            context.getSource().sendFailure(Component.literal("Your nation has no capital set. Specify a city name."));
            return 0;
        }
        final City city = registry.cities().get(nationState.capitalCityId());
        if (city == null)
        {
            return 0;
        }
        describeCity(context, city);
        return 1;
    }

    private static int infoNamed(final CommandContext<CommandSourceStack> context)
    {
        final String name = StringArgumentType.getString(context, "name");
        final City city = NegotiationService.findCityByName(NationWarsMod.get().getNationRegistry(), name);
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        describeCity(context, city);
        return 1;
    }

    private static void describeCity(final CommandContext<CommandSourceStack> context, final City city)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final int held = (int) city.checkpointIds().stream()
                .map(registry.checkpoints()::get)
                .filter(cp -> cp != null && cp.holderNationId().equals(city.ownerNationId()))
                .count();
        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());

        context.getSource().sendSuccess(() -> Component.literal(city.name() + " (tier " + (city.tier() + 1)
                + ", upgrade cost " + tier.cost() + ") — owner " + city.ownerNationId() + ", state " + city.state()
                + ", checkpoints " + held + "/" + city.checkpointIds().size() + ", banked payment " + city.bankedPayment()), false);

        if (city.state() == CityState.OCCUPIED)
        {
            final long remainingMs = Math.max(0L, city.occupationLockUntil() - System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("  OCCUPIED by " + city.occupiedByNationId()
                    + ", lock releases in " + (remainingMs / 60_000L) + " minute(s)"), false);
        }
    }

    private static int list(final CommandContext<CommandSourceStack> context, final String nationName)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final UUID nationId = nationName == null ? null : OpacNations.findNationByName(context.getSource().getServer(), nationName);
        if (nationName != null && nationId == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation."));
            return 0;
        }

        int count = 0;
        for (final City city : registry.cities().values())
        {
            if (nationId != null && !city.ownerNationId().equals(nationId))
            {
                continue;
            }
            count++;
            context.getSource().sendSuccess(() -> Component.literal(city.name() + " — tier " + (city.tier() + 1)
                    + ", " + city.state() + ", owner " + city.ownerNationId()), false);
        }
        if (count == 0)
        {
            context.getSource().sendSuccess(() -> Component.literal("No cities found."), false);
        }
        return count;
    }

    private static int checkpoints(final CommandContext<CommandSourceStack> context)
    {
        final String name = StringArgumentType.getString(context, "name");
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final City city = NegotiationService.findCityByName(registry, name);
        if (city == null)
        {
            context.getSource().sendFailure(Component.literal("No such city."));
            return 0;
        }
        if (city.checkpointIds().isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.literal(city.name() + " has no checkpoints."), false);
            return 1;
        }
        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint == null)
            {
                continue;
            }
            context.getSource().sendSuccess(() -> Component.literal("  " + checkpointId + " @ " + checkpoint.pos().toShortString()
                    + " — holder " + checkpoint.holderNationId() + ", status " + checkpoint.status()
                    + ", progress " + Math.round(checkpoint.captureProgress() * 100) + "%"), false);
        }
        return city.checkpointIds().size();
    }
}
