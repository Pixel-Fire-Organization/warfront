package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * An ally scheduled to join a {@link Coalition} once one of its members logs in and clears the login
 * shield. Not populated until alliance cascading exists.
 */
public record PendingEntry(UUID nationId, long scheduledAt, String reason)
{
}
