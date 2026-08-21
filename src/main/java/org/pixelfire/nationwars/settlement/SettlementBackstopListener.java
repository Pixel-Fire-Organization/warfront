package org.pixelfire.nationwars.settlement;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.ArrayList;

/**
 * The {@code settlementWindow} backstop: if nobody settles in time, the default outcome —
 * every occupied city transfers to its occupier — applies automatically and the lock lifts, announced as
 * an imposed truce. {@code settlementDeadline == 0} means the lock is indefinite (either
 * {@code settlementWindow} is configured as 0, or the war reached settlement as a white peace and was
 * never locked to begin with), so those wars are skipped here entirely.
 */
public final class SettlementBackstopListener
{
    private int tickCounter;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        if (++tickCounter < 200)
        {
            return;
        }
        tickCounter = 0;

        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();

        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            if (war.phase() == WarPhase.SETTLEMENT && war.settlementDeadline() > 0 && now >= war.settlementDeadline())
            {
                SettlementApplier.apply(server, registry, war, DefaultSettlement.applyOccupationsClauses(registry, war),
                        war.outcome(), true);
                registry.settlements().remove(war.warId());
            }
        }
    }
}
