package org.pixelfire.nationwars.state;

/**
 * One entry per founding precondition, in the order they are checked.
 */
public enum FoundingFailureReason
{
    NOT_IN_A_NATION("You must be in a nation to found a city."),
    RANK_TOO_LOW("Your rank in your nation is too low to found a city."),
    DIMENSION_INELIGIBLE("Cities cannot be founded in this dimension."),
    SKY_COLUMN_OBSTRUCTED("The sky above this position must be clear to the build limit."),
    SURFACE_REQUIREMENT_NOT_MET("This position is too far below the surface to found a city here."),
    TOO_CLOSE_TO_ANOTHER_CORE("Another city's core is too close to this position."),
    CITY_LIMIT_REACHED("Your nation cannot found any more cities right now."),
    FOUNDING_COOLDOWN_ACTIVE("Your nation must wait longer before founding another city."),
    CHUNK_ALREADY_CLAIMED("This chunk is already claimed by another nation."),
    NATION_LOCKED("Your nation is locked pending a peace settlement and cannot found cities."),
    NATION_AT_WAR("Your nation cannot found a city while in an unsettled war.");

    private final String message;

    FoundingFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
