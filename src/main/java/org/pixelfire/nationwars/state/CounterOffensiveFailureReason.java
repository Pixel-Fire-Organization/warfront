package org.pixelfire.nationwars.state;

public enum CounterOffensiveFailureReason
{
    WAR_NOT_ACTIVE("The war must be ACTIVE to counteroffensive."),
    DEFENDER_STILL_OCCUPIED("Your coalition must have zero occupied cities before counteroffensiving."),
    INSUFFICIENT_WAR_SCORE("Your coalition's war score is not high enough yet to counteroffensive."),
    WAR_NOT_ACTIVE_LONG_ENOUGH("The war hasn't been ACTIVE long enough to counteroffensive."),
    DEFENDER_NOT_WAR_READY("Your coalition must be war-ready to counteroffensive."),
    ALREADY_COUNTER_OFFENSIVE("This war has already turned into a counteroffensive.");

    private final String message;

    CounterOffensiveFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
