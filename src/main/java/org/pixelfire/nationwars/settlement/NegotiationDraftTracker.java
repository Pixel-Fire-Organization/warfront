package org.pixelfire.nationwars.settlement;

import org.pixelfire.nationwars.state.StagedClause;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A nation's in-progress peace offer for one war, built incrementally by {@code /war negotiate offer|
 * demand|ceasefire} before {@code /war negotiate send} turns it into a real {@link
 * org.pixelfire.nationwars.state.PeaceSettlement} proposal. Purely transient scratch space — not part of
 * any persisted state, since an unsent draft has no meaning to anyone but the nation building it.
 */
public final class NegotiationDraftTracker
{
    private record DraftKey(UUID warId, UUID nationId)
    {
    }

    private final Map<DraftKey, List<StagedClause>> drafts = new ConcurrentHashMap<>();

    public void addClause(final UUID warId, final UUID nationId, final StagedClause clause)
    {
        drafts.computeIfAbsent(new DraftKey(warId, nationId), key -> new ArrayList<>()).add(clause);
    }

    public List<StagedClause> get(final UUID warId, final UUID nationId)
    {
        return List.copyOf(drafts.getOrDefault(new DraftKey(warId, nationId), List.of()));
    }

    public void clear(final UUID warId, final UUID nationId)
    {
        drafts.remove(new DraftKey(warId, nationId));
    }
}
