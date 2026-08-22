package org.pixelfire.nationwars.io.audit;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain Java-side dispatch from {@code actionType} to its {@link Reverter}, populated once at startup
 * by each domain package's own bootstrap (mirroring {@code NationWarsPeaceClauses}) rather than a Forge
 * registry — nothing outside this mod ever needs to add a reverter, so the extra machinery buys nothing.
 */
public final class AuditReverters
{
    private static final Map<ResourceLocation, Reverter> REVERTERS = new ConcurrentHashMap<>();

    private AuditReverters()
    {
    }

    public static void register(final ResourceLocation actionType, final Reverter reverter)
    {
        REVERTERS.put(actionType, reverter);
    }

    public static Reverter get(final ResourceLocation actionType)
    {
        return REVERTERS.get(actionType);
    }
}
