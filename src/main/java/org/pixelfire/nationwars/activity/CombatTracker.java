package org.pixelfire.nationwars.activity;

import java.util.UUID;

public record CombatTracker(UUID playerId, long combatTagExpiresTick, UUID lastAttackerId)
{
}
