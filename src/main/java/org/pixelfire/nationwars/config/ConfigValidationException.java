package org.pixelfire.nationwars.config;

/**
 * Thrown when config values fail a hard validation rule that must refuse server startup rather than
 * silently running with a broken configuration.
 */
public class ConfigValidationException extends RuntimeException
{
    public ConfigValidationException(final String message)
    {
        super(message);
    }
}
