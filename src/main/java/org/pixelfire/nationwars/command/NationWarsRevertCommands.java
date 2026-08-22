package org.pixelfire.nationwars.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditIndex;
import org.pixelfire.nationwars.io.audit.AuditIndexEntry;
import org.pixelfire.nationwars.io.audit.AuditQueryResult;
import org.pixelfire.nationwars.io.audit.AuditReverters;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.io.audit.RevertDependencyCheck;
import org.pixelfire.nationwars.io.audit.Reverter;
import org.pixelfire.nationwars.io.audit.Ulid;
import org.pixelfire.nationwars.state.NationRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * {@code /nationwars staff revert <entryId>} and {@code revert-session <player> [sinceHours]}
 * Both are compensating actions — a revert is itself logged with {@code revertOf} set, nothing
 * is ever erased — and both refuse an entry a later, still-live entry depends on, naming exactly which.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsRevertCommands
{
    private NationWarsRevertCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .requires(NationWarsRevertCommands::hasStaffRevertPermission)
                        .then(Commands.literal("revert")
                                .then(Commands.argument("entryId", StringArgumentType.word())
                                        .executes(NationWarsRevertCommands::revertOne)))
                        .then(Commands.literal("revert-session")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> revertSession(ctx, 0))
                                        .then(Commands.argument("sinceHours", IntegerArgumentType.integer(0))
                                                .executes(ctx -> revertSession(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "sinceHours"))))))));
    }

    private static boolean hasStaffRevertPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_REVERT);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static int revertOne(final CommandContext<CommandSourceStack> context)
    {
        final String entryId = StringArgumentType.getString(context, "entryId");
        final AuditIndex index = NationWarsMod.get().getAuditIndex();
        final AuditQueryResult result = index.byEntryId(entryId);
        if (result instanceof AuditQueryResult.StillIndexing)
        {
            context.getSource().sendFailure(Component.literal("The nationwars audit index is still building; try again in a moment."));
            return 0;
        }
        final List<AuditIndexEntry> matches = ((AuditQueryResult.Entries) result).entries();
        if (matches.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("No audit entry with id " + entryId + "."));
            return 0;
        }
        final AuditIndexEntry summary = matches.get(0);
        final Optional<String> refusal = checkRevertible(index, summary);
        if (refusal.isPresent())
        {
            context.getSource().sendFailure(Component.literal(refusal.get()));
            return 0;
        }

        final MinecraftServer server = context.getSource().getServer();
        final ServerPlayer executor = context.getSource().getPlayer();
        NationWarsMod.get().getAuditWriter().readFull(summary, server, entry -> performRevert(context, server, executor, entry));
        return 1;
    }

    private static int revertSession(final CommandContext<CommandSourceStack> context, final int sinceHours) throws CommandSyntaxException
    {
        final GameProfile profile = GameProfileArgument.getGameProfiles(context, "player").iterator().next();
        final long sinceMillis = sinceHours <= 0 ? 0L : System.currentTimeMillis() - Duration.ofHours(sinceHours).toMillis();
        final AuditIndex index = NationWarsMod.get().getAuditIndex();
        final AuditQueryResult result = index.byActor(profile.getId(), sinceMillis, Integer.MAX_VALUE);
        if (result instanceof AuditQueryResult.StillIndexing)
        {
            context.getSource().sendFailure(Component.literal("The nationwars audit index is still building; try again in a moment."));
            return 0;
        }
        final List<AuditIndexEntry> entries = ((AuditQueryResult.Entries) result).entries();
        if (entries.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.literal("No audit entries for " + profile.getName() + " in that window."), false);
            return 0;
        }

        final MinecraftServer server = context.getSource().getServer();
        final ServerPlayer executor = context.getSource().getPlayer();
        revertNext(context, index, entries, 0, server, executor);
        return entries.size();
    }

    /**
     * Walks {@code entries} (already newest-first from {@link AuditIndex#byActor}) one at a time,
     * since each entry's own revert must land before the dependency check for the next one can see it —
     * a compromised-account session is usually a chain of edits to the same handful of records.
     */
    private static void revertNext(final CommandContext<CommandSourceStack> context, final AuditIndex index,
            final List<AuditIndexEntry> entries, final int nextIndex, final MinecraftServer server, final ServerPlayer executor)
    {
        if (nextIndex >= entries.size())
        {
            context.getSource().sendSuccess(() -> Component.literal("revert-session complete: " + entries.size() + " entries processed."), true);
            return;
        }
        final AuditIndexEntry summary = entries.get(nextIndex);
        final Optional<String> refusal = checkRevertible(index, summary);
        if (refusal.isPresent())
        {
            context.getSource().sendFailure(Component.literal(summary.entryId() + ": skipped — " + refusal.get()));
            revertNext(context, index, entries, nextIndex + 1, server, executor);
            return;
        }
        NationWarsMod.get().getAuditWriter().readFull(summary, server, entry ->
        {
            performRevert(context, server, executor, entry);
            revertNext(context, index, entries, nextIndex + 1, server, executor);
        });
    }

    private static Optional<String> checkRevertible(final AuditIndex index, final AuditIndexEntry summary)
    {
        if (!summary.reversible())
        {
            return Optional.of(summary.entryId() + " is not reversible.");
        }
        if (summary.revertOf() != null)
        {
            return Optional.of(summary.entryId() + " is itself a revert entry and cannot be reverted.");
        }
        final long windowMillis = NationWarsConfig.AUDIT_REVERT_WINDOW_DAYS.get() * 86_400_000L;
        if (System.currentTimeMillis() - summary.timestamp() > windowMillis)
        {
            return Optional.of(summary.entryId() + " is older than auditRevertWindowDays and can no longer be reverted.");
        }
        final AuditQueryResult sharing = index.byTargets(summary.targets());
        if (sharing instanceof AuditQueryResult.StillIndexing)
        {
            return Optional.of("The nationwars audit index is still building; try again in a moment.");
        }
        final List<String> blocking = RevertDependencyCheck.blockingEntries(summary, ((AuditQueryResult.Entries) sharing).entries());
        if (!blocking.isEmpty())
        {
            return Optional.of(summary.entryId() + " is depended on by later entries and cannot be reverted first: "
                    + String.join(", ", blocking));
        }
        return Optional.empty();
    }

    private static void performRevert(final CommandContext<CommandSourceStack> context, final MinecraftServer server,
            final ServerPlayer executor, final AuditEntry entry)
    {
        final Reverter reverter = AuditReverters.get(entry.actionType());
        if (reverter == null)
        {
            context.getSource().sendFailure(Component.literal(entry.entryId() + ": no reverter is registered for action type "
                    + entry.actionType() + "."));
            return;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final Optional<String> outcome = reverter.revert(registry, server, entry);

        final CompoundTag after = new CompoundTag();
        after.putString("originalActionType", entry.actionType().toString());
        outcome.ifPresent(message -> after.putString("partialRevertNote", message));
        NationWarsMod.get().getAuditWriter().append(new AuditEntry(
                Ulid.generate(), System.currentTimeMillis(),
                executor != null ? executor.getUUID() : null, executor != null ? executor.getGameProfile().getName() : "CONSOLE",
                entry.actorNationId(), ActorRole.STAFF, AuditSource.COMMAND,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "audit_revert"), entry.targets(), entry.after(), after, false,
                entry.entryId(), null));

        if (outcome.isPresent())
        {
            context.getSource().sendSuccess(() -> Component.literal(entry.entryId() + ": " + outcome.get()), true);
        }
        else
        {
            context.getSource().sendSuccess(() -> Component.literal(entry.entryId() + " (" + entry.actionType() + ") reverted."), true);
        }
    }
}
