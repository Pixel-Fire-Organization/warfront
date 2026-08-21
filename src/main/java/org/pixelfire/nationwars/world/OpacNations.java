package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thin wrapper over the OPAC server API for the lookups founding/checkpoint placement need: a player's
 * nation and rank in it, whether a chunk is already claimed by someone outside that nation, and claiming
 * chunks for a nation. Every method here touches the OPAC API and must only ever be called from the main
 * thread.
 */
public final class OpacNations
{
    private OpacNations()
    {
    }

    /**
     * @param nationId    the OPAC party id
     * @param nationName  the party's default name, used as a starting point for a founded city's name
     * @param leaderUuid  the OPAC party owner's player UUID; city/checkpoint claims are registered under
     *                    this UUID, not the acting player's, so citizenship follows OPAC's own party
     *                    sharing regardless of who placed the block
     * @param rankOrdinal {@link PartyMemberRank#ordinal()} of the player in this party; the owner is
     *                    reported at the highest rank regardless of their nominal rank field, since
     *                    ownership always outranks any configurable requirement
     * @param isOwner     whether the player is this nation's leader (OPAC party owner)
     * @param memberCount total members of the party, including the owner
     */
    public record NationSnapshot(UUID nationId, String nationName, UUID leaderUuid, int rankOrdinal, boolean isOwner, int memberCount)
    {
    }

    /**
     * The given player's nation, or {@code null} if they are not in one.
     */
    public static NationSnapshot nationOf(final MinecraftServer server, final ServerPlayer player)
    {
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(player.getUUID());
        if (party == null)
        {
            return null;
        }
        final IPartyMemberAPI member = party.getMemberInfo(player.getUUID());
        final int rankOrdinal = member.isOwner() ? PartyMemberRank.values().length - 1 : member.getRank().ordinal();
        return new NationSnapshot(party.getId(), party.getDefaultName(), party.getOwner().getUUID(),
                rankOrdinal, member.isOwner(), party.getMemberCount());
    }

    /**
     * True if {@code nationId} and {@code otherNationId} are mutual OPAC allies — checked in both
     * directions since an alliance is defined as mutual, and OPAC's own {@code isAlly} only reflects
     * one party's ally list.
     */
    public static boolean areAllies(final MinecraftServer server, final UUID nationId, final UUID otherNationId)
    {
        final var partyManager = OpenPACServerAPI.get(server).getPartyManager();
        final var party = partyManager.getPartyById(nationId);
        final var otherParty = partyManager.getPartyById(otherNationId);
        return party != null && otherParty != null && party.isAlly(otherNationId) && otherParty.isAlly(nationId);
    }

    /**
     * The OPAC party owner's player UUID for {@code nationId}, or {@code null} if the nation no longer exists.
     */
    public static UUID leaderUuidOf(final MinecraftServer server, final UUID nationId)
    {
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId);
        return party == null ? null : party.getOwner().getUUID();
    }

    public static boolean nationExists(final MinecraftServer server, final UUID nationId)
    {
        return OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId) != null;
    }

    /**
     * Every nation mutually allied with {@code nationId}. Used to cascade coalition assembly at
     * declaration time.
     */
    public static Set<UUID> mutualAlliesOf(final MinecraftServer server, final UUID nationId)
    {
        final var partyManager = OpenPACServerAPI.get(server).getPartyManager();
        final var party = partyManager.getPartyById(nationId);
        if (party == null)
        {
            return Set.of();
        }
        return party.getAllyPartiesStream()
                .map(ally -> ally.getPartyId())
                .filter(allyId -> areAllies(server, nationId, allyId))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Finds a nation by its party default name, case-insensitively. Players know their nation by name,
     * not by OPAC party UUID.
     */
    public static UUID findNationByName(final MinecraftServer server, final String name)
    {
        return OpenPACServerAPI.get(server).getPartyManager().getAllStream()
                .filter(party -> party.getDefaultName().equalsIgnoreCase(name))
                .map(party -> party.getId())
                .findFirst().orElse(null);
    }

    /**
     * True if the chunk at {@code pos} is claimed by a player who isn't a member of {@code nationId} —
     * including a claim whose owner no longer belongs to any party, treated conservatively as someone
     * else's.
     */
    public static boolean isChunkClaimedByOtherNation(final MinecraftServer server, final ResourceLocation dimension,
            final BlockPos pos, final UUID nationId)
    {
        return isClaimedByOtherNation(OpenPACServerAPI.get(server), dimension, pos, nationId);
    }

    /**
     * True if any chunk in {@code chunks} is claimed by a player who isn't a member of {@code nationId}.
     */
    public static boolean isAnyChunkClaimedByOtherNation(final MinecraftServer server, final ResourceLocation dimension,
            final Set<ChunkPos> chunks, final UUID nationId)
    {
        final var api = OpenPACServerAPI.get(server);
        for (final ChunkPos chunk : chunks)
        {
            final BlockPos chunkOrigin = new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
            if (isClaimedByOtherNation(api, dimension, chunkOrigin, nationId))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isClaimedByOtherNation(final OpenPACServerAPI api, final ResourceLocation dimension,
            final BlockPos pos, final UUID nationId)
    {
        final IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(dimension, pos);
        if (claim == null)
        {
            return false;
        }
        final var owningParty = api.getPartyManager().getPartyByMember(claim.getPlayerId());
        return owningParty == null || !owningParty.getId().equals(nationId);
    }

    /**
     * Claims every chunk in {@code chunks} under {@code leaderUuid}, bypassing per-player claim limits —
     * city/checkpoint territory doesn't consume a player's personal claim budget.
     */
    public static void claimChunks(final MinecraftServer server, final ResourceLocation dimension,
            final UUID leaderUuid, final Set<ChunkPos> chunks)
    {
        final var claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        for (final ChunkPos chunk : chunks)
        {
            claimsManager.claim(dimension, leaderUuid, 0, chunk.x, chunk.z, false);
        }
    }

    /**
     * Releases every chunk in {@code chunks}, regardless of who currently holds it.
     */
    public static void unclaimChunks(final MinecraftServer server, final ResourceLocation dimension, final Set<ChunkPos> chunks)
    {
        final var claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        for (final ChunkPos chunk : chunks)
        {
            claimsManager.unclaim(dimension, chunk.x, chunk.z);
        }
    }
}
