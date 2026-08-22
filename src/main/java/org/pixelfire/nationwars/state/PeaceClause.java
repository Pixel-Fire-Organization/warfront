package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * One entry in the {@code nationwars:peace_clause} registry: a clause <em>kind</em> (transfer a city,
 * release an occupation, tribute, ceasefire), stateless and shared across every settlement. A concrete
 * clause in one settlement is a {@link StagedClause} — this kind's id plus that use's own parameters —
 * so the apply pipeline only ever needs to resolve a clause's kind from the registry and call these two
 * methods; adding a new clause type never touches that pipeline.
 */
public interface PeaceClause
{
    /**
     * @param staffImposed true for a staff-finalized settlement, which ignores war-score affordability
     *                     limits — that is the point of an imposed peace — while still enforcing every
     *                     structural check (a city exists, is a belligerent's, is actually occupied, and
     *                     so on)
     * @return the validation failure message, or empty if {@code params} is valid against live state
     */
    Optional<String> validate(NationRegistry registry, War war, CompoundTag params, boolean staffImposed);

    /**
     * Applies this clause. Only ever called after {@link #validate} returned empty for every clause in
     * the same settlement, under the caller's global write lock. {@code staffImposed} carries the same
     * meaning as in {@link #validate}: a staff transfer doesn't spend the recipient's war score, since
     * there was no economy transaction to begin with, only a decision.
     */
    void apply(NationRegistry registry, MinecraftServer server, War war, CompoundTag params, boolean staffImposed);
}
