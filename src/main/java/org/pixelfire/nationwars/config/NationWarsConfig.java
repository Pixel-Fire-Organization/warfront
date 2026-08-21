package org.pixelfire.nationwars.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.pixelfire.nationwars.NationWarsMod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Every gameplay constant the mod uses. Nothing is hardcoded elsewhere — a feature that needs a
 * number reads it from this class instead of embedding it in code.
 *
 * <p>Values are baked into plain fields on {@link ModConfigEvent}, and the list-shaped ones
 * ({@code tiers}, {@code payments.values}, {@code logging.categories}) are parsed and validated by
 * the pure helpers in this package so those rules stay testable without booting Forge.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NationWarsConfig
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ---- Placement and cities ----------------------------------------------------------------

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SURFACE_PLACEMENT;
    public static final ForgeConfigSpec.IntValue SURFACE_TOLERANCE;
    public static final ForgeConfigSpec.IntValue COLUMN_REVALIDATE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue MIN_CORE_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_CITIES_PER_NATION;
    public static final ForgeConfigSpec.IntValue MAX_CITIES_PER_MEMBER;
    public static final ForgeConfigSpec.IntValue CITY_FOUND_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue FOUNDING_GRACE_PERIOD_SECONDS;
    public static final ForgeConfigSpec.IntValue CITY_DISBAND_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue DORMANT_CITY_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MIN_CHECKPOINT_SPACING;
    public static final ForgeConfigSpec.DoubleValue MIN_CORE_CLEARANCE;
    public static final ForgeConfigSpec.IntValue CHECKPOINT_MOVE_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue CHECKPOINT_RESPAWN_DELAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FOUNDING_DURING_WAR;

    // ---- Tiers and payment --------------------------------------------------------------------

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PAYMENT_VALUES;
    public static final ForgeConfigSpec.IntValue PAYMENT_BLOCK_MULTIPLIER;

    // ---- Ranks ----------------------------------------------------------------------------------

    public static final ForgeConfigSpec.ConfigValue<String> CITY_FOUND_RANK;
    public static final ForgeConfigSpec.ConfigValue<String> CHECKPOINT_PLACE_RANK;
    public static final ForgeConfigSpec.ConfigValue<String> CITY_UPGRADE_RANK;
    public static final ForgeConfigSpec.BooleanValue ALLIES_CAN_PLACE_CHECKPOINTS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_UPGRADE_DURING_WAR;
    public static final ForgeConfigSpec.IntValue STAFF_PERMISSION_LEVEL;

    // ---- Capture --------------------------------------------------------------------------------

    public static final ForgeConfigSpec.IntValue CAPTURE_TICK_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue CAPTURE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CAPTURE_ZONE_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue BASE_CAPTURE_RATE;
    public static final ForgeConfigSpec.DoubleValue DEFENDER_RECOVERY_RATE;
    public static final ForgeConfigSpec.DoubleValue DECAY_RATE;
    public static final ForgeConfigSpec.DoubleValue ATTACKER_STACK_BONUS;
    public static final ForgeConfigSpec.DoubleValue ATTACKER_STACK_CAP;
    public static final ForgeConfigSpec.IntValue CHECKPOINT_LOCKOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_CAN_CAPTURE;
    public static final ForgeConfigSpec.IntValue OCCUPATION_LOCK_DURATION_SECONDS;
    public static final ForgeConfigSpec.BooleanValue OCCUPATION_SUSPENDS_CLAIM_PROTECTION;

    // ---- Activity and combat ---------------------------------------------------------------------

    public static final ForgeConfigSpec.IntValue LOGIN_SHIELD_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue AFK_THRESHOLD_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ACTIVITY_MOVE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue AFK_EXIT_SHIELD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOGIN_SHIELD_GRANTS_INVULNERABILITY;
    public static final ForgeConfigSpec.BooleanValue COMBAT_LOG_KILL;
    public static final ForgeConfigSpec.IntValue COMBAT_TAG_DURATION_SECONDS;
    public static final ForgeConfigSpec.BooleanValue COMBAT_LOG_GRACE_ON_SERVER_STOP;

    // ---- War ------------------------------------------------------------------------------------

    public static final ForgeConfigSpec.IntValue WAR_PREP_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue WAR_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue WAR_DURATION_MAX_SECONDS;
    public static final ForgeConfigSpec.IntValue PRESENCE_GRACE_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue WAR_EVASION_LIMIT_SECONDS;
    public static final ForgeConfigSpec.IntValue WAR_PARTICIPATION_MINIMUM_SECONDS;
    public static final ForgeConfigSpec.BooleanValue EVASION_APPLIES_TO_ATTACKERS;
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_WARS;
    public static final ForgeConfigSpec.IntValue DEFAULT_POST_WAR_COOLDOWN_HOURS;
    public static final ForgeConfigSpec.IntValue ALLIANCE_CASCADE_DEPTH;
    public static final ForgeConfigSpec.BooleanValue ALLOW_COUNTER_OFFENSIVE;
    public static final ForgeConfigSpec.DoubleValue COUNTER_OFFENSIVE_SCORE_RATIO;
    public static final ForgeConfigSpec.IntValue COUNTER_OFFENSIVE_MIN_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue COUNTER_OFFENSIVE_PREP_SECONDS;
    public static final ForgeConfigSpec.IntValue PENDING_ENTRY_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<String> WAR_WINDOW_START;
    public static final ForgeConfigSpec.ConfigValue<String> WAR_WINDOW_END;

    // ---- Settlement -----------------------------------------------------------------------------

    public static final ForgeConfigSpec.IntValue SETTLEMENT_WINDOW_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<String> SETTLEMENT_LOCK_SCOPE;
    public static final ForgeConfigSpec.IntValue OFFER_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.IntValue DEADLOCK_THRESHOLD_SECONDS;
    public static final ForgeConfigSpec.IntValue DEADLOCK_REJECTIONS;
    public static final ForgeConfigSpec.IntValue SCORE_CHECKPOINT_CAPTURE;
    public static final ForgeConfigSpec.IntValue SCORE_CHECKPOINT_DEFENDED;
    public static final ForgeConfigSpec.IntValue SCORE_CITY_OCCUPIED;
    public static final ForgeConfigSpec.IntValue SCORE_CITY_HELD;
    public static final ForgeConfigSpec.IntValue SCORE_PARTICIPATION_PER_10_MIN;
    public static final ForgeConfigSpec.DoubleValue CITY_VALUE_TIER_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue CITY_VALUE_BANK_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue CITY_VALUE_CHECKPOINT_WEIGHT;

    // ---- Territory ------------------------------------------------------------------------------

    public static final ForgeConfigSpec.ConfigValue<String> CHECKPOINT_CLAIM_SHAPE;
    public static final ForgeConfigSpec.ConfigValue<String> CITY_CORE_CLAIM_SHAPE;
    public static final ForgeConfigSpec.BooleanValue SYNC_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue RELEASE_CLAIMS_ON_DISBAND;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WAR_PROTECTION_OVERRIDE;

    // ---- Logging --------------------------------------------------------------------------------

    public static final ForgeConfigSpec.ConfigValue<String> LOGGING_DEFAULT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOGGING_CATEGORIES;
    public static final ForgeConfigSpec.ConfigValue<String> LOG_TO_SERVER_CONSOLE;
    public static final ForgeConfigSpec.IntValue LOG_FILE_SIZE_MB;
    public static final ForgeConfigSpec.IntValue LOG_FILE_HISTORY;
    public static final ForgeConfigSpec.IntValue SLOW_TASK_THRESHOLD_MS;

    // ---- Infrastructure -------------------------------------------------------------------------

    public static final ForgeConfigSpec.IntValue WORKER_THREADS;
    public static final ForgeConfigSpec.IntValue WORKER_QUEUE_CAPACITY;
    public static final ForgeConfigSpec.IntValue LOCK_STRIPES;
    public static final ForgeConfigSpec.IntValue AUDIT_RETENTION_DAYS;
    public static final ForgeConfigSpec.IntValue AUDIT_REVERT_WINDOW_DAYS;
    public static final ForgeConfigSpec.IntValue NATION_VALIDATION_INTERVAL_SECONDS;

    public static final ForgeConfigSpec SPEC;

    static
    {
        BUILDER.push("placement");
        ALLOWED_DIMENSIONS = BUILDER.comment("Dimensions in which a city can be founded and checkpoints placed. Other dimensions never see city/checkpoint mechanics.")
                .define("allowedDimensions", List.of("minecraft:overworld"));
        BLOCKED_DIMENSIONS = BUILDER.comment("Dimensions excluded even if they would otherwise match allowedDimensions.")
                .define("blockedDimensions", List.of());
        REQUIRE_SURFACE_PLACEMENT = BUILDER.comment(
                        "If true, a core or checkpoint can only be placed near the world surface, not down a mineshaft or in a cave.")
                .define("requireSurfacePlacement", true);
        SURFACE_TOLERANCE = BUILDER.comment("How many blocks below the surface heightmap a placement is still allowed, "
                        + "so terraforming a small pit doesn't block placement.")
                .defineInRange("surfaceTolerance", 4, 0, 64);
        COLUMN_REVALIDATE_INTERVAL_SECONDS = BUILDER.comment(
                        "How often, in seconds, the mod re-checks that the sky above each core/checkpoint is still clear. "
                                + "Catches obstructions placed by /setblock or world-edit tools that bypass live prevention.")
                .defineInRange("columnRevalidateInterval", 600, 1, Integer.MAX_VALUE);
        MIN_CORE_DISTANCE = BUILDER.comment(
                        "Minimum horizontal distance, in blocks, required between two City Cores. Too small a value relative to "
                                + "the largest tier's radius is corrected automatically at startup (see the warning if that happens).")
                .defineInRange("minCoreDistance", 192, 1, Integer.MAX_VALUE);
        MAX_CITIES_PER_NATION = BUILDER.comment("Hard cap on how many cities one nation may found in total.")
                .defineInRange("maxCitiesPerNation", 5, 1, Integer.MAX_VALUE);
        MAX_CITIES_PER_MEMBER = BUILDER.comment(
                        "Per-member city allowance: a nation may found up to this many cities for every member it has, "
                                + "in addition to being capped by maxCitiesPerNation.")
                .defineInRange("maxCitiesPerMember", 2, 1, Integer.MAX_VALUE);
        CITY_FOUND_COOLDOWN_SECONDS = BUILDER.comment("Minimum time, in seconds, a nation must wait between founding two cities.")
                .defineInRange("cityFoundCooldown", 1800, 0, Integer.MAX_VALUE);
        FOUNDING_GRACE_PERIOD_SECONDS = BUILDER.comment(
                        "Time, in seconds, after founding during which a new city cannot be targeted by a war and is exempt "
                                + "from the checkpoint minimum.")
                .defineInRange("foundingGracePeriod", 900, 0, Integer.MAX_VALUE);
        CITY_DISBAND_DELAY_SECONDS = BUILDER.comment(
                        "Countdown, in seconds, between a leader requesting disbandment and it actually happening. "
                                + "Broadcast server-wide and cancellable during the wait.")
                .defineInRange("cityDisbandDelay", 300, 0, Integer.MAX_VALUE);
        DORMANT_CITY_EXPIRY_SECONDS = BUILDER.comment(
                        "How long, in seconds, a city may stay DORMANT (below its checkpoint minimum) before its core is removed.")
                .defineInRange("dormantCityExpiry", 604800, 0, Integer.MAX_VALUE);
        MIN_CHECKPOINT_SPACING = BUILDER.comment("Minimum distance, in blocks, required between two checkpoints of the same city.")
                .defineInRange("minCheckpointSpacing", 3.0, 0.0, Double.MAX_VALUE);
        MIN_CORE_CLEARANCE = BUILDER.comment("Minimum distance, in blocks, a checkpoint must keep from its city's core.")
                .defineInRange("minCoreClearance", 3.0, 0.0, Double.MAX_VALUE);
        CHECKPOINT_MOVE_GRACE_SECONDS = BUILDER.comment(
                        "Window, in seconds, after breaking a checkpoint during which the same player can re-place it and keep "
                                + "its identity and capture history, instead of it counting as a fresh checkpoint.")
                .defineInRange("checkpointMoveGrace", 60, 0, Integer.MAX_VALUE);
        CHECKPOINT_RESPAWN_DELAY_SECONDS = BUILDER.comment(
                        "How long, in seconds, a checkpoint stays shattered before its cosmetic break/capture respawn effect finishes.")
                .defineInRange("checkpointRespawnDelay", 3, 0, Integer.MAX_VALUE);
        ALLOW_FOUNDING_DURING_WAR = BUILDER.comment(
                        "If false, a nation currently in an unsettled war cannot found a new city.")
                .define("allowFoundingDuringWar", false);
        BUILDER.pop();

        BUILDER.push("tiers");
        TIERS = BUILDER.comment(
                        "The city tier ladder, one entry per tier, in order. Each entry is \"radius/cost/minCheckpoints/maxCheckpoints\":",
                        "radius is how far checkpoints can be placed from the core; cost is the banked payment needed to reach it;",
                        "minCheckpoints/maxCheckpoints bound how many checkpoints the city may have at that tier.",
                        "A tier's minCheckpoints must equal the previous tier's maxCheckpoints, so a city must fill its current "
                                + "tier before it can upgrade; this is checked at startup.")
                .defineList("tiers", List.of("5/0/1/5", "8/128/5/8", "13/512/8/13", "21/2048/13/21"), o -> o instanceof String);
        BUILDER.push("payments");
        PAYMENT_VALUES = BUILDER.comment(
                        "What each item is worth toward a tier upgrade's banked payment, as \"<item-or-tag>=<value>\". "
                                + "Prefix a tag with # to price every item in it (e.g. \"#forge:gems/ruby=12\"); tag matches are checked "
                                + "after exact item ids. Only items listed here can be inserted into a city's payment slot.")
                .defineList("values", List.of(
                        "minecraft:iron_ingot=1",
                        "minecraft:gold_ingot=3",
                        "minecraft:emerald=6",
                        "minecraft:diamond=9",
                        "minecraft:netherite_ingot=36"), o -> o instanceof String);
        PAYMENT_BLOCK_MULTIPLIER = BUILDER.comment(
                        "Multiplier applied when a listed ingot/gem is inserted in its compressed block form instead of as a single item.")
                .defineInRange("blockMultiplier", 9, 1, Integer.MAX_VALUE);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("ranks");
        CITY_FOUND_RANK = BUILDER.comment("Minimum party rank required to found a city.").define("cityFoundRank", "MEMBER");
        CHECKPOINT_PLACE_RANK = BUILDER.comment("Minimum party rank required to place a checkpoint.").define("checkpointPlaceRank", "MEMBER");
        CITY_UPGRADE_RANK = BUILDER.comment("Minimum party rank required to confirm a tier upgrade in the City GUI.")
                .define("cityUpgradeRank", "MODERATOR");
        ALLIES_CAN_PLACE_CHECKPOINTS = BUILDER.comment("If true, members of an allied nation may also place checkpoints for a city, not just its own citizens.")
                .define("alliesCanPlaceCheckpoints", false);
        ALLOW_UPGRADE_DURING_WAR = BUILDER.comment(
                        "If false, a nation currently in an unsettled war cannot confirm a tier upgrade.")
                .define("allowUpgradeDuringWar", false);
        STAFF_PERMISSION_LEVEL = BUILDER.comment(
                        "Vanilla operator level that counts as staff when no permission mod is installed to resolve the "
                                + "nationwars.staff.* permission nodes.")
                .defineInRange("staffPermissionLevel", 2, 0, 4);
        BUILDER.pop();

        BUILDER.push("capture");
        CAPTURE_TICK_INTERVAL = BUILDER.comment("How often, in game ticks, each contested checkpoint's capture zone is re-evaluated.")
                .defineInRange("captureTickInterval", 10, 1, Integer.MAX_VALUE);
        CAPTURE_RADIUS = BUILDER.comment("Horizontal radius, in blocks, of a checkpoint's capture zone.")
                .defineInRange("captureRadius", 5.0, 0.1, 1024.0);
        CAPTURE_ZONE_HEIGHT = BUILDER.comment("How far, in blocks, the capture zone extends above and below the checkpoint.")
                .defineInRange("captureZoneHeight", 8.0, 0.1, 1024.0);
        BASE_CAPTURE_RATE = BUILDER.comment("Capture progress gained per second while attackers are present and no defenders are.")
                .defineInRange("baseCaptureRate", 1.0 / 45.0, 0.0, 10.0);
        DEFENDER_RECOVERY_RATE = BUILDER.comment("Capture progress lost per second while defenders are present and no attackers are.")
                .defineInRange("defenderRecoveryRate", 1.0 / 20.0, 0.0, 10.0);
        DECAY_RATE = BUILDER.comment("Capture progress lost per second while nobody from either side is present.")
                .defineInRange("decayRate", 1.0 / 90.0, 0.0, 10.0);
        ATTACKER_STACK_BONUS = BUILDER.comment("Extra capture speed per additional attacker beyond the first, before the cap below applies.")
                .defineInRange("attackerStackBonus", 0.5, 0.0, 100.0);
        ATTACKER_STACK_CAP = BUILDER.comment("Maximum multiplier capture speed can reach from stacking attackers in one zone.")
                .defineInRange("attackerStackCap", 3.0, 1.0, 1000.0);
        CHECKPOINT_LOCKOUT_SECONDS = BUILDER.comment(
                        "After a checkpoint flips, how long, in seconds, the side that just lost it is blocked from immediately recapturing it.")
                .defineInRange("checkpointLockout", 15, 0, Integer.MAX_VALUE);
        CREATIVE_CAN_CAPTURE = BUILDER.comment("If true, players in creative mode count toward capturing a checkpoint.")
                .define("creativeCanCapture", false);
        OCCUPATION_LOCK_DURATION_SECONDS = BUILDER.comment(
                        "How long, in seconds, a city stays locked (uncapturable, no upgrades) immediately after being occupied "
                                + "or after a checkpoint transfer at settlement.")
                .defineInRange("occupationLockDuration", 3600, 0, Integer.MAX_VALUE);
        OCCUPATION_SUSPENDS_CLAIM_PROTECTION = BUILDER.comment(
                        "If true, occupying a city also suspends the owner's OPAC claim protection inside it, beyond the normal "
                                + "war-time override.")
                .define("occupationSuspendsClaimProtection", false);
        BUILDER.pop();

        BUILDER.push("activity");
        LOGIN_SHIELD_DURATION_SECONDS = BUILDER.comment(
                        "How long, in seconds, after logging in a player is shielded: not counted as war-ready, but still able to fight and be killed.")
                .defineInRange("loginShieldDuration", 180, 0, Integer.MAX_VALUE);
        AFK_THRESHOLD_SECONDS = BUILDER.comment("How long, in seconds, without tracked activity before a player is marked AFK.")
                .defineInRange("afkThreshold", 300, 0, Integer.MAX_VALUE);
        ACTIVITY_MOVE_THRESHOLD = BUILDER.comment(
                        "Minimum squared movement distance, in blocks, that counts as activity for the AFK timer. "
                                + "Filters out drift from being pushed by pistons/water/entities.")
                .defineInRange("activityMoveThreshold", 0.05, 0.0, 1024.0);
        AFK_EXIT_SHIELD_SECONDS = BUILDER.comment("Extra shield time, in seconds, granted when a player returns from AFK to READY.")
                .defineInRange("afkExitShieldSeconds", 0, 0, Integer.MAX_VALUE);
        LOGIN_SHIELD_GRANTS_INVULNERABILITY = BUILDER.comment(
                        "If true, shielded players cannot take damage. Left off by default: enabling it lets a nation cycle "
                                + "logins to park unkillable bodies on a contested checkpoint.")
                .define("loginShieldGrantsInvulnerability", false);
        COMBAT_LOG_KILL = BUILDER.comment(
                        "If true, disconnecting while combat-tagged kills the player immediately and drops their inventory. "
                                + "Disabling this is the only supported way to turn off combat-log kills.")
                .define("combatLogKill", true);
        COMBAT_TAG_DURATION_SECONDS = BUILDER.comment(
                        "How long, in seconds, a player stays combat-tagged after dealing/receiving player damage or leaving a capture zone.")
                .defineInRange("combatTagDuration", 20, 0, Integer.MAX_VALUE);
        COMBAT_LOG_GRACE_ON_SERVER_STOP = BUILDER.comment(
                        "If true, disconnects caused by the server shutting down are exempt from the combat-log kill, "
                                + "since that case can be identified reliably and a crash/rage-quit cannot.")
                .define("combatLogGraceOnServerStop", true);
        BUILDER.pop();

        BUILDER.push("war");
        WAR_PREP_DURATION_SECONDS = BUILDER.comment("How long, in seconds, a war sits in PREPARATION before capture becomes possible.")
                .defineInRange("warPrepDuration", 21600, 0, Integer.MAX_VALUE);
        WAR_DURATION_SECONDS = BUILDER.comment("Default total wall-clock lifetime of a war, in seconds, before it times out into settlement.")
                .defineInRange("warDuration", 604800, 1, Integer.MAX_VALUE);
        WAR_DURATION_MAX_SECONDS = BUILDER.comment("Upper bound, in seconds, staff may extend a war's duration to.")
                .defineInRange("warDurationMax", 2592000, 1, Integer.MAX_VALUE);
        PRESENCE_GRACE_DURATION_SECONDS = BUILDER.comment(
                        "How long, in seconds, a side may have zero Ready players before the war suspends.")
                .defineInRange("presenceGraceDuration", 180, 0, Integer.MAX_VALUE);
        WAR_EVASION_LIMIT_SECONDS = BUILDER.comment(
                        "Total accrued evasion time, in seconds, that triggers an automatic surrender for a nation dodging a live opponent.")
                .defineInRange("warEvasionLimit", 259200, 1, Integer.MAX_VALUE);
        WAR_PARTICIPATION_MINIMUM_SECONDS = BUILDER.comment(
                        "Cumulative Ready time, in seconds, a nation's citizens must field in one war to reset its evasion clock.")
                .defineInRange("warParticipationMinimum", 3600, 0, Integer.MAX_VALUE);
        EVASION_APPLIES_TO_ATTACKERS = BUILDER.comment("If true, the evasion-surrender clock also applies to nations on the attacking side.")
                .define("evasionAppliesToAttackers", true);
        MAX_CONCURRENT_WARS = BUILDER.comment("Maximum number of unsettled wars a single nation may be a belligerent in at once.")
                .defineInRange("maxConcurrentWars", 50, 1, Integer.MAX_VALUE);
        DEFAULT_POST_WAR_COOLDOWN_HOURS = BUILDER.comment(
                        "Hours a defeated nation must wait before the same nation can declare war on it again.")
                .defineInRange("defaultPostWarCooldownHours", 168, 0, Integer.MAX_VALUE);
        ALLIANCE_CASCADE_DEPTH = BUILDER.comment(
                        "How many hops of mutual alliance a declaration pulls in as defenders. 1 means only direct allies of the "
                                + "target join; raising it can drag an entire alliance network into one war.")
                .defineInRange("allianceCascadeDepth", 1, 0, Integer.MAX_VALUE);
        ALLOW_COUNTER_OFFENSIVE = BUILDER.comment("If false, the counteroffensive command is disabled entirely and wars stay one-directional.")
                .define("allowCounterOffensive", true);
        COUNTER_OFFENSIVE_SCORE_RATIO = BUILDER.comment(
                        "Minimum ratio of defender war score to attacker war score required before the defender can counteroffensive.")
                .defineInRange("counterOffensiveScoreRatio", 1.0, 0.0, 1000.0);
        COUNTER_OFFENSIVE_MIN_DURATION_SECONDS = BUILDER.comment(
                        "Minimum time, in seconds, a war must have been ACTIVE before a counteroffensive can be declared.")
                .defineInRange("counterOffensiveMinDuration", 86400, 0, Integer.MAX_VALUE);
        COUNTER_OFFENSIVE_PREP_SECONDS = BUILDER.comment(
                        "Grace period, in seconds, before the original attacker's cities become capturable after a counteroffensive starts.")
                .defineInRange("counterOffensivePrep", 21600, 0, Integer.MAX_VALUE);
        PENDING_ENTRY_EXPIRY_SECONDS = BUILDER.comment(
                        "How long, in seconds, an ally may sit pending (nobody has logged in yet) before being dropped from the war "
                                + "without owing anything. 0 means use the same value as warDuration.")
                .defineInRange("pendingEntryExpiry", 0, 0, Integer.MAX_VALUE);
        WAR_WINDOW_START = BUILDER.comment(
                        "If set, restricts new war declarations to a time-of-day window (paired with warWindowEnd). Empty disables the restriction.")
                .define("warWindowStart", "");
        WAR_WINDOW_END = BUILDER.comment("End of the war declaration window; see warWindowStart.").define("warWindowEnd", "");
        BUILDER.pop();

        BUILDER.push("settlement");
        SETTLEMENT_WINDOW_SECONDS = BUILDER.comment(
                        "How long, in seconds, belligerents have to settle a war before the default outcome (every occupied city "
                                + "transfers to its occupier) applies automatically and the lock lifts. 0 makes the lock indefinite.")
                .defineInRange("settlementWindow", 1209600, 0, Integer.MAX_VALUE);
        SETTLEMENT_LOCK_SCOPE = BUILDER.comment("How broadly the settlement lock restricts a belligerent nation.")
                .define("settlementLockScope", "FULL");
        OFFER_EXPIRY_SECONDS = BUILDER.comment("How long, in seconds, a peace offer waits in the recipient's inbox before expiring unanswered.")
                .defineInRange("offerExpiry", 172800, 1, Integer.MAX_VALUE);
        DEADLOCK_THRESHOLD_SECONDS = BUILDER.comment(
                        "How long, in seconds, a settlement may stay open with no agreement before it is flagged for staff attention.")
                .defineInRange("deadlockThreshold", 604800, 1, Integer.MAX_VALUE);
        DEADLOCK_REJECTIONS = BUILDER.comment("Number of rejected offers in one settlement before it is flagged for staff attention.")
                .defineInRange("deadlockRejections", 3, 1, Integer.MAX_VALUE);
        SCORE_CHECKPOINT_CAPTURE = BUILDER.comment("War score awarded to a nation for capturing an enemy checkpoint.")
                .defineInRange("scoreCheckpointCapture", 10, 0, Integer.MAX_VALUE);
        SCORE_CHECKPOINT_DEFENDED = BUILDER.comment("War score awarded to a nation for retaking one of its own checkpoints.")
                .defineInRange("scoreCheckpointDefended", 5, 0, Integer.MAX_VALUE);
        SCORE_CITY_OCCUPIED = BUILDER.comment("War score awarded the first time a city is occupied in a given war.")
                .defineInRange("scoreCityOccupied", 100, 0, Integer.MAX_VALUE);
        SCORE_CITY_HELD = BUILDER.comment("War score awarded to a defender for holding a targeted city to the war's end.")
                .defineInRange("scoreCityHeld", 50, 0, Integer.MAX_VALUE);
        SCORE_PARTICIPATION_PER_10_MIN = BUILDER.comment("War score awarded per 10 minutes of Ready participation while a war is ACTIVE.")
                .defineInRange("scoreParticipationPer10Min", 1, 0, Integer.MAX_VALUE);
        CITY_VALUE_TIER_WEIGHT = BUILDER.comment(
                        "Weight applied to a city's tier cost when pricing it in war score for a TransferCity settlement clause.")
                .defineInRange("cityValueTierWeight", 1.0, 0.0, 1000.0);
        CITY_VALUE_BANK_WEIGHT = BUILDER.comment("Weight applied to a city's banked payment when pricing it in war score.")
                .defineInRange("cityValueBankWeight", 0.5, 0.0, 1000.0);
        CITY_VALUE_CHECKPOINT_WEIGHT = BUILDER.comment("Weight applied to a city's checkpoint count when pricing it in war score.")
                .defineInRange("cityValueCheckpointWeight", 10.0, 0.0, 1000.0);
        BUILDER.pop();

        BUILDER.push("territory");
        CHECKPOINT_CLAIM_SHAPE = BUILDER.comment("Chunk claim shape projected by each checkpoint: PLUS, SINGLE, SQUARE, or NONE.")
                .define("checkpointClaimShape", "PLUS");
        CITY_CORE_CLAIM_SHAPE = BUILDER.comment("Chunk claim shape projected by the City Core itself.").define("cityCoreClaimShape", "PLUS");
        SYNC_CLAIMS = BUILDER.comment("If true, the mod keeps OPAC claims in sync with checkpoint/core placement automatically.")
                .define("syncClaims", true);
        RELEASE_CLAIMS_ON_DISBAND = BUILDER.comment("If true, disbanding a city releases all of its claimed chunks.")
                .define("releaseClaimsOnDisband", true);
        WAR_PROTECTION_OVERRIDE = BUILDER.comment(
                        "Which actions bypass OPAC claim protection between opposing belligerents while a war is ACTIVE and only "
                                + "inside the targeted city's territory. Options: blockBreak, blockPlace, pvp, explosions, fireSpread, "
                                + "containerAccess, entityDamage.")
                .defineList("warProtectionOverride", List.of("blockBreak", "blockPlace", "pvp", "explosions"), o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("logging");
        LOGGING_DEFAULT = BUILDER.comment("Fallback log level for any subsystem not listed in logging.categories.").define("default", "INFO");
        LOGGING_CATEGORIES = BUILDER.comment(
                        "Per-subsystem diagnostic log level, as \"<category>=<level>\", so a specific bug can be chased at DEBUG "
                                + "without drowning the log in everything else.")
                .defineList("categories", List.of(
                        "capture=INFO", "war=INFO", "claims=INFO", "threading=WARN",
                        "audit=INFO", "config=INFO", "protection=INFO", "persistence=INFO"), o -> o instanceof String);
        LOG_TO_SERVER_CONSOLE = BUILDER.comment("Minimum level from the mod's log that is also echoed to the main server console.")
                .define("logToServerConsole", "WARN");
        LOG_FILE_SIZE_MB = BUILDER.comment("Size, in megabytes, at which the diagnostic log file rolls over.")
                .defineInRange("logFileSizeMb", 32, 1, Integer.MAX_VALUE);
        LOG_FILE_HISTORY = BUILDER.comment("Number of rolled-over diagnostic log archives to keep before deleting the oldest.")
                .defineInRange("logFileHistory", 14, 0, Integer.MAX_VALUE);
        SLOW_TASK_THRESHOLD_MS = BUILDER.comment("A worker task taking longer than this, in milliseconds, is logged as slow.")
                .defineInRange("slowTaskThresholdMs", 50, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("infrastructure");
        WORKER_THREADS = BUILDER.comment("Size of the off-main-thread worker pool. 0 auto-sizes it from the number of available processors.")
                .defineInRange("workerThreads", 0, 0, Integer.MAX_VALUE);
        WORKER_QUEUE_CAPACITY = BUILDER.comment(
                        "Maximum queued worker tasks before the pool falls back to running new tasks synchronously on the calling thread.")
                .defineInRange("workerQueueCapacity", 512, 1, Integer.MAX_VALUE);
        LOCK_STRIPES = BUILDER.comment("Number of striped locks used to guard cross-record updates (e.g. one city and one war at once).")
                .defineInRange("lockStripes", 64, 1, Integer.MAX_VALUE);
        AUDIT_RETENTION_DAYS = BUILDER.comment("How many days of audit log entries are kept on disk before being deleted.")
                .defineInRange("auditRetentionDays", 90, 1, Integer.MAX_VALUE);
        AUDIT_REVERT_WINDOW_DAYS = BUILDER.comment("How many days back an audit entry can still be reverted by staff.")
                .defineInRange("auditRevertWindowDays", 30, 1, Integer.MAX_VALUE);
        NATION_VALIDATION_INTERVAL_SECONDS = BUILDER.comment(
                        "How often, in seconds, the mod re-checks nation/city invariants (party still exists, claims match the "
                                + "current leader, sky columns still clear, evasion clocks correct) and repairs anything wrong.")
                .defineInRange("nationValidationInterval", 300, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private NationWarsConfig()
    {
    }

    public static volatile List<TierDefinition> tiers = List.of();
    public static volatile List<PaymentEntry> paymentValues = List.of();
    public static volatile Map<String, String> loggingCategories = Map.of();
    public static volatile int effectiveMinCoreDistance;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        final List<TierDefinition> parsedTiers = TierListParser.parse(TIERS.get());
        TierValidation.validateLadder(parsedTiers);
        TierValidation.validateSpacingFeasibility(parsedTiers, MIN_CHECKPOINT_SPACING.get());
        final int clampedMinCoreDistance = TierValidation.clampMinCoreDistance(MIN_CORE_DISTANCE.get(), parsedTiers, LOGGER::warn);

        tiers = parsedTiers;
        paymentValues = PaymentListParser.parse(PAYMENT_VALUES.get());
        loggingCategories = LogCategoryListParser.parse(LOGGING_CATEGORIES.get());
        effectiveMinCoreDistance = clampedMinCoreDistance;

        LOGGER.info("nationwars config loaded: {} tiers, {} priced items/tags, minCoreDistance={}",
                tiers.size(), paymentValues.size(), effectiveMinCoreDistance);
    }
}
