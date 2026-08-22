package org.pixelfire.nationwars.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.world.SkyColumnScanner;

/**
 * {@code /nationwars staff sky <pos>}: reports whether the sky column above a position is clear.
 * Useful on its own for diagnosing a rejected placement once founding/placement exist; for now it's
 * also how the sky column scan gets exercised against a real world before anything else depends on it.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsWorldCommands
{
    private NationWarsWorldCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("sky")
                                .requires(NationWarsWorldCommands::hasStaffInspectPermission)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(NationWarsWorldCommands::checkSky)))));
    }

    private static boolean hasStaffInspectPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_INSPECT);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static int checkSky(final CommandContext<CommandSourceStack> context)
    {
        final BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        final ServerLevel level = context.getSource().getLevel();
        final boolean clear = SkyColumnScanner.isColumnClear(level, pos);

        context.getSource().sendSuccess(() -> Component.literal(
                "sky column at " + pos.toShortString() + (clear ? " is CLEAR" : " is OBSTRUCTED")), false);
        return clear ? 1 : 0;
    }
}
