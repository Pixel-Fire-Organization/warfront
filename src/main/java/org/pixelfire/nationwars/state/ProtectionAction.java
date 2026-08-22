package org.pixelfire.nationwars.state;

/**
 * The action categories {@code warProtectionOverride} can list, each matched by its config string.
 */
public enum ProtectionAction
{
    BLOCK_BREAK("blockBreak"),
    BLOCK_PLACE("blockPlace"),
    PVP("pvp"),
    EXPLOSIONS("explosions"),
    FIRE_SPREAD("fireSpread"),
    CONTAINER_ACCESS("containerAccess"),
    ENTITY_DAMAGE("entityDamage");

    private final String configKey;

    ProtectionAction(final String configKey)
    {
        this.configKey = configKey;
    }

    public String configKey()
    {
        return configKey;
    }
}
