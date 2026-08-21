package org.pixelfire.nationwars.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.pixelfire.nationwars.network.OpenPeaceDealPacket;
import org.pixelfire.nationwars.network.SyncCityPacket;
import org.pixelfire.nationwars.network.SyncCoalitionPacket;
import org.pixelfire.nationwars.network.SyncCombatTagPacket;
import org.pixelfire.nationwars.network.SyncEvasionWarningPacket;
import org.pixelfire.nationwars.network.SyncReadinessPacket;
import org.pixelfire.nationwars.network.SyncSettlementPacket;
import org.pixelfire.nationwars.network.SyncWarScorePacket;
import org.pixelfire.nationwars.network.SyncWarStatePacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the HUD overlay and renderers read, kept up to date purely by whichever S2C packet last
 * arrived — never queried from server state directly, since the client has no such thing. Cleared on
 * disconnect via {@link #clear()} so a HUD doesn't show stale data from a previous session/server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNationCache
{
    private static final Map<UUID, SyncCityPacket> CITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncWarStatePacket> WARS = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncCoalitionPacket> ATTACKER_COALITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncCoalitionPacket> DEFENDER_COALITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> OWN_WAR_SCORE = new ConcurrentHashMap<>();
    private static volatile SyncReadinessPacket readiness;
    private static volatile SyncCombatTagPacket combatTag;
    private static volatile SyncEvasionWarningPacket lastEvasionWarning;
    private static volatile long evasionWarningShownAt;
    private static volatile OpenPeaceDealPacket openDeal;
    private static volatile SyncSettlementPacket settlementProgress;

    private ClientNationCache()
    {
    }

    public static void clear()
    {
        CITIES.clear();
        WARS.clear();
        ATTACKER_COALITIONS.clear();
        DEFENDER_COALITIONS.clear();
        OWN_WAR_SCORE.clear();
        readiness = null;
        combatTag = null;
        lastEvasionWarning = null;
        evasionWarningShownAt = 0L;
        openDeal = null;
        settlementProgress = null;
    }

    public static void putCity(final SyncCityPacket packet)
    {
        CITIES.put(packet.cityId(), packet);
    }

    public static Map<UUID, SyncCityPacket> cities()
    {
        return CITIES;
    }

    public static void putWar(final SyncWarStatePacket packet)
    {
        WARS.put(packet.data().getUUID("warId"), packet);
    }

    public static Map<UUID, SyncWarStatePacket> wars()
    {
        return WARS;
    }

    public static void putCoalition(final SyncCoalitionPacket packet)
    {
        final UUID warId = packet.data().getUUID("warId");
        (packet.data().getBoolean("isAttackerSide") ? ATTACKER_COALITIONS : DEFENDER_COALITIONS).put(warId, packet);
    }

    public static SyncCoalitionPacket attackerCoalition(final UUID warId)
    {
        return ATTACKER_COALITIONS.get(warId);
    }

    public static SyncCoalitionPacket defenderCoalition(final UUID warId)
    {
        return DEFENDER_COALITIONS.get(warId);
    }

    public static void putWarScore(final SyncWarScorePacket packet)
    {
        OWN_WAR_SCORE.put(packet.data().getUUID("warId"), packet.data().getLong("ownScore"));
    }

    public static long ownWarScore(final UUID warId)
    {
        return OWN_WAR_SCORE.getOrDefault(warId, 0L);
    }

    public static void setReadiness(final SyncReadinessPacket packet)
    {
        readiness = packet;
    }

    public static SyncReadinessPacket readiness()
    {
        return readiness;
    }

    public static void setCombatTag(final SyncCombatTagPacket packet)
    {
        combatTag = packet;
    }

    public static SyncCombatTagPacket combatTag()
    {
        return combatTag;
    }

    public static void setEvasionWarning(final SyncEvasionWarningPacket packet, final long shownAtMillis)
    {
        lastEvasionWarning = packet;
        evasionWarningShownAt = shownAtMillis;
    }

    public static SyncEvasionWarningPacket lastEvasionWarning()
    {
        return lastEvasionWarning;
    }

    public static long evasionWarningShownAt()
    {
        return evasionWarningShownAt;
    }

    public static void setOpenDeal(final OpenPeaceDealPacket packet)
    {
        openDeal = packet;
    }

    public static OpenPeaceDealPacket openDeal()
    {
        return openDeal;
    }

    public static void setSettlementProgress(final SyncSettlementPacket packet)
    {
        settlementProgress = packet;
    }

    public static SyncSettlementPacket settlementProgress()
    {
        return settlementProgress;
    }
}
