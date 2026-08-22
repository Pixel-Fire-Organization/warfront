package org.pixelfire.nationwars.world;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.joml.Vector3f;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;

/**
 * Every rendering cue from the spec (core beam, checkpoint banner, progress ring, chain overlay) is
 * implemented as coloured dust particles rather than a custom {@code BlockEntityRenderer} with its own
 * geometry and texture — {@code DustParticleOptions} already supports an arbitrary RGB colour, so this
 * reaches the same "owner's colour" / "holder's colour" requirement through a vanilla mechanism that
 * needs no new assets and broadcasts to nearby players automatically via {@link
 * ServerLevel#sendParticles}. The shatter-and-reform cosmetic is the one exception, sent as its own
 * {@link org.pixelfire.nationwars.network.CheckpointEffectPacket} since it's a one-shot burst, not a
 * standing loop.
 */
public final class CityRenderEffectsListener
{
    private int tickCounter;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        if (++tickCounter < 10)
        {
            return;
        }
        tickCounter = 0;

        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();

        for (final City city : registry.cities().values())
        {
            renderCoreBeam(server, city);
        }
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            renderCheckpoint(server, checkpoint);
        }
    }

    private void renderCoreBeam(final MinecraftServer server, final City city)
    {
        final ServerLevel level = server.getLevel(city.dimension());
        if (level == null || !level.isLoaded(city.corePos()))
        {
            return;
        }
        sendBeamColumn(level, city.corePos().getX() + 0.5, city.corePos().getZ() + 0.5, city.corePos().getY() + 1,
                NationColor.of(city.ownerNationId()));
        if (city.occupiedByNationId() != null)
        {
            sendBeamColumn(level, city.corePos().getX() + 0.7, city.corePos().getZ() + 0.7, city.corePos().getY() + 1,
                    NationColor.of(city.occupiedByNationId()));
        }
    }

    private void sendBeamColumn(final ServerLevel level, final double x, final double z, final int baseY, final float[] color)
    {
        final DustParticleOptions options = new DustParticleOptions(new Vector3f(color[0], color[1], color[2]), 1.0f);
        for (int dy = 0; dy < 12; dy++)
        {
            level.sendParticles(options, x, baseY + dy, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void renderCheckpoint(final MinecraftServer server, final Checkpoint checkpoint)
    {
        final ServerLevel level = server.getLevel(checkpoint.dimension());
        if (level == null || !level.isLoaded(checkpoint.pos()))
        {
            return;
        }
        final double x = checkpoint.pos().getX() + 0.5;
        final double y = checkpoint.pos().getY() + 1.2;
        final double z = checkpoint.pos().getZ() + 0.5;

        final float[] holderColor = NationColor.of(checkpoint.holderNationId());
        level.sendParticles(new DustParticleOptions(new Vector3f(holderColor[0], holderColor[1], holderColor[2]), 1.3f),
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        if (checkpoint.capturingNationId() != null && checkpoint.captureProgress() > 0f)
        {
            renderProgressRing(level, x, y, z, checkpoint.captureProgress(), NationColor.of(checkpoint.capturingNationId()));
        }
        if (checkpoint.status() == CheckpointStatus.FROZEN || checkpoint.status() == CheckpointStatus.SEALED)
        {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private void renderProgressRing(final ServerLevel level, final double centerX, final double centerY, final double centerZ,
            final float progress, final float[] color)
    {
        final DustParticleOptions options = new DustParticleOptions(new Vector3f(color[0], color[1], color[2]), 0.8f);
        final int points = Math.max(1, Math.round(progress * 8));
        for (int i = 0; i < points; i++)
        {
            final double angle = 2 * Math.PI * i / 8.0;
            final double radius = 1.2;
            level.sendParticles(options, centerX + radius * Math.cos(angle), centerY, centerZ + radius * Math.sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
