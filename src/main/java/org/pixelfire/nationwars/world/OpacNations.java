package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.UUID;

/**
 * Thin wrapper over the OPAC server API for the lookups founding needs: a player's nation and rank in
 * it, and whether a chunk is already claimed by someone outside that nation. Every method here touches
 * the OPAC API and must only ever be called from the main thread (spec §3.1).
 */
public final class OpacNations
{
    private OpacNations()
    {
    }

    /**
     * @param nationId    the OPAC party id
     * @param nationName  the party's default name, used as a starting point for a founded city's name
     * @param rankOrdinal {@link PartyMemberRank#ordinal()} of the player in this party; the owner is
     *                    reported at the highest rank regardless of their nominal rank field, since
     *                    ownership always outranks any configurable requirement
     * @param isOwner     whether the player is this nation's leader (OPAC party owner)
     * @param memberCount total members of the party, including the owner
     */
    public record NationSnapshot(UUID nationId, String nationName, int rankOrdinal, boolean isOwner, int memberCount)
    {
    }

    /**
     * The founding player's nation, or {@code null} if they are not in one.
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
        return new NationSnapshot(party.getId(), party.getDefaultName(), rankOrdinal, member.isOwner(), party.getMemberCount());
    }

    /**
     * True if the chunk at {@code pos} is claimed by a player who isn't a member of {@code nationId} —
     * including a claim whose owner no longer belongs to any party, treated conservatively as someone
     * else's.
     */
    public static boolean isChunkClaimedByOtherNation(final MinecraftServer server, final ResourceLocation dimension,
            final BlockPos pos, final UUID nationId)
    {
        final var api = OpenPACServerAPI.get(server);
        final IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(dimension, pos);
        if (claim == null)
        {
            return false;
        }
        final var owningParty = api.getPartyManager().getPartyByMember(claim.getPlayerId());
        return owningParty == null || !owningParty.getId().equals(nationId);
    }

    /**
     * Claims the chunk at {@code pos} on behalf of {@code nationId}, attributed to {@code claimingPlayer}
     * as OPAC requires a player UUID as the claim owner.
     */
    public static void claimChunk(final MinecraftServer server, final ResourceLocation dimension,
            final ServerPlayer claimingPlayer, final BlockPos pos)
    {
        final int chunkX = pos.getX() >> 4;
        final int chunkZ = pos.getZ() >> 4;
        OpenPACServerAPI.get(server).getServerClaimsManager()
                .tryToClaim(dimension, claimingPlayer.getUUID(), 0, chunkX, chunkZ, chunkX, chunkZ, false);
    }
}
