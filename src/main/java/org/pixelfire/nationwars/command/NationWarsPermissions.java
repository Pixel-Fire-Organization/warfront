package org.pixelfire.nationwars.command;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;

/**
 * Staff permission nodes, registered with Forge's built-in {@code PermissionAPI}. With no permission
 * mod installed, each node's default resolver falls back to vanilla operator level
 * ({@code staffPermissionLevel}); a permission mod (LuckPerms, FTB Ranks, ...) takes over resolution
 * automatically with no code change here. Only the nodes existing commands need are registered so far;
 * the rest of the eventual permission-node set lands with the staff tooling stage.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsPermissions
{
    public static final PermissionNode<Boolean> STAFF_CONFIG = new PermissionNode<>(
            NationWarsMod.MODID, "staff.config", PermissionTypes.BOOLEAN,
            (player, playerId, context) -> player != null && player.hasPermissions(NationWarsConfig.STAFF_PERMISSION_LEVEL.get()));

    public static final PermissionNode<Boolean> STAFF_INSPECT = new PermissionNode<>(
            NationWarsMod.MODID, "staff.inspect", PermissionTypes.BOOLEAN,
            (player, playerId, context) -> player != null && player.hasPermissions(NationWarsConfig.STAFF_PERMISSION_LEVEL.get()));

    public static final PermissionNode<Boolean> STAFF_SETTLE = new PermissionNode<>(
            NationWarsMod.MODID, "staff.settle", PermissionTypes.BOOLEAN,
            (player, playerId, context) -> player != null && player.hasPermissions(NationWarsConfig.STAFF_PERMISSION_LEVEL.get()));

    private NationWarsPermissions()
    {
    }

    @SubscribeEvent
    public static void gather(final PermissionGatherEvent.Nodes event)
    {
        event.addNodes(STAFF_CONFIG, STAFF_INSPECT, STAFF_SETTLE);
    }
}
