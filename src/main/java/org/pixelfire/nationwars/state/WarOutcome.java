package org.pixelfire.nationwars.state;

/**
 * {@code null} on a {@link War} still in progress; set the moment it reaches a terminal outcome.
 */
public enum WarOutcome
{
    ATTACKER_TOTAL_VICTORY,
    TIMEOUT,
    EVASION_SURRENDER,
    SURRENDER,
    ATTACKER_WITHDRAWAL,
    VOID,
    STAFF_CANCEL
}
