# Nations & Checkpoints — Implementation Plan

Derived from [`docs/warfront-spec.md`](warfront-spec.md) (v1.4) and its milestone map (§24). Each stage below is a
concrete engineering slice, strictly dependent on the ones before it — no stage should start until the previous
stage's "Done when" is met. Stage numbers map loosely to spec milestones (M0–M11) but are split finer so each is
independently reviewable (roughly one PR per stage).

Spec section references (`§n`) point at the authoritative behavior; this plan does not restate rules that are
already precisely specified there.

---

## Stage 1 — Config framework (§ Appendix A)

Replace the template `Config.java` with the real `ModConfigSpec` (`Type.SERVER`), covering every key in Appendix A,
grouped into sections matching the appendix (placement/cities, tiers/payment, ranks, capture, activity/combat, war,
settlement, territory, logging, infrastructure). Implement the tier-list and payment-value config objects as
`ConfigValue<List<? extends String>>`/custom parsing, not yet wired to any consumer.

Include the config-load validators specified inline in the spec: tier ladder relation (§10.2, §9.3), spacing
feasibility per tier (§9.3), `minCoreDistance` vs. max tier radius (§8.3). Validation failures refuse to start with
an error naming the offending tier, per spec.

**Done when:** `nationwars-server.toml` generates with every Appendix A key at its documented default; malformed
tier/spacing config refuses server start with a clear error; unit tests cover each validator with a passing and a
failing config.

## Stage 2 — Registries, package skeleton, and mod bootstrap (§3, §24 M0)

Establish `org.pixelfire.nationwars` package structure (`world`, `state`, `compute`, `io`, `net`, `command`, `config`
mirroring the architecture layers in §4.1). Replace template `NationWarsMod.java`: register deferred registers for
blocks, items, block entities, menu types (empty for now), and the `nationwars:peace_clause` registry stub (§15.2).

Resolve the OPAC API on `FMLCommonSetupEvent` per §3.1/§3; throw a descriptive `IllegalStateException` if
unavailable. Add the OPAC dependency to `mods.toml` / `build.gradle` per §3.

**Done when:** mod loads in `runServer` with OPAC present and fails fast with a clear message when it is absent;
package skeleton exists with no business logic yet.

## Stage 3 — Threading foundation: worker pool, striped locks, state registry shell (§4.2–4.3, §24 M0)

Implement `NationRegistry` holding empty `ConcurrentHashMap`s for the record types defined in §5 (created as bare
immutable records with only id fields for now — full fields land per-feature in later stages). Implement the
striped-lock utility (`lockStripes`, ordered-by-UUID acquisition to avoid deadlock) and the global write lock used
for rare multi-record transactions (§4.2).

Implement the worker `ExecutorService` per §4.3 exactly as specified (size formula, bounded queue with
caller-runs fallback, per-task exception wrapping, results marshalled back via `server.execute(...)`). Add a
`WorkerTaskTest` proving no result path calls `.get()`/`.join()` on the main thread.

**Done when:** worker pool starts/stops with the server; a synthetic task submitted from a unit test completes and
its result is observed to run via `server.execute`; striped-lock ordering is unit-tested against a deliberately
reversed-UUID pair to confirm no deadlock.

## Stage 4 — I/O layer: dedicated writer thread and persistence skeleton (§17.4, §18, §24 M0)

Implement the single dedicated writer thread with its bounded queue and synchronous-mode fallback on saturation
(§17.4 storage rules apply to both audit and persistence, so build the shared writer primitive here). Implement
`NationWarsSavedData` skeleton attached to the Overworld `ServerLevel` (§18) with schema version field and an empty
migration chain, but no real fields yet — just enough to prove attach/save/load round-trips.

**Done when:** a dummy payload survives a server save/reload cycle through the writer thread; saturating the queue
intentionally in a test falls back to synchronous writes without dropping the payload.

## Stage 5 — Diagnostic logging (§17.5–17.6, §24 M1)

Register the `nationwars` Log4j2 logger programmatically at mod construction (rolling file appender,
`logFileSizeMb`/`logFileHistory`, `additivity=false`, `logToServerConsole` threshold). Wire per-category levels
(§17.6 table) from config. Implement `/nationwars staff loglevel <category> <level>` as the first command in the
mod, gated by the `nationwars.staff.config` permission node stub (full permission system lands in Stage 19, but the
node check itself is trivial and this command needs it now).

**Done when:** `logs/nationwars/nationwars.log` rolls per config and is absent from `latest.log`; changing a
category level live via the command changes verbosity without a restart.

## Stage 6 — Audit log entry format and writer (§17.1–17.4, §24 M1)

Define the `AuditEntry` record (§17.1 fields) and the registry-driven `actionType` id scheme. Implement append-only
daily gzipped JSONL writing through the Stage 4 writer primitive, plus the in-memory index (entry id, actor,
targets, timestamp) rebuilt off-thread on startup from the retention window, with a "still indexing" response for
early queries.

Implement query commands only (`/nationwars staff log player|city|nation|war|show`); reversal itself is deferred to
Stage 20, since nothing reversible exists yet. No feature writes real entries yet — this stage proves the pipe with
synthetic entries.

**Done when:** a synthetic audit entry survives a restart and is queryable by actor/target/time; querying before the
index finishes rebuilding returns the documented notice instead of partial results.

## Stage 7 — City Core and Checkpoint blocks, no gameplay yet (§6, §24 M2)

Implement `nationwars:city_core` and `nationwars:checkpoint` blocks and their block entities (`cityId` /
`checkpointId` + cached `cityId`), with the indestructibility rules for the core (§6.1: immune to mining, creative,
explosions, pistons, every removal vector) and default vanilla breakability for the checkpoint (state-gated behavior
lands in Stage 10). Datapack recipes per §6.1/§6.2 defaults. No City/Checkpoint records exist in the registry yet —
these blocks are inert placeholders (§21.13: a block entity with no matching city record is inert).

**Done when:** both blocks place and render placeholder textures; the core cannot be removed by any vector tested
(pickaxe, creative, TNT, piston) in a manual test pass; checkpoint breaks like a normal block since no city owns it
yet.

## Stage 8 — Sky column rule and OPAC protection-override probe (§7, §16.4, §24 M2)

Implement the sky-column definition, dimension eligibility, and surface-requirement checks (§7.1–7.3) with the
section-shortcut scan (§4.5 step 1) on the main thread and the snapshot-and-analyze off-thread path (§4.5 step 2)
for partially filled sections. Implement column protection (§7.4 prevention: cancel entity/fluid placement and
falling-block entry inside a column) keyed by an O(1) `Map<ChunkPos, List<ColumnRef>>`, even though no columns are
registered yet.

Run the OPAC protection-override probe called out as the milestone gate in §24 M2 and flagged as the top open
question in §23.1: verify empirically against the target OPAC build whether its protection is expressed as
cancellable Forge events (§16.4 implementation) or needs the mixin/journal fallback. Record the finding — this
decides the shape of Stage 15.

**Done when:** a clear column measures as clear and an obstructed one is detected, both verified in a manual test;
the protection-override probe's finding (event-cancellation confirmed or fallback required) is written down before
any later stage depends on it.

## Stage 9 — Founding a city (§8, §24 M2)

Wire the real `City` record (§5.1 full field set) into `NationRegistry`. Implement `/city found` (or the core-place
flow — confirm trigger against spec context; founding preconditions in §8.1 imply a placement action) enforcing all
ten preconditions in order, creating the city at tier 1 `ACTIVE`, registering its sky column (Stage 8), and applying
the founding grace period (§8.2). Snapshot OPAC party data (leader, membership, rank) on the main thread per §3.1
threading rule before any check runs off-thread.

Audit-log founding via Stage 6's pipe (first real, non-synthetic entry the mod writes).

**Done when:** a nation founds a city only when all ten preconditions hold, with the correct precondition named on
each rejection; the city appears in `NationRegistry`; a founding audit entry is queryable.

## Stage 10 — Checkpoint placement, breaking, and moving (§9, §24 M3)

Implement the real `Checkpoint` record (§5.2) and placement preconditions (§9.1, including radius geometry §9.2 and
spacing feasibility §9.3 already validated at config load in Stage 1). Implement plus-shape claim-set computation
(§16.1) as a pure function on the compute layer (§4.4 table), applied via OPAC's `tryToClaim` on the main thread in
one batch (§16.2).

Implement state-gated breaking (§6.2 table: real break on `ACTIVE`/`DORMANT`, refused if it would breach
`minCheckpoints(tier)`) and the break-then-replace move grace (§9.5, `checkpointMoveGrace`). Implement the tier
minimum enforcement making a city `DORMANT` below `minCheckpoints(tier)` past founding grace (§9.4), including the
`dormantCityExpiry` removal path. Audit-log every place/break/move.

**Done when:** placement enforces all eight preconditions; breaking a checkpoint below the tier minimum is refused;
moving within the grace window preserves `checkpointId` and capture history; a city artificially dropped below
minimum becomes `DORMANT` and is warned on citizen login.

## Stage 11 — City GUI and tier upgrades (§10, §24 M4)

Implement `CityCoreMenu`/screen (§10.1) with the payment slot backed by the config-driven, tag-supporting payment
table from Stage 1 (§10.3: runtime-generated `#nationwars:city_upgrade_payment` tag, unpriced items rejected at
insertion). Implement the five upgrade preconditions (§10.4), including the radius-vs-neighbor-claims check, and
surface the "requires N checkpoints — you have M" message in the GUI per spec.

**Done when:** an arbitrary-length tier list from config drives the GUI with no code change; a custom modded ore
priced in config upgrades a city; an upgrade attempt below the checkpoint maximum is refused with the exact message
format from §10.4.

## Stage 12 — Activity, readiness, and combat tagging (§12, §24 M5)

Implement `PlayerActivityData` (§5.7) and the SHIELDED→READY→AFK state machine (§12.1) with the exact activity
definition (§12.2, closing the standard AFK-machine loopholes) and `/afk`. Implement nation/coalition readiness
queries (§12.3) — computed on demand, no coalitions exist yet so this stage only needs the per-nation form; the
coalition form is exercised for real in Stage 14.

Implement `CombatTracker` and combat-log-kill (§12.5): tagging on player damage dealt/received, the
`combatLogGraceOnServerStop` exemption, and the disable switch. Implement the login-shield non-invulnerability
default explicitly, since it is a documented footgun (§12.6).

**Done when:** a player idling with only head rotation goes AFK on schedule; disconnecting mid-combat kills the
player and drops inventory at the disconnect position unless server-stop grace applies; `/afk` works instantly.

## Stage 13 — War declaration and lifecycle skeleton without capture (§14.1–14.3, §14.5 minus counter-offensive, §24 M6 part 1)

Implement `War`/`Coalition` records (§5.3–5.4) and the phase state machine (§14.1) driven purely by timers and
readiness (no capture exists yet, so `ACTIVE`↔`SUSPENDED` transitions are readiness-only). Implement
`/war declare` with all eleven checks (§14.2), `warCooldowns`, `maxConcurrentWars`. Implement suspension rules
(§14.3: `warExpiresAt` never pauses, evasion clock keeps running, generic notification phrasing).

Implement the termination triggers that don't require capture or settlement yet (`warExpiresAt` reached →
`TIMEOUT`; `/war withdraw`; a coalition emptying by disbandment → `VOID`; `/nationwars staff war cancel`) landing in
a `SETTLEMENT`/`ENDED` stub that Stage 17 will make real. `/war status` and `/war list` read-only commands.

**Done when:** a war can be declared, sits in `PREPARATION`, goes `ACTIVE` only when both sides are war-ready, and
suspends/resumes correctly across a simulated log-off; timeout and withdrawal reach a stub terminal phase.

## Stage 14 — Alliances, coalition assembly, and pending entry (§13, §24 M6 part 2)

Implement mutual-alliance detection (§13.1) and coalition assembly at declaration time with `allianceCascadeDepth`
(§13.2), attacker-side voluntary joining via `/war join <warId> attackers` (§13.2 last paragraph, subject to the
same cooldown/lock checks as declaration). Implement `pendingMembers`/`PendingEntry` scheduled entry (§13.4: not
targetable while pending, own `warPrepDuration` window on entry, per-citizen login notification,
`pendingEntryExpiry` drop) and the alliance-break-mid-war no-op (§13.5).

**Done when:** declaring on a nation with a mutual ally at cascade depth 1 pulls that ally in as pending; the ally's
cities are untargetable until a member logs in and clears the shield, at which point it gets its own private prep
window; breaking the alliance mid-war does not remove the ally from the coalition.

## Stage 15 — War protection override (§16.4, §24 M7 part 1)

Using the Stage 8 probe finding, implement `allowed(player, pos, action)` as the pure derived function specified in
§16.4 — never stored, evaluated per event at `EventPriority.LOWEST` with `receiveCanceled = true`, un-cancelling
OPAC's own cancellation when war-sanctioned. If the probe found OPAC does not expose all relevant actions as
cancellable events, implement the override-journal fallback described in the same section instead, scoped
identically (time/space/people/action per the §16.4 scope table) and wired to the `warProtectionOverride` config
list from Stage 1.

This must land before Stage 16 (capture), since capture inside claimed territory is unplayable without it, matching
the milestone note in §24 M7 that both land together.

**Done when:** a manual test shows blockBreak/blockPlace/pvp/explosions allowed between opposing-coalition members
only inside `targetCityIds` claim union, only during `ACTIVE` phase, and snapping back to OPAC's normal protection
the instant the war suspends — with no restore step, i.e. verified across a simulated crash between events.

## Stage 16 — Capture and occupation (§11, §24 M7 part 2)

Implement presence evaluation and progress accrual (§11.2–11.3) on the main-thread capture tick
(`captureTickInterval`), classification by coalition, and the flip logic with `checkpointLockout` (§11.4). Implement
the cosmetic break-and-respawn effect during `UNDER_SIEGE`/`OCCUPIED` (§11.5) as a scheduled block-state restore that
cancels the real `BreakEvent`, sharing the same effect for capture flips. Implement occupation evaluation
(§11.6–11.7: once-per-tick after all checkpoint updates, occupation lock, lock-expiry role reversal) and confirm
§11.8 (inventories/builds/spawns untouched) holds by construction, not by an explicit check.

**Done when:** holding every non-sealed checkpoint of a targeted city occupies it exactly once even when several
flip in the same tick (§21.7); the checkpoint flashes shatter-and-reform in the new holder's color on both capture
and cosmetic break; the occupation lock blocks capture/upgrade/breaking until expiry, after which the defender can
retake.

## Stage 17 — Counter-offensive (§14.6, §24 M6 closing item)

Implement `/war counteroffensive` with its five gating conditions (needs war score aggregation from Stage 18, so
this stage depends on that ordering point — build war-score accrual events now if not already present from capture
in Stage 16, since §15.4's per-event scoring table overlaps capture/occupation events). Implement the two-front
effects: attacker cities added to `targetCityIds`, `counterOffensivePrep` window, voluntary joiners included,
`/war withdraw` routing to `SETTLEMENT` with occupations intact post-counter-offensive.

**Done when:** a defender who reaches zero occupied cities and the score ratio can flip the war to two-front; the
original attacker's cities are uncapturable until their own prep window elapses; `allowCounterOffensive=false`
disables the command outright.

## Stage 18 — Peace settlement pipeline (§15.1–15.4, §15.6, §24 M8 part 1)

Implement the settlement lock (§15.1, including the white-peace guard when nothing was occupied), the
registry-driven clause system (§15.2: `TransferCity`, `ReleaseOccupation`, `Tribute`, `Ceasefire`), surrender terms
(§15.3), war score accrual and the `cityValue` affordability formula (§15.4, aggregated off-thread per §4.4). Implement
atomic settlement application (§15.6) under the global write lock, including claim re-registration for transferred
cities (§16.3) batched into one tick.

**Done when:** `/war surrender` transfers occupied cities and applies a ceasefire atomically; a `TransferCity` clause
the recipient can't afford fails validation naming the shortfall; applying a settlement is a single audit-logged
transaction that either fully commits or fully aborts.

## Stage 19 — Negotiated peace screen and staff finalization (§15.5, §15.7, §24 M8 part 2)

Implement the async, incrementally-ratified negotiation flow (§15.5): dedicated packets with server-held
single-use tokens and hash re-validation, `/war negotiate` command fallback, offer inbox and `offerExpiry`,
deadlock flagging (`deadlockThreshold`/`deadlockRejections`). Implement staff finalization commands (§15.7) and the
`settlementWindow` backstop with its default-outcome auto-apply and 48h countdown warning.

This is the last piece the milestone note in §24 calls mandatory alongside M7/M8 — a war reaching `SETTLEMENT` with
no resolution path deadlocks two coalitions, so this stage must ship before any public playtest.

**Done when:** three simulated leaders reach a ratified deal with no staff command issued; a deliberately abandoned
settlement auto-applies the default outcome at `settlementWindow` expiry; a stale offer against changed live state
voids with an explanation instead of applying silently.

## Stage 20 — Staff tooling: permissions, reversal, and perf reporting (§17.3, §20.2, §22, §24 M9)

Implement the full `PermissionAPI` node set (Appendix B) with op-level fallback (§20.2). Implement audit reversal
(§17.3: single-entry revert, `revert-session`, dependency-graph refusal naming blocking entries,
`auditRevertWindowDays` bound, irreversible-action flagging at write time) — this is the payoff for every audit
entry written since Stage 6. Implement `/nationwars staff perf`/`dump` (§17.7, §22) and the remaining staff commands
from §20.2 not yet built (`city transfer|release|delete|revalidate`, `war extend`, `coalition add|remove`,
`evasion`, `readiness`).

**Done when:** `/nationwars staff revert-session <player> <since>` undoes a simulated compromised-account sequence
in one command, newest-first, correctly refusing/reordering around dependent entries; every permission node in
Appendix B gates its documented command and falls back correctly with no permission mod installed.

## Stage 21 — Client rendering and HUD (§19, §24 M10)

Implement the S2C/C2S packet set (§19.1–19.2) with rate limiting and full server-side re-validation. Implement
rendering (§19.3: core beams, checkpoint banners/rings/chain overlays, shatter-and-reform) and HUD elements (target
cities, occupation badges, war deadline, coalition roster, settlement lock banner, boss bar, shield/AFK/combat-tag
indicators). Implement the vanilla-client fallback (§19.4: every HUD fact reachable via chat commands, channel-
presence guard so absent clients are skipped not kicked).

**Done when:** a modded client shows live capture progress and occupation state with no chat spam; a vanilla client
reaches the same information entirely through `/city info`, `/war status`, `/war negotiate review`.

## Stage 22 — Edge cases, invariant repair, and validation sweep (§5.8, §21, §24 M11 part 1)

Implement the periodic validation sweep (§21.6: party existence, leader-vs-claim-ownership, §5.8 invariants, sky
columns, evasion clocks — planned off-thread, committed and audit-logged on the main thread) and systematically work
through §21's numbered edge cases not already covered incidentally by earlier stages (party disbandment §21.2,
unloaded-chunk lazy decay §21.3, leader changes §21.5, simultaneous captures already covered in Stage 16, dogpiled
cities §21.9, ally-vs-already-lost-city skip §21.15).

**Done when:** every edge case in §21 has either a passing test or a documented manual verification; a deliberately
corrupted save (e.g., a checkpoint pointing at a nonexistent city) is auto-repaired on load with a `WARN` and an
audit entry, per §5.8.

## Stage 23 — Persistence hardening, migrations, and load testing (§18, §24 M11 part 2)

Implement the schema-version migration chain (currently a no-op single version, but the chain mechanism must exist
and be tested with a synthetic v1→v2 migration). Implement restart behavior guarantees (§18.1: capture progress
reset, evasion-clock downtime subtraction via recorded uptime windows, force-save triggers on phase
transitions/occupations/settlements/disbandments). Load-test 50 concurrent wars (`maxConcurrentWars`) against the
main-thread budget in §4.6/§22, using `/nationwars staff perf` output as the pass/fail signal.

**Done when:** a save file survives a full restart with wars, locks, and evasion trackers intact and capture
progress reset to 0; an overnight simulated outage does not accrue evasion time; 50 concurrent wars hold tick time
within the budget documented in §22.

---

## Sequencing rationale

This plan follows the spec's own sequencing notes (§24, final paragraph) with finer granularity:

- **Stages 1–4** (config, skeleton, threading, I/O) must exist before any feature, since retrofitting the threading
  model onto synchronous code is a rewrite, not a patch — stated explicitly in the spec.
- **Stages 5–6** (logging, audit) land before any stateful feature so nothing is retrofitted for auditability later.
- **Stages 7–11** (blocks → founding → checkpoints → GUI/tiers) build the non-war game loop first, since war
  mechanics operate on cities and checkpoints that must already exist.
- **Stage 12** (readiness) must precede **Stage 13** (war declaration), since declaration checks war-readiness.
- **Stage 13** (war skeleton) must precede **Stage 14** (coalitions) and **Stage 15/16** (protection override,
  capture), since none of those make sense without an active war to attach to.
- **Stage 15** (protection override) is ordered immediately before **Stage 16** (capture) because capture inside
  claimed territory is unplayable without it — the spec ships them together in M7.
- **Stage 17** (counter-offensive) depends on war score, which is introduced properly in Stage 18 but needed in
  skeletal form earlier — flagged explicitly in the stage.
- **Stage 18–19** (settlement, negotiation) must ship close together, since a war reaching `SETTLEMENT` with no
  resolution path deadlocks two coalitions — the spec's own hard constraint.
- **Stage 20** (staff tooling / reversal) is deliberately last among gameplay stages because it depends on every
  audited action type that precedes it.
- **Stages 21–23** (client, hardening, persistence/load-testing) close out the milestone list and assume the full
  feature set is already functionally correct.

## Out of scope (v1 non-goals, §1.1)

Economy/taxation, city population simulation, NPC guards, siege weapons, custom party management UI,
cross-dimension cities, PvP combat changes beyond combat logging. None of the 23 stages above should introduce
these.
