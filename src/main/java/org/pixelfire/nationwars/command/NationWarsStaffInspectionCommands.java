package org.pixelfire.nationwars.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import org.pixelfire.nationwars.activity.PlayerActivityState;
import org.pixelfire.nationwars.compute.TickTimer;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.EvasionKey;
import org.pixelfire.nationwars.state.EvasionTracker;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.world.OpacNations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code /nationwars staff evasion|readiness} — read-only inspection of live tracker state, plus the
 * one mutation {@code evasion ... reset} needs to unstick a clock a staff member has already resolved
 * out of band.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID)
public final class NationWarsStaffInspectionCommands
{
    private NationWarsStaffInspectionCommands()
    {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("nationwars")
                .then(Commands.literal("staff")
                        .requires(NationWarsStaffInspectionCommands::hasStaffInspectPermission)
                        .then(Commands.literal("evasion")
                                .then(Commands.argument("nation", StringArgumentType.string())
                                        .then(Commands.argument("warId", UuidArgument.uuid())
                                                .executes(NationWarsStaffInspectionCommands::inspectEvasion)
                                                .then(Commands.literal("reset")
                                                        .executes(NationWarsStaffInspectionCommands::resetEvasion)))))
                        .then(Commands.literal("readiness")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(NationWarsStaffInspectionCommands::readiness)))
                        .then(Commands.literal("perf").executes(NationWarsStaffInspectionCommands::perf))
                        .then(Commands.literal("dump").executes(NationWarsStaffInspectionCommands::dump))));
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

    private static int inspectEvasion(final CommandContext<CommandSourceStack> context)
    {
        final EvasionKey key = resolveKey(context);
        if (key == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation or war."));
            return 0;
        }
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final EvasionTracker tracker = registry.evasionTrackers().getOrDefault(key, EvasionTracker.empty(key.warId(), key.nationId()));
        final long limitMs = NationWarsConfig.WAR_EVASION_LIMIT_SECONDS.get() * 1000L;
        context.getSource().sendSuccess(() -> Component.literal("Evasion clock for " + key.nationId() + " in war " + key.warId()
                + ": accrued=" + (tracker.evasionAccruedMs() / 60_000L) + "min / limit=" + (limitMs / 60_000L)
                + "min, qualifyingReady=" + (tracker.qualifyingReadyMs() / 60_000L) + "min, lastWarned="
                + tracker.lastWarnedThresholdPercent() + "%"), false);
        return 1;
    }

    private static int resetEvasion(final CommandContext<CommandSourceStack> context)
    {
        final EvasionKey key = resolveKey(context);
        if (key == null)
        {
            context.getSource().sendFailure(Component.literal("No such nation or war."));
            return 0;
        }
        NationWarsMod.get().getNationRegistry().evasionTrackers().put(key, EvasionTracker.empty(key.warId(), key.nationId()));
        context.getSource().sendSuccess(() -> Component.literal("Evasion clock reset for " + key.nationId() + " in war " + key.warId() + "."),
                true);
        return 1;
    }

    private static EvasionKey resolveKey(final CommandContext<CommandSourceStack> context)
    {
        final String nationName = StringArgumentType.getString(context, "nation");
        final UUID warId = UuidArgument.getUuid(context, "warId");
        final UUID nationId = OpacNations.findNationByName(context.getSource().getServer(), nationName);
        final War war = NationWarsMod.get().getNationRegistry().wars().get(warId);
        if (nationId == null || war == null)
        {
            return null;
        }
        return new EvasionKey(warId, nationId);
    }

    private static int readiness(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final UUID playerId = GameProfileArgument.getGameProfiles(context, "player").iterator().next().getId();
        final ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(playerId);
        if (target == null)
        {
            context.getSource().sendFailure(Component.literal("That player is not online."));
            return 0;
        }
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = context.getSource().getServer().overworld().getGameTime();
        final PlayerActivityState state = tracker.stateOf(target.getUUID(), currentTick, afkThresholdTicks);
        context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is " + state), false);
        return 1;
    }

    /**
     * Reports queue depth for the two off-main-thread systems, plus average and worst-in-window
     * main-thread cost for the four tick listeners that do the bulk of the mod's per-tick work —
     * {@code /nationwars staff perf}'s pass/fail signal for the spec's tick budget.
     */
    private static int perf(final CommandContext<CommandSourceStack> context)
    {
        final int workerQueueDepth = NationWarsMod.get().getWorkerPool().queueDepth();
        final int writerQueueDepth = NationWarsMod.get().getWriterThread().queueDepth();
        context.getSource().sendSuccess(() -> Component.literal("worker queue depth=" + workerQueueDepth
                + ", audit/IO writer queue depth=" + writerQueueDepth), false);

        reportTimer(context, "capture", NationWarsMod.get().getCaptureTickListener().perfTimer());
        reportTimer(context, "warLifecycle", NationWarsMod.get().getWarLifecycleListener().perfTimer());
        reportTimer(context, "evasion", NationWarsMod.get().getEvasionTickListener().perfTimer());
        reportTimer(context, "validationSweep", NationWarsMod.get().getValidationSweepListener().perfTimer());
        return 1;
    }

    private static void reportTimer(final CommandContext<CommandSourceStack> context, final String name, final TickTimer timer)
    {
        final var snapshot = timer.snapshot();
        context.getSource().sendSuccess(() -> Component.literal(name + ": avg=" + String.format("%.3f", snapshot.averageMs())
                + "ms, worstInWindow=" + snapshot.worstInWindowMs() + "ms, samples=" + snapshot.sampleCount()), false);
    }

    /**
     * Writes a JSON state snapshot to {@code logs/nationwars/dump-<timestamp>.json}: every city, war
     * and nation-state record (player UUIDs only, nothing else personally identifying), plus evasion
     * trackers and the derived config values. Records dump as their {@code toString()} rather than a
     * fully field-mapped JSON tree — still valid JSON, still everything needed for a bug report, without
     * a bespoke NBT/record-to-JSON mapping for types ({@code BlockPos}, {@code ResourceKey<Level>}) nothing
     * else in the mod needs to serialize.
     */
    private static int dump(final CommandContext<CommandSourceStack> context)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final JsonObject root = new JsonObject();
        root.addProperty("timestamp", Instant.now().toString());

        final JsonArray cities = new JsonArray();
        registry.cities().values().forEach(city -> cities.add(city.toString()));
        root.add("cities", cities);

        final JsonArray checkpoints = new JsonArray();
        registry.checkpoints().values().forEach(checkpoint -> checkpoints.add(checkpoint.toString()));
        root.add("checkpoints", checkpoints);

        final JsonArray wars = new JsonArray();
        registry.wars().values().forEach(war -> wars.add(war.toString()));
        root.add("wars", wars);

        final JsonArray nationStates = new JsonArray();
        registry.nationStates().values().forEach(state -> nationStates.add(state.toString()));
        root.add("nationStates", nationStates);

        final JsonArray settlements = new JsonArray();
        registry.settlements().values().forEach(settlement -> settlements.add(settlement.toString()));
        root.add("settlements", settlements);

        final JsonArray evasionTrackers = new JsonArray();
        registry.evasionTrackers().values().forEach(tracker -> evasionTrackers.add(tracker.toString()));
        root.add("evasionTrackers", evasionTrackers);

        final JsonObject config = new JsonObject();
        config.addProperty("tierCount", NationWarsConfig.tiers.size());
        config.addProperty("paymentValueCount", NationWarsConfig.paymentValues.size());
        config.addProperty("effectiveMinCoreDistance", NationWarsConfig.effectiveMinCoreDistance);
        root.add("config", config);

        final Path path = Path.of("logs", "nationwars", "dump-" + System.currentTimeMillis() + ".json");
        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        }
        catch (final IOException e)
        {
            context.getSource().sendFailure(Component.literal("Failed to write dump: " + e.getMessage()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Wrote state dump to " + path), true);
        return 1;
    }
}
