package org.pixelfire.nationwars.world.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.PaymentEntry;
import org.pixelfire.nationwars.config.PaymentValuation;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.UpgradeContext;
import org.pixelfire.nationwars.state.UpgradeFailureReason;
import org.pixelfire.nationwars.state.UpgradePreconditions;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import org.pixelfire.nationwars.world.block.NationWarsMenus;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One payment slot; {@link ContainerData} syncs tier, banked payment, checkpoint count, city state, and
 * an occupation countdown (always 0 until occupation exists). Confirming an upgrade is
 * {@link #clickMenuButton}, button id {@link #UPGRADE_BUTTON_ID}, so vanilla menu validation applies.
 */
public final class CityCoreMenu extends AbstractContainerMenu
{
    public static final int UPGRADE_BUTTON_ID = 0;

    private static final int DATA_TIER = 0;
    private static final int DATA_BANKED_PAYMENT = 1;
    private static final int DATA_CHECKPOINT_COUNT = 2;
    private static final int DATA_CITY_STATE = 3;
    private static final int DATA_OCCUPATION_COUNTDOWN = 4;
    private static final int DATA_COUNT = 5;

    private final BlockPos corePos;
    private final ItemStackHandler paymentSlot = new ItemStackHandler(1)
    {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack)
        {
            return valueOf(stack).isPresent();
        }
    };
    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(final int index)
        {
            final City city = currentCity();
            if (city == null)
            {
                return 0;
            }
            return switch (index)
            {
                case DATA_TIER -> city.tier();
                case DATA_BANKED_PAYMENT -> (int) Math.min(Integer.MAX_VALUE, city.bankedPayment());
                case DATA_CHECKPOINT_COUNT -> city.checkpointIds().size();
                case DATA_CITY_STATE -> city.state().ordinal();
                case DATA_OCCUPATION_COUNTDOWN -> 0;
                default -> 0;
            };
        }

        @Override
        public void set(final int index, final int value)
        {
        }

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    public CityCoreMenu(final int windowId, final Inventory playerInventory, final BlockPos corePos)
    {
        super(NationWarsMenus.CITY_CORE.get(), windowId);
        this.corePos = corePos;
        addSlot(new SlotItemHandler(paymentSlot, 0, 80, 35));
        addDataSlots(data);
    }

    public int tier()
    {
        return data.get(DATA_TIER);
    }

    public int bankedPayment()
    {
        return data.get(DATA_BANKED_PAYMENT);
    }

    public int checkpointCount()
    {
        return data.get(DATA_CHECKPOINT_COUNT);
    }

    public int cityStateOrdinal()
    {
        return data.get(DATA_CITY_STATE);
    }

    private City currentCity()
    {
        return NationWarsMod.get().getNationRegistry().cities().values().stream()
                .filter(city -> city.corePos().equals(corePos))
                .findFirst().orElse(null);
    }

    private OptionalLong valueOf(final ItemStack stack)
    {
        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null)
        {
            return OptionalLong.empty();
        }
        final String idString = id.toString();
        final List<PaymentEntry> entries = NationWarsConfig.paymentValues;
        final int multiplier = NationWarsConfig.PAYMENT_BLOCK_MULTIPLIER.get();
        final OptionalLong direct = PaymentValuation.valueOf(idString, tagId -> matchesTag(stack, tagId), entries);
        if (direct.isPresent())
        {
            return direct;
        }
        return PaymentValuation.blockFormValueOf(idString, tagId -> matchesTag(stack, tagId), entries, multiplier);
    }

    private static boolean matchesTag(final ItemStack stack, final String tagId)
    {
        final ResourceLocation rl = ResourceLocation.tryParse(tagId);
        return rl != null && stack.is(TagKey.create(Registries.ITEM, rl));
    }

    /**
     * Consumes whatever sits in the payment slot into the city's banked payment. Called once per tick
     * while the menu is open, same as vanilla furnace-style menus that react to slot contents rather
     * than a dedicated network packet.
     */
    @Override
    public void broadcastChanges()
    {
        final ItemStack inserted = paymentSlot.getStackInSlot(0);
        if (!inserted.isEmpty())
        {
            final OptionalLong value = valueOf(inserted);
            if (value.isPresent())
            {
                final long total = value.getAsLong() * inserted.getCount();
                applyPayment(total);
                paymentSlot.setStackInSlot(0, ItemStack.EMPTY);
            }
        }
        super.broadcastChanges();
    }

    private void applyPayment(final long amount)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final City city = currentCity();
        if (city == null)
        {
            return;
        }
        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null)
            {
                registry.cities().put(city.cityId(), withBankedPayment(current, current.bankedPayment() + amount));
            }
        }, city.cityId());
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id)
    {
        if (id != UPGRADE_BUTTON_ID || !(player instanceof ServerPlayer serverPlayer))
        {
            return false;
        }
        attemptUpgrade(serverPlayer);
        return true;
    }

    private void attemptUpgrade(final ServerPlayer player)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final City city = currentCity();
        if (city == null)
        {
            return;
        }
        final var server = player.getServer();
        final NationSnapshot nation = OpacNations.nationOf(server, player);
        if (nation == null || !nation.nationId().equals(city.ownerNationId()))
        {
            return;
        }
        final PartyMemberRank requiredRank = parseRank(NationWarsConfig.CITY_UPGRADE_RANK.get());
        if (nation.rankOrdinal() < requiredRank.ordinal())
        {
            player.sendSystemMessage(Component.literal("Your rank is too low to confirm this upgrade."));
            return;
        }

        final List<TierDefinition> tiers = NationWarsConfig.tiers;
        final boolean hasNextTier = city.tier() + 1 < tiers.size();
        final TierDefinition currentTier = tiers.get(city.tier());
        final TierDefinition nextTier = hasNextTier ? tiers.get(city.tier() + 1) : null;

        boolean expandedRadiusTooClose = false;
        if (hasNextTier && player.level() instanceof ServerLevel level)
        {
            expandedRadiusTooClose = isExpandedRadiusTooClose(registry, city, level, nextTier.radius());
        }

        final var nationState = registry.nationStates().get(nation.nationId());
        final UpgradeContext context = new UpgradeContext(
                hasNextTier,
                city.state() == CityState.ACTIVE,
                city.bankedPayment(),
                hasNextTier ? nextTier.cost() : Long.MAX_VALUE,
                city.checkpointIds().size(),
                currentTier.maxCheckpoints(),
                expandedRadiusTooClose,
                nationState != null && nationState.lockedByWarId() != null,
                nationState != null && !nationState.activeWarIds().isEmpty(),
                NationWarsConfig.ALLOW_UPGRADE_DURING_WAR.get());

        final Optional<UpgradeFailureReason> failure = UpgradePreconditions.check(context);
        if (failure.isPresent())
        {
            player.sendSystemMessage(Component.literal(failure.get().message()));
            return;
        }

        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null)
            {
                registry.cities().put(city.cityId(), withTier(current, current.tier() + 1, current.bankedPayment() - nextTier.cost()));
            }
        }, city.cityId());

        final CompoundTag after = new CompoundTag();
        after.putInt("tier", city.tier() + 1);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                player.getUUID(), player.getGameProfile().getName(), nation.nationId(), ActorRole.MODERATOR,
                AuditSource.GUI, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_upgraded"),
                List.of(city.cityId()), new CompoundTag(), after, false));

        player.sendSystemMessage(Component.literal("City upgraded to tier " + (city.tier() + 2) + "."));
    }

    private boolean isExpandedRadiusTooClose(final NationRegistry registry, final City city, final ServerLevel level, final int nextRadius)
    {
        final double threshold = nextRadius + NationWarsConfig.MIN_CHECKPOINT_SPACING.get();
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (checkpoint.cityId().equals(city.cityId()) || !checkpoint.dimension().equals(level.dimension()))
            {
                continue;
            }
            final double dx = checkpoint.pos().getX() - city.corePos().getX();
            final double dz = checkpoint.pos().getZ() - city.corePos().getZ();
            if (Math.sqrt(dx * dx + dz * dz) < threshold)
            {
                return true;
            }
        }
        return false;
    }

    private static City withBankedPayment(final City city, final long bankedPayment)
    {
        return new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(), city.dimension(),
                city.corePos(), city.tier(), bankedPayment, city.checkpointIds(), city.state(), city.occupiedByNationId(),
                city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(),
                city.transferCount(), city.pendingDisbandAt(), city.dormantSince());
    }

    private static City withTier(final City city, final int tier, final long bankedPayment)
    {
        return new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(), city.dimension(),
                city.corePos(), tier, bankedPayment, city.checkpointIds(), city.state(), city.occupiedByNationId(),
                city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(),
                city.transferCount(), city.pendingDisbandAt(), city.dormantSince());
    }

    private static PartyMemberRank parseRank(final String name)
    {
        try
        {
            return PartyMemberRank.valueOf(name);
        }
        catch (final IllegalArgumentException e)
        {
            return PartyMemberRank.MODERATOR;
        }
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player)
    {
        return currentCity() != null;
    }
}
