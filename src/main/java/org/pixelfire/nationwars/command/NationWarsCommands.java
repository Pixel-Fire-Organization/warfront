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
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.Level;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.NationWarsLogging;

/**
 * Player- and staff-facing commands. Only {@code /nationwars staff loglevel} exists so far — the rest
 * of the command tree (city, nation, war, and the broader staff surface) lands with the features they
 * operate on.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsCommands
{
    private NationWarsCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("loglevel")
                                .requires(NationWarsCommands::hasStaffConfigPermission)
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .then(Commands.argument("level", StringArgumentType.word())
                                                .executes(NationWarsCommands::setLogLevel))))));
    }

    private static boolean hasStaffConfigPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_CONFIG);
        }
        // The server console and command blocks have no OPAC/permission-mod identity to resolve
        // against; fall back to the same operator-level threshold the node's default resolver uses.
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static int setLogLevel(final CommandContext<CommandSourceStack> context)
    {
        final String category = StringArgumentType.getString(context, "category");
        final String levelName = StringArgumentType.getString(context, "level");

        final Level level = Level.toLevel(levelName, null);
        if (level == null)
        {
            context.getSource().sendFailure(Component.literal("Unknown log level '" + levelName
                    + "'. Expected one of OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL."));
            return 0;
        }

        if (!NationWarsLogging.setCategoryLevel(category, level))
        {
            context.getSource().sendFailure(Component.literal("Unknown log category '" + category + "'."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("nationwars log category '" + category + "' set to " + level), true);
        return 1;
    }
}
