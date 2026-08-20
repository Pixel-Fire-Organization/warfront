package org.pixelfire.nationwars.state;

/**
 * Marker for an entry in the {@code nationwars:peace_clause} registry. A clause is one term of a
 * negotiated or imposed peace settlement (transferring a city, releasing an occupation, tribute, a
 * ceasefire, and so on). Registering new clause types here is the only change needed to add one —
 * the settlement pipeline that applies them stays generic.
 *
 * <p>No clause types are registered yet; this is the registry skeleton that later stages fill in.
 */
public interface PeaceClause
{
}
