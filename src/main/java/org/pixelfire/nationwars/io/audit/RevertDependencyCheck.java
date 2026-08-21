package org.pixelfire.nationwars.io.audit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure check for the audit log's dependency-graph rule: reverting an entry is refused if a later entry sharing
 * one of its targets still depends on it having happened, and the command must name exactly which
 * entries block it. "Still depends" excludes: any revert entry (a compensating action restores prior
 * state rather than building new state on top, so it never itself creates a dependency), and an
 * ordinary later entry that has itself since been undone (found by scanning the same candidate list for
 * a revert naming it) — an already-reverted blocker no longer represents live state, so it shouldn't
 * block reverting something earlier either.
 */
public final class RevertDependencyCheck
{
    private RevertDependencyCheck()
    {
    }

    /**
     * @param sharingTargets every entry (including {@code entry} itself) that references at least one
     *                       of {@code entry}'s target ids, in any order
     * @return the entryIds of every later entry that blocks reverting {@code entry}, empty if none do
     */
    public static List<String> blockingEntries(final AuditIndexEntry entry, final List<AuditIndexEntry> sharingTargets)
    {
        final Set<String> undoneEntryIds = new HashSet<>();
        for (final AuditIndexEntry candidate : sharingTargets)
        {
            if (candidate.revertOf() != null)
            {
                undoneEntryIds.add(candidate.revertOf());
            }
        }

        final List<String> blocking = new ArrayList<>();
        for (final AuditIndexEntry candidate : sharingTargets)
        {
            if (candidate.entryId().equals(entry.entryId()))
            {
                continue;
            }
            if (candidate.timestamp() <= entry.timestamp())
            {
                continue;
            }
            if (candidate.revertOf() != null)
            {
                continue;
            }
            if (undoneEntryIds.contains(candidate.entryId()))
            {
                continue;
            }
            blocking.add(candidate.entryId());
        }
        return blocking;
    }
}
