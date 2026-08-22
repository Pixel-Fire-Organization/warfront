package org.pixelfire.nationwars.state;

public enum WarJoinFailureReason
{
    NOT_NATION_OWNER("Only your nation's leader may join a war."),
    ALREADY_IN_THIS_WAR("Your nation is already a belligerent in this war."),
    WAR_NOT_JOINABLE("This war is no longer accepting voluntary attacker-side joiners."),
    UNSETTLED_WAR_ALREADY_EXISTS("There is already an unsettled war between your nations."),
    COOLDOWN_ACTIVE("Your nation must wait longer before joining a war against this nation."),
    JOINER_LOCKED("Your nation is locked pending a peace settlement and cannot join a war."),
    JOINER_AT_WAR_CAP("Your nation is already in the maximum number of concurrent wars.");

    private final String message;

    WarJoinFailureReason(final String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}
