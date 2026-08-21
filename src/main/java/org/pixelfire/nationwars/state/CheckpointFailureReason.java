package org.pixelfire.nationwars.state;

/**
 * One entry per checkpoint placement precondition. Precondition 5 ("sky column clear;
 * surface requirement met") is split into two reasons so a rejection is specific, same as
 * {@link FoundingFailureReason} does for the equivalent founding precondition.
 */
public enum CheckpointFailureReason
{
    NOT_WITHIN_A_CITYS_RADIUS("This position isn't within exactly one city's tier radius."),
    NOT_A_CITIZEN_OR_ALLY("You must be a citizen of that city's nation (or an ally, if allowed) to place its checkpoints."),
    RANK_TOO_LOW("Your rank in your nation is too low to place a checkpoint."),
    CITY_NOT_ACTIVE("That city must be ACTIVE to place a checkpoint."),
    SKY_COLUMN_OBSTRUCTED("The sky above this position must be clear to the build limit."),
    SURFACE_REQUIREMENT_NOT_MET("This position is too far below the surface to place a checkpoint here."),
    CHECKPOINT_LIMIT_REACHED("That city already has the maximum checkpoints allowed at its tier."),
    TOO_CLOSE_TO_ANOTHER_CHECKPOINT_OR_CORE("This position is too close to another checkpoint or to the city's core."),
    CHUNK_ALREADY_CLAIMED("One of the chunks this checkpoint would claim already belongs to another nation.");

    private final String message;

    CheckpointFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
