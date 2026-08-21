package org.pixelfire.nationwars.io.audit;

/**
 * The role an {@link AuditEntry}'s actor held when they performed the logged action, at the time
 * they performed it — not their current role, which may have changed since.
 */
public enum ActorRole
{
    LEADER,
    MODERATOR,
    MEMBER,
    STAFF,
    SYSTEM
}
