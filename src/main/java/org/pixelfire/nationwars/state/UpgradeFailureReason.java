package org.pixelfire.nationwars.state;

/**
 * One entry per tier upgrade precondition. {@code MAX_TIER_REACHED} isn't itself numbered in the
 * precondition list, but has to be checked before any of the others make sense: there is no "cost of
 * the next tier" once there is no next tier.
 */
public enum UpgradeFailureReason
{
    MAX_TIER_REACHED("This city is already at the highest configured tier."),
    CITY_NOT_ACTIVE("The city must be ACTIVE to upgrade."),
    INSUFFICIENT_BANKED_PAYMENT("This city hasn't banked enough payment for the next tier yet."),
    CHECKPOINTS_BELOW_TIER_MAXIMUM("This city must hold its current tier's maximum checkpoints before upgrading."),
    EXPANDED_RADIUS_TOO_CLOSE_TO_ANOTHER_CITY("The expanded radius would come too close to another city's checkpoints."),
    NATION_LOCKED("Your nation is locked pending a peace settlement and cannot upgrade cities."),
    NATION_AT_WAR("Your nation cannot upgrade a city while in an unsettled war.");

    private final String message;

    UpgradeFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
