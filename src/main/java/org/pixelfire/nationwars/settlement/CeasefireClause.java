package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.state.War;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code Ceasefire(durationHours)}: writes {@code warCooldowns} both ways. Doesn't touch
 * {@code activeWarIds}/{@code lockedByWarId} — releasing those is {@link SettlementApplier}'s job once
 * every clause in the settlement has applied, not this one clause's.
 */
public final class CeasefireClause implements PeaceClause
{
    public static final ResourceLocation ID = ResourceLocation.tryBuild("nationwars", "ceasefire");

    @Override
    public Optional<String> validate(final NationRegistry registry, final War war, final CompoundTag params, final boolean staffImposed)
    {
        return params.getLong("durationHours") > 0 ? Optional.empty() : Optional.of("ceasefire duration must be positive");
    }

    @Override
    public void apply(final NationRegistry registry, final MinecraftServer server, final War war, final CompoundTag params,
            final boolean staffImposed)
    {
        final long expiresAt = System.currentTimeMillis() + params.getLong("durationHours") * 3_600_000L;
        for (final UUID attackerId : war.attackers().members())
        {
            writeCooldown(registry, attackerId, war.defenders().primaryNationId(), expiresAt);
        }
        for (final UUID defenderId : war.defenders().members())
        {
            writeCooldown(registry, defenderId, war.attackers().primaryNationId(), expiresAt);
        }
    }

    private static void writeCooldown(final NationRegistry registry, final UUID nationId, final UUID opponentPrimaryId,
            final long expiresAt)
    {
        final NationState current = registry.nationStates().getOrDefault(nationId, NationState.empty(nationId));
        final Map<UUID, Long> warCooldowns = new HashMap<>(current.warCooldowns());
        warCooldowns.put(opponentPrimaryId, expiresAt);
        registry.nationStates().put(nationId, new NationState(nationId, current.cityIds(), current.capitalCityId(),
                current.activeWarIds(), Map.copyOf(warCooldowns), current.lastCityFoundedAt(), current.lockedByWarId()));
    }
}
