package org.pixelfire.nationwars.io.audit;

import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.state.NationRegistry;

import java.util.Optional;

/**
 * The inverse of one {@code actionType}, looked up by {@link AuditReverters} for {@code /nationwars
 * staff revert}. Only registered for action types whose {@code before}/{@code after} snapshots actually
 * carry enough state to reconstruct the prior state — every other {@code reversible = true} entry
 * without a registered reverter still fails the command cleanly rather than attempting a guess.
 */
public interface Reverter
{
    /**
     * @return the failure reason if the revert could not be fully applied (live state has moved on in
     *         a way this reverter cannot repair), or empty on success. A partial revert is reported by
     *         still returning empty but the reverter should log/message what could not be restored,
     *         per the spec's "best-effort partial revert and an explicit list" allowance.
     */
    Optional<String> revert(NationRegistry registry, MinecraftServer server, AuditEntry entry);
}
