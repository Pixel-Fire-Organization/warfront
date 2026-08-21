package org.pixelfire.nationwars.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.CounterOffensiveFailureReason;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarDeclarationFailureReason;
import org.pixelfire.nationwars.state.WarJoinFailureReason;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.war.CounterOffensiveService;
import org.pixelfire.nationwars.war.WarDeclarationService;
import org.pixelfire.nationwars.war.WarJoinService;
import org.pixelfire.nationwars.war.WarTermination;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /war declare|withdraw|join|counteroffensive|status|list} and the staff cancel command.
 * Surrender isn't implemented here — it requires the settlement clause pipeline (Stage 18).
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsWarCommands
{
    private NationWarsWarCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("war")
                .then(Commands.literal("declare")
                        .then(Commands.argument("nation", StringArgumentType.greedyString())
                                .executes(NationWarsWarCommands::declare)))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("warId", UuidArgument.uuid())
                                .executes(NationWarsWarCommands::withdraw)))
                .then(Commands.literal("join")
                        .then(Commands.argument("warId", UuidArgument.uuid())
                                .then(Commands.literal("attackers")
                                        .executes(NationWarsWarCommands::join))))
                .then(Commands.literal("counteroffensive")
                        .then(Commands.argument("warId", UuidArgument.uuid())
                                .executes(NationWarsWarCommands::counterOffensive)))
                .then(Commands.literal("status")
                        .executes(NationWarsWarCommands::statusList)
                        .then(Commands.argument("warId", UuidArgument.uuid())
                                .executes(NationWarsWarCommands::status)))
                .then(Commands.literal("list").executes(NationWarsWarCommands::statusList)));

        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("war")
                                .then(Commands.literal("cancel")
                                        .requires(NationWarsWarCommands::hasStaffConfigPermission)
                                        .then(Commands.argument("warId", UuidArgument.uuid())
                                                .executes(NationWarsWarCommands::staffCancel))))));
    }

    private static boolean hasStaffConfigPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_CONFIG);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static int declare(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null)
        {
            context.getSource().sendFailure(Component.literal("Only a player may declare war."));
            return 0;
        }
        final String targetName = StringArgumentType.getString(context, "nation");
        final UUID targetId = OpacNations.findNationByName(context.getSource().getServer(), targetName);

        final Optional<WarDeclarationFailureReason> failure =
                WarDeclarationService.declare(context.getSource().getServer(), player, targetId);
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get().message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("War declared on " + targetName + "."), true);
        return 1;
    }

    private static int withdraw(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final War war = registry.wars().get(warId);
        if (player == null || war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return 0;
        }
        final NationSnapshot nation = OpacNations.nationOf(context.getSource().getServer(), player);
        if (nation == null || !nation.isOwner() || !war.attackers().primaryNationId().equals(nation.nationId()))
        {
            context.getSource().sendFailure(Component.literal("Only the attacking nation's leader may withdraw from this war."));
            return 0;
        }
        WarTermination.conclude(registry, war, WarOutcome.ATTACKER_WITHDRAWAL, System.currentTimeMillis());
        context.getSource().sendSuccess(() -> Component.literal("Withdrew from the war."), true);
        return 1;
    }

    private static int join(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (player == null || war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return 0;
        }
        final Optional<WarJoinFailureReason> failure = WarJoinService.join(context.getSource().getServer(), player, war);
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get().message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Joined the war as an attacker."), true);
        return 1;
    }

    private static int counterOffensive(final CommandContext<CommandSourceStack> context)
    {
        if (!NationWarsConfig.ALLOW_COUNTER_OFFENSIVE.get())
        {
            context.getSource().sendFailure(Component.literal("Counteroffensives are disabled on this server."));
            return 0;
        }
        final ServerPlayer player = context.getSource().getPlayer();
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (player == null || war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return 0;
        }
        final NationSnapshot nation = OpacNations.nationOf(context.getSource().getServer(), player);
        if (nation == null || !nation.isOwner() || !war.defenders().primaryNationId().equals(nation.nationId()))
        {
            context.getSource().sendFailure(Component.literal("Only the defending nation's leader may declare a counteroffensive."));
            return 0;
        }
        final Optional<CounterOffensiveFailureReason> failure = CounterOffensiveService.declare(context.getSource().getServer(), war);
        if (failure.isPresent())
        {
            context.getSource().sendFailure(Component.literal(failure.get().message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Counteroffensive declared. The war is now two-front."), true);
        return 1;
    }

    private static int staffCancel(final CommandContext<CommandSourceStack> context)
    {
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final War war = registry.wars().get(warId);
        if (war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return 0;
        }
        WarTermination.conclude(registry, war, WarOutcome.STAFF_CANCEL, System.currentTimeMillis());
        context.getSource().sendSuccess(() -> Component.literal("War " + warId + " cancelled by staff."), true);
        return 1;
    }

    private static int status(final CommandContext<CommandSourceStack> context)
    {
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (war == null)
        {
            context.getSource().sendFailure(Component.literal("No such war."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(describe(war)), false);
        return 1;
    }

    private static int statusList(final CommandContext<CommandSourceStack> context)
    {
        final var wars = NationWarsMod.get().getNationRegistry().wars().values();
        if (wars.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.literal("No wars recorded."), false);
            return 1;
        }
        for (final War war : wars)
        {
            context.getSource().sendSuccess(() -> Component.literal(describe(war)), false);
        }
        return wars.size();
    }

    private static String describe(final War war)
    {
        return war.warId() + ": " + war.attackers().primaryNationId() + " vs " + war.defenders().primaryNationId()
                + " [" + war.phase() + (war.outcome() != null ? ", " + war.outcome() : "") + "]";
    }
}
