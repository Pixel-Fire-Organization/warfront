package org.pixelfire.nationwars.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.pixelfire.nationwars.NationWarsMod;

/**
 * {@code /afk}: marks the calling player AFK immediately. There is no matching command to become Ready
 * again — only real activity clears it.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsActivityCommands
{
    private NationWarsActivityCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("afk").executes(NationWarsActivityCommands::markAfk));
    }

    private static int markAfk(final CommandContext<CommandSourceStack> context)
    {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null)
        {
            context.getSource().sendFailure(Component.literal("Only a player can go AFK."));
            return 0;
        }
        NationWarsMod.get().getActivityTracker().markManualAfk(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("You are now marked AFK."), false);
        return 1;
    }
}
