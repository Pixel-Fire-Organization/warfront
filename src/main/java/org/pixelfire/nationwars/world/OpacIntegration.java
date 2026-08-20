package org.pixelfire.nationwars.world;

/**
 * Fail-fast check that Open Parties and Claims is actually resolvable on the classpath. This mod has
 * no purpose without OPAC — nations are OPAC parties — so a missing or incompatible OPAC build should
 * stop startup with a message that says so, rather than surfacing as a confusing crash the first time
 * some unrelated feature tries to look up a party.
 *
 * <p>The dependency is also declared as mandatory in {@code mods.toml}, so this check is normally
 * redundant with Forge's own dependency resolution; it exists as a second line of defense and to give
 * a clearer error if that resolution is ever bypassed (e.g. a version range that technically matches
 * but ships a renamed or incompatible API).
 */
public final class OpacIntegration
{
    public static final String OPAC_MOD_ID = "openpartiesandclaims";

    private static final String OPAC_API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";

    private OpacIntegration()
    {
    }

    /**
     * Confirms the OPAC server API class can actually be loaded. Uses a plain string rather than a
     * compile-time import so this class itself always loads cleanly, even if OPAC is missing — the
     * failure is reported through this method's own exception instead of a class-verification error
     * at an unpredictable point during mod loading.
     *
     * @throws IllegalStateException if the OPAC API cannot be resolved
     */
    public static void verifyAvailable()
    {
        try
        {
            Class.forName(OPAC_API_CLASS, false, OpacIntegration.class.getClassLoader());
        }
        catch (final ClassNotFoundException e)
        {
            throw new IllegalStateException(
                    "Open Parties and Claims (modid '" + OPAC_MOD_ID + "') is required but its API ("
                            + OPAC_API_CLASS + ") could not be found on the classpath. "
                            + "Install a compatible OPAC build for this Minecraft/Forge version and restart the server.", e);
        }
    }
}
