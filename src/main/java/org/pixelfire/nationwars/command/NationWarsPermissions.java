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
 * mod installed, Forge resolves these from vanilla operator level via each node's default resolver
 * ({@code staffPermissionLevel}); installing a permission mod (LuckPerms, FTB Ranks, ...) takes over
 * resolution automatically with no code change here.
 *
 * <p>Only the one node the {@code loglevel} command needs exists so far. The full set from the
 * eventual permission-node appendix — separate nodes for city/war/settlement/revert/config actions —
 * lands with the staff tooling stage; this is a minimal, working stub so the very first staff command
 * has something real to gate on rather than being unprotected.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsPermissions
{
    public static final PermissionNode<Boolean> STAFF_CONFIG = new PermissionNode<>(
            NationWarsMod.MODID, "staff.config", PermissionTypes.BOOLEAN,
            (player, playerId, context) -> player != null && player.hasPermissions(NationWarsConfig.STAFF_PERMISSION_LEVEL.get()));

    private NationWarsPermissions()
    {
    }

    @SubscribeEvent
    public static void gather(final PermissionGatherEvent.Nodes event)
    {
        event.addNodes(STAFF_CONFIG);
    }
}
