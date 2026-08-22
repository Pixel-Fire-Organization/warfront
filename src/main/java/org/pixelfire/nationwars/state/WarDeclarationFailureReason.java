package org.pixelfire.nationwars.state;

/**
 * One entry per declaration check. Checks 3 and 9-10 are each split into their component parts for a
 * clearer rejection message, same as {@link FoundingFailureReason} does for founding.
 */
public enum WarDeclarationFailureReason
{
    NOT_NATION_OWNER("Only your nation's leader may declare war."),
    DECLARER_HAS_NO_CITY("Your nation must have at least one city to declare war."),
    TARGET_NOT_FOUND("That nation does not exist."),
    TARGET_IS_SELF("You cannot declare war on your own nation."),
    TARGET_IS_MUTUAL_ALLY("You cannot declare war on a mutual ally."),
    TARGET_HAS_NO_ELIGIBLE_CITY("That nation has no city past its founding grace to target."),
    TARGET_NOT_WAR_READY("That nation is not currently war-ready."),
    DECLARER_NOT_WAR_READY("Your nation is not currently war-ready."),
    UNSETTLED_WAR_ALREADY_EXISTS("There is already an unsettled war between your nations."),
    COOLDOWN_ACTIVE("Your nation must wait longer before declaring on this nation again."),
    DECLARER_LOCKED("Your nation is locked pending a peace settlement and cannot declare war."),
    TARGET_LOCKED("That nation is locked pending a peace settlement and cannot be declared on."),
    DECLARER_AT_WAR_CAP("Your nation is already in the maximum number of concurrent wars."),
    TARGET_AT_WAR_CAP("That nation is already in the maximum number of concurrent wars."),
    OUTSIDE_WAR_WINDOW("War declarations are only allowed during the configured time window.");

    private final String message;

    WarDeclarationFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
