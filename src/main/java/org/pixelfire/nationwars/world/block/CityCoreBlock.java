package org.pixelfire.nationwars.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.menu.CityCoreMenu;

/**
 * Indestructibility comes entirely from its {@link BlockBehaviour.Properties} (matching vanilla
 * bedrock: {@code strength(-1, 3_600_000)} blocks every break including creative instant-mine, and the
 * high resistance value blocks any vanilla explosion), not from a cancelled {@code BreakEvent} — there
 * is no gameplay path this block allows removal through, so there is nothing to cancel.
 */
public class CityCoreBlock extends Block implements EntityBlock
{
    public CityCoreBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state)
    {
        return new CityCoreBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos, final Player player,
            final InteractionHand hand, final BlockHitResult hit)
    {
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof CityCoreBlockEntity blockEntity)
                || blockEntity.cityId() == null)
        {
            return InteractionResult.PASS;
        }
        final City city = NationWarsMod.get().getNationRegistry().cities().get(blockEntity.cityId());
        if (city == null)
        {
            return InteractionResult.PASS;
        }
        final NationSnapshot nation = OpacNations.nationOf(((ServerLevel) level).getServer(), serverPlayer);
        if (nation == null || !nation.nationId().equals(city.ownerNationId()))
        {
            serverPlayer.sendSystemMessage(Component.literal("Only citizens of " + city.name() + "'s nation may open its city GUI."));
            return InteractionResult.SUCCESS;
        }
        NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((windowId, inventory, p) -> new CityCoreMenu(windowId, inventory, pos), Component.literal(city.name())),
                pos);
        return InteractionResult.SUCCESS;
    }
}
