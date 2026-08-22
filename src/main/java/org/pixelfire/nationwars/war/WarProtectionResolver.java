package org.pixelfire.nationwars.war;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.ProtectionAction;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarProtectionContext;
import org.pixelfire.nationwars.state.WarProtectionOverride;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.world.ClaimSetComputation;
import org.pixelfire.nationwars.world.ClaimShape;

import java.util.UUID;

/**
 * Resolves {@link WarProtectionContext} from live registry state, then hands it to the pure
 * {@link WarProtectionOverride} checker. One chunk can only ever belong to one city's claim union in
 * practice (claims are exclusive), so the first matching targeted city found is authoritative.
 */
public final class WarProtectionResolver
{
    private WarProtectionResolver()
    {
    }

    public static boolean isOverridden(final ResourceKey<Level> dimension, final BlockPos pos, final UUID actorNationId,
            final ProtectionAction action)
    {
        if (actorNationId == null || !NationWarsConfig.WAR_PROTECTION_OVERRIDE.get().contains(action.configKey()))
        {
            return false;
        }

        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final ChunkPos chunk = new ChunkPos(pos);

        for (final War war : registry.wars().values())
        {
            if (war.phase() != WarPhase.ACTIVE)
            {
                continue;
            }
            for (final UUID cityId : war.targetCityIds())
            {
                final City city = registry.cities().get(cityId);
                if (city == null || !city.dimension().equals(dimension) || !cityClaimsChunk(registry, city, chunk))
                {
                    continue;
                }
                final boolean opposing = isOpposing(war, actorNationId, city.ownerNationId());
                final WarProtectionContext context = new WarProtectionContext(true, opposing, true, true);
                if (WarProtectionOverride.isAllowed(context))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOpposing(final War war, final UUID actorNationId, final UUID cityOwnerNationId)
    {
        final boolean actorAttacks = war.attackers().members().contains(actorNationId);
        final boolean actorDefends = war.defenders().members().contains(actorNationId);
        final boolean ownerAttacks = war.attackers().members().contains(cityOwnerNationId);
        final boolean ownerDefends = war.defenders().members().contains(cityOwnerNationId);
        return (actorAttacks && ownerDefends) || (actorDefends && ownerAttacks);
    }

    private static boolean cityClaimsChunk(final NationRegistry registry, final City city, final ChunkPos chunk)
    {
        final var coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        if (ClaimSetComputation.chunksFor(coreShape, new ChunkPos(city.corePos())).contains(chunk))
        {
            return true;
        }
        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint != null && checkpoint.claimedChunks().contains(chunk))
            {
                return true;
            }
        }
        return false;
    }
}
