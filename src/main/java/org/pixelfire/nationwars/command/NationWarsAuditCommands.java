package org.pixelfire.nationwars.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditIndex;
import org.pixelfire.nationwars.io.audit.AuditIndexEntry;
import org.pixelfire.nationwars.io.audit.AuditQueryResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read-only audit log queries: {@code /nationwars staff log player|city|nation|war|show}. Reversal
 * ({@code /nationwars staff revert}, {@code revert-session}) lands with the staff tooling stage, once
 * there is a real reversible action to revert — nothing here changes state.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsAuditCommands
{
    private static final int DEFAULT_LIMIT = 20;

    private NationWarsAuditCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .then(Commands.literal("log")
                                .requires(NationWarsAuditCommands::hasStaffInspectPermission)
                                .then(playerSubcommand())
                                .then(Commands.literal("city").then(targetArgument()))
                                .then(Commands.literal("nation").then(targetArgument()))
                                .then(Commands.literal("war").then(targetArgument()))
                                .then(Commands.literal("show")
                                        .then(Commands.argument("entryId", StringArgumentType.word())
                                                .executes(NationWarsAuditCommands::showEntry))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> playerSubcommand()
    {
        return Commands.literal("player")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> queryByPlayer(ctx, 0, DEFAULT_LIMIT))
                        .then(Commands.argument("sinceHours", IntegerArgumentType.integer(0))
                                .executes(ctx -> queryByPlayer(ctx, IntegerArgumentType.getInteger(ctx, "sinceHours"), DEFAULT_LIMIT))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                        .executes(ctx -> queryByPlayer(ctx, IntegerArgumentType.getInteger(ctx, "sinceHours"),
                                                IntegerArgumentType.getInteger(ctx, "limit"))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> targetArgument()
    {
        return Commands.argument("id", UuidArgument.uuid())
                .executes(NationWarsAuditCommands::queryByTarget);
    }

    private static boolean hasStaffInspectPermission(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            return PermissionAPI.getPermission(player, NationWarsPermissions.STAFF_INSPECT);
        }
        return source.hasPermission(NationWarsConfig.STAFF_PERMISSION_LEVEL.get());
    }

    private static int queryByPlayer(final CommandContext<CommandSourceStack> context, final int sinceHours, final int limit)
            throws CommandSyntaxException
    {
        final GameProfile profile = GameProfileArgument.getGameProfiles(context, "player").iterator().next();
        final long sinceMillis = System.currentTimeMillis() - Duration.ofHours(sinceHours).toMillis();

        return respondToQuery(context, index -> index.byActor(profile.getId(), sinceMillis, limit),
                "no audit entries for player " + profile.getName());
    }

    private static int queryByTarget(final CommandContext<CommandSourceStack> context)
    {
        final UUID id = UuidArgument.getUuid(context, "id");
        return respondToQuery(context, index -> index.byTarget(id), "no audit entries reference " + id);
    }

    private static int respondToQuery(final CommandContext<CommandSourceStack> context,
            final Function<AuditIndex, AuditQueryResult> query, final String emptyMessage)
    {
        final AuditIndex index = NationWarsMod.get().getAuditIndex();
        final AuditQueryResult result = query.apply(index);

        if (result instanceof AuditQueryResult.StillIndexing)
        {
            context.getSource().sendFailure(Component.literal(
                    "The nationwars audit index is still building from disk; try again in a moment."));
            return 0;
        }

        final List<AuditIndexEntry> entries = ((AuditQueryResult.Entries) result).entries();
        if (entries.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.literal(emptyMessage), false);
            return 0;
        }

        for (final AuditIndexEntry entry : entries)
        {
            context.getSource().sendSuccess(() -> Component.literal(formatSummary(entry)), false);
        }
        return entries.size();
    }

    private static int showEntry(final CommandContext<CommandSourceStack> context)
    {
        final String entryId = StringArgumentType.getString(context, "entryId");
        final AuditIndex index = NationWarsMod.get().getAuditIndex();
        final AuditQueryResult result = index.byEntryId(entryId);

        if (result instanceof AuditQueryResult.StillIndexing)
        {
            context.getSource().sendFailure(Component.literal(
                    "The nationwars audit index is still building from disk; try again in a moment."));
            return 0;
        }

        final List<AuditIndexEntry> matches = ((AuditQueryResult.Entries) result).entries();
        if (matches.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("No audit entry with id " + entryId + "."));
            return 0;
        }

        final AuditIndexEntry summary = matches.get(0);
        // Routed through AuditWriter (not the general worker pool) so this read runs on the same
        // writer thread as every append — it can never land in the middle of a write.
        NationWarsMod.get().getAuditWriter().readFull(summary, context.getSource().getServer(),
                entry -> context.getSource().sendSuccess(() -> Component.literal(formatFull(entry)), false));
        return 1;
    }

    private static String formatSummary(final AuditIndexEntry entry)
    {
        return entry.entryId() + " | " + entry.timestamp() + " | " + entry.actorName() + " | " + entry.actionType()
                + " | targets=" + entry.targets();
    }

    private static String formatFull(final AuditEntry entry)
    {
        return formatSummary(AuditIndexEntry.summarize(entry)) + "\nbefore: " + entry.before() + "\nafter: " + entry.after()
                + "\nreversible: " + entry.reversible();
    }
}
