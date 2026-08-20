# Nations & Checkpoints — Technical Specification

**Target:** Minecraft 1.20.1 · Minecraft Forge · Java 17
**Hard dependency:** Open Parties and Claims (OPAC) by *thexaero*
**Working mod id:** `nationwars` (placeholder — rename before first release)
**Status:** Draft v1.4 — open questions in §23

### Changes from v1.3

| # | Decision | § |
|---|---|---|
| 1 | **Checkpoint ladder locked to `min(N) = max(N−1)`.** You must fill your current tier before you can buy the next. | 10.2 |
| 2 | **Counter-offensives.** A defender who repels an invasion can turn the war around and put the attacker's cities at stake. | 14.6 |
| 3 | **War protection override.** OPAC protection is suspended between belligerents inside besieged territory, derived from live war state rather than stored — so there is nothing to restore and a crash cannot leave protection off. | 16.4 |
| 4 | **Diagnostic file logging** specified alongside the audit log: dedicated rolling log file, per-subsystem levels. | 17.5 |
| 5 | Ratification simplified to side-level agreement, with staff ratifying only on deadlock. | 15.5 |
| 6 | **No permission-mod dependency.** Forge's `PermissionAPI` with op-level fallback; permission mods integrate if present. | 20.2 |
| 7 | Config reload policy documented — which values are hot-reloadable during playtesting and which need a restart. | Appendix A |

### Changes from v1.2

| # | Decision | § |
|---|---|---|
| 1 | Prose tightened throughout; rationale reduced to one line per rule. | — |
| 2 | **Audit log with staff reversal.** Every privileged action is recorded with before/after state and can be rolled back, individually or as a whole session. | 17 |
| 3 | **Checkpoints claim territory directly** — 5 chunks each, in a plus shape. Radius no longer determines claims. | 16 |
| 4 | Checkpoints are breakable and movable by **any citizen** while the city is `ACTIVE`. | 6.2, 9.5 |
| 5 | During war, breaking a checkpoint is **cosmetic** — the block shatters and respawns. Capture triggers the same effect in the new holder's colours. | 11.5 |
| 6 | Upgrade payment items are **config-defined**, with tag support for modded ores. | 10.3 |
| 7 | Tier 1 allows **up to 5 checkpoints**, minimum 1. Tiers are now a config list of arbitrary length. **No gameplay constant is hardcoded.** | 10.2 |
| 8 | **Coalition wars and alliances.** Declaring on a nation declares on its mutual allies; allies with nobody online enter the war when they next log in. | 13, 14 |
| 9 | **Evasion surrender.** A nation that dodges a war for 3 days auto-surrenders. Qualifying sessions are 1 hour, and the clock only runs while the enemy is present. Replaces the presence truce. | 14.4 |
| 10 | **Combat logging kills.** Disconnecting while in combat kills the player immediately. | 12.5 |
| 11 | "Admin" means **staff** — admins and moderators, via granular permission nodes. | 20.2, Appendix B |
| 12 | **Threading model is a first-class concern**, specified before the features that depend on it. | 4 |
| 13 | `maxConcurrentWars` raised to 50; dogpiling an already-warring nation is permitted. | 14.2 |
| 14 | Allies sit at the negotiating table and spend **war score** to claim spoils. | 15.4 |

---

## 1. Overview

A server-side territorial warfare mod. Player parties (from Open Parties and Claims) are **nations**. Nations found **cities** by placing a City Core block; each city is defended by **checkpoints** — capture-the-flag objectives that also project the city's territorial claims. During a declared **war**, an attacking coalition that holds every checkpoint of a city **occupies** it. Occupied cities change hands only when the war is settled.

Design principles:

1. **No parallel party system.** OPAC is the single source of truth for membership, ownership, ranks and alliances. This mod stores city, checkpoint, war and audit state, keyed by OPAC party UUID.
2. **Server-authoritative.** All state lives on the server; the client renders only.
3. **Conquest is military; ownership is political.** Winning the fight earns occupation. Getting the deed requires a settlement, negotiated by the belligerents themselves.
4. **Wars are campaigns.** A war runs for weeks. Sieges happen in the sessions where both sides are present; the front persists between them.
5. **No offline raiding, no offline evasion.** Capture requires live players on both sides — and a nation that hides from a war it is in loses it.
6. **Griefing is preventable or reversible.** Constraints are enforced by blocking the action; anything that slips through is in the audit log and can be rolled back.
7. **Nothing is hardcoded.** Every gameplay constant is config.

### 1.1 Non-goals (v1)

Economy/taxation, city population simulation, NPC guards, siege weapons, custom party management UI, cross-dimension cities, PvP combat changes beyond combat logging.

---

## 2. Terminology

| Term | Meaning |
|---|---|
| **Nation** | An OPAC party, identified by party UUID. |
| **Nation leader** | The OPAC party owner. |
| **Citizen** | Any member of a party. |
| **Staff** | Server admins and moderators, identified by permission node (§20.2). |
| **City** | A territory anchored by one City Core block, owned by one nation. |
| **Checkpoint** | A capturable objective block belonging to one city; claims 5 chunks. |
| **Tier** | A city's upgrade level, setting radius and checkpoint limits. |
| **Sky column** | The 3×3 column of air above a core or checkpoint, up to build limit. |
| **Shielded** | Within 3 minutes of login. Not Ready. |
| **AFK** | No tracked activity for 5 minutes. Not Ready. |
| **Ready player** | Online, past the login shield, not AFK. |
| **War-ready** | A nation or coalition with at least one Ready player. |
| **Alliance** | A *mutual* OPAC ally relation between two nations. |
| **Coalition** | The set of nations on one side of a war. |
| **Belligerent** | Any nation in either coalition. |
| **Occupied** | A city whose every checkpoint is held by a hostile coalition. Control, not ownership. |
| **Settlement** | The act that transfers cities at the end of a war. |
| **Locked nation** | A nation frozen pending settlement: no wars, no founding, no upgrades, no checkpoint changes. |
| **War score** | Points a nation earns during a war, spent on spoils at settlement. |

---

## 3. Dependencies and environment

```
Minecraft      1.20.1
Forge          47.2.0+   (recommended 47.3.x)
Java           17
OPAC           latest 1.20.1 build; compiled against the shipped API jar
```

```toml
[[dependencies.nationwars]]
    modId = "openpartiesandclaims"
    mandatory = true
    versionRange = "[0.0,)"   # narrow after pinning a tested build
    ordering = "AFTER"
    side = "BOTH"
```

**Fail fast.** Resolve the OPAC API on `FMLCommonSetupEvent` and throw a descriptive `IllegalStateException` if unavailable.

### 3.1 OPAC API surface used

Via `xaero.pac.common.server.api.OpenPACServerAPI.get(MinecraftServer)`.

| Need | Call |
|---|---|
| Nation of a player | `getPartyManager().getPartyByMember(uuid)` → `IServerPartyAPI` (nullable) |
| Nation by id | `getPartyManager().getPartyById(uuid)` |
| Nation leader | `party.getOwner()` → `IPartyMemberAPI` |
| Online citizens | `party.getOnlineMemberStream()` |
| Member count | `party.getMemberCount()` |
| Alliances | `party.isAlly(otherId)`, `party.getAllyPartiesStream()` |
| All nations | `getPartyManager().getAllStream()` |
| Claims | `getServerClaimsManager()` → `IServerClaimsManagerAPI` |

Use `tryToClaim(...)` for voluntary claiming and `claim(...)` for settlement transfers, where bypassing per-player limits is intended.

> ⚠️ Verify signatures against the javadoc of the exact OPAC build. Versioned API packages (`...api` vs `...api.v2`) have changed between releases.

**Threading:** treat the entire OPAC API as main-thread-only. Snapshot what you need into mod-owned records before handing work to a worker (§4).

---

## 4. Architecture and threading model

The mod is built for concurrency from the ground up. The constraint that shapes everything: **Minecraft's world state is single-threaded.** `Level`, `ChunkAccess`, `BlockEntity`, `ServerPlayer` and the OPAC API may only be touched on the main server thread. Everything else can and should move off it.

### 4.1 Layering

| Layer | Thread | Contents |
|---|---|---|
| **World layer** | Main only | Block reads/writes, entity queries, OPAC calls, packet dispatch |
| **State layer** | Any (lock-free reads) | `NationRegistry`: cities, checkpoints, wars, coalitions, nation state |
| **Compute layer** | Worker pool | Claim set computation, war score aggregation, settlement validation, sky column analysis, audit indexing, statistics |
| **I/O layer** | Dedicated single writer | Audit log append, persistence serialization |

Rule: **decisions are made on the main thread; work is done off it.** A worker may compute *what* should happen; only the main thread commits it. This keeps capture, occupation and settlement outcomes deterministic and ordering-independent regardless of pool scheduling.

### 4.2 State layer

* Every record (`City`, `Checkpoint`, `War`, `Coalition`) is an **immutable Java record**. Mutation produces a new instance.
* `NationRegistry` holds `ConcurrentHashMap<UUID, City>` etc. Readers get a consistent record with no lock.
* Cross-record consistency uses **striped locks** keyed by city id and war id — `Striped<Lock>` style, `lockStripes` (default 64). Never hold two stripes without ordering them by UUID to avoid deadlock.
* Multi-record atomic changes (settlement application, city transfer) take a single global write lock. These are rare — measured in events per day, not per tick — so contention is irrelevant and correctness is worth more.

### 4.3 Worker pool

```java
ExecutorService workers = Executors.newFixedThreadPool(
    Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
    threadFactory("nationwars-worker-%d", /*daemon*/ true, Thread.NORM_PRIORITY - 1));
```

* Size is `workerThreads` in config; `0` means the formula above.
* Results return to the main thread via `server.execute(runnable)`.
* **Never block the main thread on a future.** No `.join()`, no `.get()`. If a result is needed synchronously, the task was not a candidate for offloading.
* Bounded queues (`workerQueueCapacity`, default 512) with a caller-runs fallback, so overload degrades into synchronous execution rather than unbounded memory growth.
* Every task is wrapped so an uncaught exception is logged with its originating action rather than silently killing a pool thread.

### 4.4 What runs off-thread

| Task | Why it is safe |
|---|---|
| Claim set computation (§16.1) | Pure function of positions and config |
| War score aggregation (§15.4) | Reads immutable war records |
| Settlement pre-validation (§15.6) | Pure check against a state snapshot |
| Sky column analysis | Operates on a snapshot copied on the main thread (§4.5) |
| Audit serialization and write (§17.4) | Owns its own file handle |
| Persistence serialization | Snapshot taken on main thread, encoded off it |
| Peace deal balance weights | Display-only |
| Evasion and readiness statistics | Derived from tracker snapshots |

### 4.5 Sky column scanning

The naive scan is ~2,900 block reads. Two optimisations, in order:

1. **Section shortcut, main thread.** Test `LevelChunkSection#hasOnlyAir()` per 16-block section. A column above open ground is typically 15+ empty sections, reducing the check to ~20 pointer comparisons. Only partially-filled sections need per-block inspection.
2. **Snapshot and analyse.** For the sections that need it, copy their `PalettedContainer` state into a plain array on the main thread (cheap) and evaluate off-thread.

In practice step 1 alone makes placement validation effectively free, and step 2 only matters for the periodic revalidation sweep across many cities at once.

### 4.6 Main-thread budget

The per-tick main-thread work is deliberately tiny:

* Capture evaluation: one AABB player query per contested checkpoint, every `captureTickInterval` ticks.
* Activity: one position delta per online player.
* Everything else is event-driven or offloaded.

`/nationwars staff perf` reports per-system average and p99 main-thread cost, worker queue depth, and I/O backlog.

---

## 5. Data model

All records immutable; all persisted unless noted.

### 5.1 `City`

| Field | Type | Notes |
|---|---|---|
| `cityId` | `UUID` | Survives ownership changes. |
| `name` | `String` | 3–24 chars, sanitized. |
| `ownerNationId` | `UUID` | Changes only at settlement. |
| `founderNationId` | `UUID` | Immutable. |
| `dimension`, `corePos` | | |
| `tier` | `int` | Index into the config tier list. |
| `bankedPayment` | `long` | §10.3. |
| `checkpointIds` | `Set<UUID>` | |
| `state` | `CityState` | `ACTIVE`, `UNDER_SIEGE`, `OCCUPIED`, `DORMANT`. |
| `occupiedByNationId` | `UUID?` | Non-null iff `OCCUPIED`. |
| `occupiedSince`, `occupationLockUntil` | `long` | §11.6. |
| `foundedAt`, `lastTransferAt`, `transferCount` | | |
| `pendingDisbandAt` | `long` | 0 if none (§6.1). |

### 5.2 `Checkpoint`

| Field | Type | Notes |
|---|---|---|
| `checkpointId`, `cityId` | `UUID` | |
| `dimension`, `pos` | | |
| `holderNationId` | `UUID` | |
| `captureProgress` | `float` | 0–1. |
| `capturingNationId` | `UUID?` | |
| `status` | enum | `HELD`, `CONTESTED`, `CAPTURING`, `FROZEN`, `SEALED`. |
| `claimedChunks` | `Set<ChunkPos>` | Cached plus-shape (§16.1). |
| `lastEvaluatedTime` | `long` | For lazy decay (§21.3). |
| `placedBy`, `placedAt` | | |

### 5.3 `War`

| Field | Type | Notes |
|---|---|---|
| `warId` | `UUID` | |
| `attackers`, `defenders` | `Coalition` | §13.3. |
| `phase` | enum | `PREPARATION`, `ACTIVE`, `SUSPENDED`, `SETTLEMENT`, `ENDED`. |
| `declaredAt`, `activeAt`, `warExpiresAt` | `long` | Deadline is wall-clock and never pauses. |
| `targetCityIds`, `occupiedCityIds` | `Set<UUID>` | |
| `warScore` | `Map<UUID, Long>` | Nation → score (§15.4). |
| `suspendedSince`, `contestedTimeMs` | `long` | |
| `settlementDeadline` | `long` | 0 if backstop disabled. |
| `stagedSettlement`, `appliedSettlement` | `PeaceSettlement?` | |
| `outcome` | enum | §14.5. |

### 5.4 `Coalition`

| Field | Type | Notes |
|---|---|---|
| `members` | `Set<UUID>` | Active belligerents. |
| `pendingMembers` | `Map<UUID, PendingEntry>` | Allies awaiting login (§13.4). |
| `primaryNationId` | `UUID` | Declarer, or original target. Leads negotiation. |

`PendingEntry`: `nationId`, `scheduledAt`, `reason` (`ALLY_OF <nation>`).

### 5.5 `PeaceSettlement`

`settlementId`, `warId`, `proposedByNationId?`, `List<Clause> clauses`, `createdAt`, `expiresAt`, `Map<UUID, RatificationState> ratifications`, `status`.

Clause types are **registry-driven** from day one (§15.2) so new ones need no changes to the apply pipeline.

### 5.6 `NationState`

`nationId`, `cityIds`, `capitalCityId`, `activeWarIds`, `warCooldowns` (opponent → earliest re-declaration), `lastCityFoundedAt`, `lockedByWarId?`.

### 5.7 Transient trackers (not persisted)

* `PlayerActivityData` — `loginTick`, `shieldExpiresTick`, `lastActivityTick`, `state`, `manualAfk`.
* `CombatTracker` — `combatTagExpiresTick`, `lastAttackerId`.
* `EvasionTracker` — persisted per (war, nation): `evasionAccruedMs`, `qualifyingReadyMs`, `lastWarnedAt`.

### 5.8 Invariants

Validated on load, auto-repaired with a `WARN`, and every repair is written to the audit log:

1. A checkpoint belongs to exactly one city; the city knows all its checkpoints.
2. A city has one core at `corePos` whose block entity carries the same `cityId`.
3. `minCheckpoints(tier) ≤ checkpointIds.size() ≤ maxCheckpoints(tier)` past the founding grace.
4. Every checkpoint is within the tier radius of its core.
5. No two cores are closer than `minCoreDistance`.
6. `ownerNationId` resolves to a live party, or the city is `DORMANT`.
7. `OCCUPIED` ⟺ `occupiedByNationId != null` and an unsettled war references the city.
8. A nation appears in at most one coalition per war.

---

## 6. Blocks and items

### 6.1 City Core (`nationwars:city_core`)

* Full block, light 10, `PushReaction.BLOCK`.
* `CityCoreBlockEntity` stores `cityId`.
* Beacon-style beam in the owner's colour; a second offset beam in the occupier's colour while occupied.
* Right-click opens the City GUI (§10).

**Breaking.** The core cannot be mined. It is indestructible in every state, to every player, including creative mode, and immune to explosions, pistons and every other block-removal vector.

Dissolution is by command only:

| | |
|---|---|
| Command | `/city disband <name> confirm` |
| Permitted actor | Nation leader (OPAC party owner) only — not `MODERATOR`, not build permission |
| Allowed state | `ACTIVE` only |
| Delay | `cityDisbandDelay` (default 5 min), broadcast countdown, cancellable via `/city disband <name> cancel` |
| Announcement | Server-wide, at issue and at completion |
| Audit | Logged and revertible (§17) |

Build permission is granted routinely and broadly; party ownership is not. Gating dissolution on ownership bounds the damage from a mistaken permission grant, and the delay plus audit log covers the compromised-leader case.

**Recipe:** datapack-defined; default 1 beacon, 4 gold blocks, 1 nether star, 3 obsidian.

### 6.2 Checkpoint (`nationwars:checkpoint`)

* Full block, `PushReaction.BLOCK`.
* `CheckpointBlockEntity` stores `checkpointId` and cached `cityId`.
* Renders a banner in the holder's colour; capture ring when contested.
* Claims 5 chunks (§16.1).

**Breaking depends on city state:**

| City state | Behaviour |
|---|---|
| `ACTIVE` | Real break by **any citizen** of the owning nation. Drops the item, releases claims, audit-logged. Refused if it would breach `minCheckpoints(tier)`. |
| `UNDER_SIEGE`, `OCCUPIED` | **Cosmetic only** (§11.5). Block shatters and respawns; no drop, no state change. |
| `DORMANT` | Real break, as `ACTIVE`. |

**Recipe:** datapack-defined; default 1 banner, 2 iron blocks, 1 ender pearl, 4 stone bricks.

---

## 7. The sky column rule

### 7.1 Definition

For a block at `p`, the sky column is
`{(x,y,z) : |x−p.x| ≤ 1, |z−p.z| ≤ 1, p.y < y < level.getMaxBuildHeight()}`

Clear iff every position is `isAir()` (`AIR`, `CAVE_AIR`, `VOID_AIR`). Fluids, leaves, glass, light blocks and torches are obstructions. Entities are not. Scanning strategy in §4.5.

### 7.2 Dimension eligibility

Eligible iff `hasSkyLight()`, `!hasCeiling()`, and passing the `allowedDimensions`/`blockedDimensions` filter. Default Overworld only; the Nether is structurally excluded by its ceiling.

### 7.3 Surface requirement

With `requireSurfacePlacement` (default true):

```
p.y ≥ level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.x, p.z) − surfaceTolerance
```

`surfaceTolerance` default 4. Blocks 3×3 shafts to bedrock while allowing terraforming.

### 7.4 Keeping the column clear

**Prevention (primary).** Each column is a protected volume:

* `BlockEvent.EntityPlaceEvent` / `EntityMultiPlaceEvent` cancelled inside a column, for all players and mob placements.
* `FluidPlaceBlockEvent` and fluid spread cancelled.
* `FallingBlockEntity` entering a column is removed and drops its item at the boundary.

Lookup is O(1) via `Map<ChunkPos, List<ColumnRef>>`, so the handler exits immediately for placements nowhere near a column.

**Validation (secondary).** `/setblock` and world-edit tools bypass prevention. Re-scan on chunk load and every `columnRevalidateInterval` (default 600 s), off-thread per §4.5. An obstructed checkpoint becomes `SEALED`; an obstructed core makes its city `DORMANT`.

### 7.5 Sealed checkpoints

`SEALED` does not remove a checkpoint from the capture requirement — that would let either side manipulate the win condition.

* No progress gained or lost.
* A city with any `SEALED` checkpoint cannot be occupied.
* Announced to both nations with coordinates; it is an abuse signal, not a tactic.
* Returns to `HELD` 30 s after the obstruction clears.

---

## 8. Founding a city

### 8.1 Preconditions

1. The player is in a nation — nationless players cannot found cities.
2. Rank meets `cityFoundRank` (default `MEMBER`).
3. Dimension eligible (§7.2).
4. Sky column clear; surface requirement met.
5. No core within `minCoreDistance` (default 192).
6. Nation below `maxCitiesPerNation` and `maxCitiesPerMember` × member count.
7. `now − lastCityFoundedAt ≥ cityFoundCooldown`.
8. Target chunk unclaimed, or claimed by this nation.
9. Nation not locked pending settlement (§15.3).
10. Nation not in an unsettled war, if `allowFoundingDuringWar` is false.

### 8.2 Effects

Create the city at tier 1, `ACTIVE`; register the sky column; claim the core's chunk set (§16.1); notify the nation; audit-log. The **founding grace period** (default 15 min) exempts the city from war targeting and the checkpoint minimum.

### 8.3 Spacing validation

`minCoreDistance` must exceed `2 × maxTierRadius + 8`. Validated at config load and clamped with a `WARN`.

---

## 9. Placing, moving and breaking checkpoints

### 9.1 Placement preconditions

1. Player is a citizen of the city's owning nation (or an ally if `alliesCanPlaceCheckpoints`).
2. Rank meets `checkpointPlaceRank` (default `MEMBER`).
3. Within the tier radius of exactly one core.
4. City is `ACTIVE`.
5. Sky column clear; surface requirement met.
6. City below `maxCheckpoints(tier)`.
7. At least `minCheckpointSpacing` from other checkpoints and `minCoreClearance` from the core.
8. **All 5 claim chunks (§16.1) are free** — unclaimed or already this nation's.

### 9.2 Radius geometry

Horizontal Euclidean distance, unbounded in Y:

```
inRange = (dx*dx + dz*dz) <= r*r
```

### 9.3 Spacing feasibility

Radius, spacing and checkpoint count are geometrically coupled. Points on the boundary circle need
`spacing ≤ 2r·sin(π/n)`; at the tier-1 defaults (r=5, n=5) that caps spacing at 5.87, so `minCheckpointSpacing` defaults to 3.

Validate at config load: for each tier, assert `maxCheckpoints` is placeable given `radius` and `minCheckpointSpacing`, and refuse to start with an explicit error naming the offending tier. Misconfiguration here produces a city that can never reach its own maximum, which is confusing to diagnose in play.

### 9.4 Minimum

A city below `minCheckpoints(tier)` past its founding grace becomes `DORMANT`: not war-targetable, warned on citizen login, removed after `dormantCityExpiry` (default 7 days) with the core dropped.

### 9.5 Breaking and moving

While the city is `ACTIVE`, **any citizen** may break a checkpoint. It drops as an item, its claims are released, and the action is audit-logged with the position and claim set so a bad break can be reverted exactly (§17).

There is no separate move operation — break and re-place is a move, subject to the placement rules above. `checkpointMoveGrace` (default 60 s) preserves the `checkpointId` and its capture history if the same player re-places within the window, so a routine relocation does not read as a delete plus a create in the audit log.

Breaking is refused if it would breach the tier minimum, or if the city is not `ACTIVE`.

---

## 10. City GUI and tier upgrades

### 10.1 Menu

`CityCoreMenu extends AbstractContainerMenu`, opened with `NetworkHooks.openScreen`. One payment slot; `ContainerData` syncs tier, banked payment, checkpoint count, city state, occupation countdown. Tier selection and confirmation use `clickMenuButton`, so vanilla menu validation applies.

Open: citizens of the owning nation. Confirm an upgrade: `cityUpgradeRank` (default `MODERATOR`).

### 10.2 Tiers are config

Tiers are a list of arbitrary length. Nothing in the code assumes four.

```toml
[[tiers]]
radius = 5
cost = 0
minCheckpoints = 1
maxCheckpoints = 5

[[tiers]]
radius = 8
cost = 128
minCheckpoints = 5
maxCheckpoints = 8

[[tiers]]
radius = 13
cost = 512
minCheckpoints = 8
maxCheckpoints = 13

[[tiers]]
radius = 21
cost = 2048
minCheckpoints = 13
maxCheckpoints = 21
```

| Tier | Radius | Cost | Min | Max |
|---:|---:|---:|---:|---:|
| 1 | 5 | — | 1 | 5 |
| 2 | 8 | 128 | 5 | 8 |
| 3 | 13 | 512 | 8 | 13 |
| 4 | 21 | 2048 | 13 | 21 |

**`minCheckpoints(N) = maxCheckpoints(N−1)`.** Fill your current tier before you can buy the next: a tier-2 city must hold all 5 of tier 1's checkpoints, a tier-3 city all 8 of tier 2's. Growth is earned by consolidation rather than bought outright, and there is no way to hold maximum reach behind a thin screen of flags.

Radii and maximums are consecutive Fibonacci numbers. Validate the ladder relation at config load and refuse to start if a tier's minimum exceeds the previous tier's maximum — that configuration produces an unreachable tier.

### 10.3 Payment values are config

Payment accumulates: each inserted item is consumed and adds its value to `bankedPayment`. Values are config, accepting item ids and tags so custom ores work without a code or datapack change.

```toml
[payments]
# "<item-or-tag>=<value>"; tags take the `#` prefix and are matched after exact ids
values = [
  "minecraft:iron_ingot=1",
  "minecraft:gold_ingot=3",
  "minecraft:emerald=6",
  "minecraft:diamond=9",
  "minecraft:netherite_ingot=36",
  "#forge:ingots/copper=1",
  "#forge:gems/ruby=12",
]
blockMultiplier = 9        # applied to the block form of any listed ingot/gem tag
```

The accepted-item tag `#nationwars:city_upgrade_payment` is generated at runtime from this list, so the slot only accepts what the config prices. An unpriced item is rejected at insertion rather than silently consumed for zero.

Banked payment is never refunded, transfers with the city at settlement, and is the pool tribute clauses draw from.

### 10.4 Upgrade preconditions

1. City `ACTIVE`.
2. `bankedPayment ≥ cost(tier+1)`.
3. City is **at its current tier's maximum** — which is the next tier's minimum (§10.2).
4. Expanded radius does not come within `minCheckpointSpacing` of another city's checkpoints.
5. Nation not locked, and not at war if `allowUpgradeDuringWar` is false.

The GUI states requirement 3 explicitly (`Tier 3 requires 8 checkpoints — you have 6`).

---

## 11. Capture and occupation

### 11.1 When capture is possible

* An `ACTIVE` war exists between the checkpoint's owning coalition and the capturing coalition.
* The city is in `targetCityIds`.
* The checkpoint is neither `SEALED` nor `FROZEN`.
* The chunk is loaded (§21.3).

Outside war, checkpoints are inert.

### 11.2 Presence evaluation

Every `captureTickInterval` (default 10 ticks), per eligible checkpoint in a loaded chunk:

1. Query players in the capture zone — a cylinder of `captureRadius` (default 5), `captureZoneHeight` (default 8) above and below.
2. Exclude spectators, creative players (unless `creativeCanCapture`), the dead, and anyone in the zone under 1 s.
3. Classify by coalition: attacker, defender, or neutral.

Shielded and AFK players count in capture zones — the shield governs war eligibility, not combat.

### 11.3 Progress

```
attackerWeight = clamp(1 + attackerStackBonus × (attackers − 1), 0, attackerStackCap)
```

| Situation | Effect |
|---|---|
| attackers > 0, defenders = 0 | `progress += baseCaptureRate × attackerWeight × dt` |
| attackers > 0, defenders > 0 | frozen; `CONTESTED` |
| attackers = 0, defenders > 0 | `progress -= defenderRecoveryRate × dt` |
| attackers = 0, defenders = 0 | `progress -= decayRate × dt` |

Defaults: `baseCaptureRate` 1/45 per s, `defenderRecoveryRate` 1/20, `decayRate` 1/90, `attackerStackBonus` 0.5, `attackerStackCap` 3.

### 11.4 Checkpoint flip

At `progress ≥ 1`: set `holderNationId`, reset progress, play the break-and-respawn effect (§11.5), award war score, notify both coalitions with the tally. `checkpointLockout` (default 15 s) blocks immediate re-capture by the previous holder.

### 11.5 Cosmetic breaking during war

While a city is `UNDER_SIEGE` or `OCCUPIED`, breaking a checkpoint block is purely visual:

* The block shatters with particles and the break sound.
* No item drops; the `Checkpoint` record and all capture state are untouched.
* It respawns after `checkpointRespawnDelay` (default 3 s) in the **current holder's** colours.

Capture triggers exactly the same effect automatically. The result is that "the flag shatters and comes back in the enemy's colour" is the universal signal that a checkpoint changed hands — legible from a distance with no HUD, and identical whether a player swung at it or a capture completed. Players who instinctively hit the enemy flag get a satisfying response and change nothing, which is the point.

Server-side, this is a scheduled block-state restore, not a real break: the `BlockEvent.BreakEvent` is cancelled and the effect is replayed to nearby clients. No block entity is destroyed and no claim is released, so it cannot be used to strip territory mid-war.

### 11.6 Occupying a city

Checked once per tick after all checkpoint updates:

> Every checkpoint held by the same hostile coalition, none `SEALED` → the city is occupied.

1. `state = OCCUPIED`, occupier recorded, `occupationLockUntil = now + occupationLockDuration` (default 1 h).
2. All checkpoints `FROZEN`, progress 0.
3. Added to `occupiedCityIds`; war score awarded.
4. Server-wide announcement.
5. If all target cities are occupied, the war moves to `SETTLEMENT`.

**Ownership does not change.** The defender keeps the deed; the attacker holds the ground.

### 11.7 The occupation lock

While locked: nobody can capture the city or its checkpoints; the core and checkpoints cannot be broken; no upgrades. On expiry, if the war is still `ACTIVE`, checkpoints unfreeze and the defender may retake them — roles reverse, and retaking all of them releases the occupation.

Against a month-long war, an hour is a breathing space, not a decision: cities can change hands repeatedly across a campaign. The lock exists to stop a city ping-ponging within one evening's fighting.

### 11.8 What is never affected

Inventories, builds, chests and spawn points. The mod moves territorial control, not property; looting is governed by OPAC claim settings.

---

## 12. Activity, readiness and combat

### 12.1 Player state machine

```
   login
     │
     ▼
┌──────────┐  3 min elapsed   ┌────────┐  5 min no activity  ┌─────┐
│ SHIELDED │─────────────────▶│ READY  │────────────────────▶│ AFK │
└──────────┘                  └────────┘◀────────────────────└─────┘
                                          any tracked activity
```

`lastActivityTick` is initialised to `shieldExpiresTick`, so a player who logs in and stands still goes AFK at login + 8 minutes. Activity during the shield does not shorten it. Returning from AFK restores `READY` immediately.

### 12.2 What counts as activity

Counts: movement beyond `activityMoveThreshold` where the player is not a passenger of a vehicle they do not control; block break/place/interact; attacking or being damaged by a player; container and inventory changes; item use; chat and commands.

Does not count: head rotation alone, being moved by pistons/boats/minecarts/water, item pickup, passive damage. This closes the standard AFK-machine loopholes.

`/afk` marks a player AFK immediately. There is no command to become Ready without playing.

### 12.3 Nation and coalition readiness

```java
boolean ready(IServerPartyAPI p)  { return p.getOnlineMemberStream().anyMatch(this::isReady); }
boolean ready(Coalition c)        { return c.members().stream().anyMatch(this::nationReady); }
```

Computed on demand. Pending coalition members (§13.4) do not count.

### 12.4 Where readiness is used

| Situation | Rule |
|---|---|
| Declaration | Both the primary target and the declaring nation must be war-ready. |
| Ongoing war | `ACTIVE` only while both coalitions are war-ready. |
| Evasion clock | Runs only while the *opposing* coalition is war-ready (§14.4). |
| Negotiation | The proposing or accepting leader must be Ready. |

Readiness is never exposed as a roster; clients see only their own state and a "valid war target" boolean.

### 12.5 Combat logging

A player who disconnects while combat-tagged **dies immediately**: inventory and XP drop at their disconnect position, the death is announced normally, and the event is audit-logged.

Tagged by:

* Dealing or receiving player damage — `combatTagDuration` (default 20 s), refreshed on each hit.
* Standing in a capture zone of an `ACTIVE` war — tagged for the same duration after leaving.

Notes:

* The client is warned on entering combat and shown the tag countdown, so a legitimate quit is possible by waiting.
* Crashes and genuine network drops are indistinguishable from rage-quits server-side, and treating them differently is exploitable. `combatLogGraceOnServerStop` (default true) exempts disconnects caused by server shutdown, which is the one case the server can identify reliably.
* `combatLogKill` can be disabled outright, which is the only supported way to opt out.

### 12.6 The login shield is not invulnerability

A shielded player can fight, be killed, and count in capture zones. Granting immunity would let a nation cycle logins to park unkillable bodies on a contested checkpoint. `loginShieldGrantsInvulnerability` exists, defaults false, and is documented as unbalanced.

---

## 13. Alliances and coalitions

### 13.1 Alliance definition

An alliance is a **mutual** OPAC ally relation: A lists B *and* B lists A. One-directional ally entries confer nothing, which prevents a nation from dragging an unwilling party into a war by unilaterally declaring friendship.

### 13.2 Automatic entry

When C declares on B, the declaration extends to every mutual ally of B. The defender coalition is assembled at declaration time and is not re-evaluated afterwards — alliances formed mid-war do not pull new nations in, and alliances broken mid-war do not release anyone.

`allianceCascadeDepth` (default 1) bounds the spread. At 1, allies of B join, but allies of those allies do not. Raising it can pull an entire server into one war from a single declaration; that is a legitimate server style but should be a deliberate choice.

Attacker-side allies are **not** auto-enrolled. Joining an offensive war is voluntary via `/war join <warId> attackers`, subject to the same cooldown and lock checks as a declaration.

### 13.3 Coalition structure

Each side has a `primaryNationId` — the declarer for attackers, the original target for defenders. The primary leads negotiation (§15.5) but has no authority over allies' assets.

A nation may be in multiple wars simultaneously (`maxConcurrentWars`, default 50), on either side, including as an attacker in one and a defender in another.

### 13.4 Scheduled entry

An ally with no Ready player at declaration is added to `pendingMembers` rather than dropped:

* Its cities are **not** war-targetable and it accrues no war score or evasion time while pending.
* Entry triggers when any of its members logs in and clears the login shield.
* On entry, that nation gets its **own `PREPARATION` window** (`warPrepDuration`, default 6 h) before its cities become targetable, and every citizen is notified on login that their nation has been brought into a war and by whom.
* The war continues against the already-active members throughout.

Waking up to find your nation at war is a legitimate consequence of an alliance, but waking up to find your cities already lost is not. The private prep window is the compensation.

`pendingEntryExpiry` (default equal to the war duration) drops an ally that never logs in; they were never in the fight and owe nothing at settlement.

### 13.5 Alliance changes during a war

Breaking an alliance mid-war does not remove a nation from a coalition it has already joined — exit is via surrender or settlement only. This prevents using an alliance break as a free withdrawal after taking losses or spoils.

---

## 14. War lifecycle

### 14.1 Phases

```
                       declare
                          │
                    ┌─────▼─────┐  prepDuration
                    │PREPARATION│
                    └─────┬─────┘
                          │
  both coalitions   ┌─────▼─────┐  all targets occupied ┌───────────┐
  war-ready    ┌───▶│  ACTIVE   │──────────────────────▶│SETTLEMENT │
               │    └─────┬─────┘  deadline reached ───▶│           │
               │          │        last defender        │           │
               │  either  │        evasion-surrenders ─▶│           │
               │  side    │                             └─────┬─────┘
               │  empties │                                   │
               │    ┌─────▼─────┐                       ┌─────▼─────┐
               └────│ SUSPENDED │                       │   ENDED   │
                    └───────────┘                       └───────────┘
```

### 14.2 Declaration

`/war declare <nation>`, leader only.

| # | Check |
|---|---|
| 1 | Sender is their nation's OPAC party owner |
| 2 | Sender's nation has ≥ 1 city |
| 3 | Target exists, is not the sender's nation, and is not a mutual ally |
| 4 | Target has ≥ 1 non-`DORMANT` city past its founding grace |
| 5 | **Target nation is war-ready** |
| 6 | **Declaring nation is war-ready** |
| 7 | No existing unsettled war between the two |
| 8 | `now ≥ warCooldowns[target]` (default 7 days after settlement) |
| 9 | Neither nation is locked pending settlement |
| 10 | Neither nation is at `maxConcurrentWars` (default 50) |
| 11 | Server time inside the war window, if configured |

Declaring on a nation already at war is permitted — dogpiling is a legitimate strategy and reflects how real coalitions form against a weakened power. Checks 5 and 6 apply to the primary target and declarer only; allies are enrolled via §13.2 regardless of their readiness.

### 14.3 Suspension

`ACTIVE` only while both coalitions are war-ready. If either side has zero Ready players for `presenceGraceDuration` (default 3 min), the war suspends:

* Capture progress freezes, then decays.
* Occupation and occupation locks continue.
* **`warExpiresAt` does not pause.** Suspension stops the fighting, not the calendar.
* **The evasion clock does not stop** for the absent side (§14.4).
* Notifications are phrased generically so suspension cannot be used to infer who is online.

Suspension is the normal state. Over a month-long war two coalitions are simultaneously present for a small fraction of elapsed time; `SUSPENDED` is where the war lives, punctuated by the evenings when both sides show up. Only the transition *to* `ACTIVE` deserves a prominent alert — that is when a siege is actually happening.

### 14.4 Evasion surrender

A nation that hides from a war it is in loses it. Per belligerent nation, per war:

| Term | Meaning | Default |
|---|---|---|
| Evasion clock | Accrues while **this nation has no Ready player** and **an opposing coalition member does** | — |
| `warEvasionLimit` | Accrued evasion time triggering automatic surrender | 72 h |
| `warParticipationMinimum` | Ready time that resets the evasion clock | 60 min |
| Qualifying session | Cumulative Ready time by any citizens, reaching the minimum | — |

Two properties matter:

* **The clock only runs while the enemy is present.** A nation cannot be forced to surrender because both sides happened to be offline; only dodging a live opponent counts.
* **A login is not enough.** Connecting for five minutes to reset the timer does nothing — the nation must field an hour of Ready time. Contributions from multiple citizens accumulate, so a coalition can share the burden.

Warnings go to every citizen at 50%, 75% and 90% of the limit, stating the remaining time and the participation needed to clear it. On breach:

1. That nation surrenders automatically: its occupied cities transfer to their occupiers immediately (§15.3 terms).
2. It exits the coalition. The war continues if other members remain.
3. If it was the last active member, the war moves to `SETTLEMENT`.
4. A full-length cooldown is applied and the event is announced and audit-logged.

This replaces the presence truce from v1.2. Softlocking a month-long war by not logging in is now the fastest way to lose it.

### 14.5 Termination

`PREPARATION` (`warPrepDuration`, default 6 h): coalitions notified at declaration and again on each citizen's next login; target cities `UNDER_SIEGE`; no capture. The attacker may cancel at a doubled cooldown.

`ACTIVE` (`warDuration`, default 7 days, maximum 30): capture live whenever both coalitions are present. `warExpiresAt` is wall-clock and runs through suspension, restarts and downtime.

| Trigger | Outcome | Resolution |
|---|---|---|
| All target cities occupied | `ATTACKER_TOTAL_VICTORY` | → `SETTLEMENT` |
| `warExpiresAt` reached | `TIMEOUT` | → `SETTLEMENT` |
| Last active defender evasion-surrenders | `EVASION_SURRENDER` | → `SETTLEMENT` |
| Defender primary `/war surrender` | `SURRENDER` | Applied immediately (§15.3) |
| Attacker primary `/war withdraw` | `ATTACKER_WITHDRAWAL` | Applied immediately; occupations released, doubled cooldown |
| A coalition empties by disbandment | `VOID` | Occupations released |
| `/nationwars staff war cancel` | `STAFF_CANCEL` | Occupations released |

Reaching `SETTLEMENT` locks every belligerent (§15.3) unless nothing was occupied, in which case the war closes immediately as a white peace.

### 14.6 Counter-offensive

A war starts one-directional: only the defender's cities are at stake. That is correct for an invasion, and wrong for what happens when the invasion fails. A defender who wipes out an attacking force should be able to march back the way they came.

The defender primary calls `/war counteroffensive` when all of the following hold:

| # | Condition | Default |
|---|---|---|
| 1 | War is `ACTIVE` | — |
| 2 | The defender coalition currently has **zero** cities occupied — the invasion is fully repelled | — |
| 3 | Defender war score ≥ attacker war score × `counterOffensiveScoreRatio` | `1.0` |
| 4 | At least `counterOffensiveMinDuration` has elapsed since the war went `ACTIVE` | `24 h` |
| 5 | The defender coalition is war-ready | — |

Condition 2 is the important one: you cannot counter-attack while still occupied. Condition 3 means the turn has to be earned in the field — repelling a token raid is not grounds for annexing the raider.

**Effects.** The war becomes **two-front**:

* Every attacker-coalition nation's cities are added to `targetCityIds`, and the defender coalition may capture them under the ordinary rules.
* The original attacker's cities enter a `counterOffensivePrep` window (default 6 h) before becoming capturable — the same courtesy given to allies dragged in while offline (§13.4).
* Voluntary attacker-side joiners (§13.2) are included. Enlisting in an offensive war exposes you to the counter-attack.
* `/war withdraw` no longer ends the war cheaply for the attacker: after a counter-offensive it routes to `SETTLEMENT` with occupations intact, exactly like any other termination.
* The war deadline is unchanged. A counter-offensive does not buy extra time; it changes what is on the table within the time remaining.

**Settlement** needs no special handling. §15 already works from `occupiedCityIds` and war score in whichever direction they run, so a war that ends with the original defender holding three of the aggressor's cities settles by the same rules as one that ends the other way. The peace screen's two columns are already symmetric.

`allowCounterOffensive` (default `true`) disables the mechanic for servers that want strictly one-directional wars.

---

## 15. Peace settlement


Cities change hands here. Three paths produce the same `PeaceSettlement`, applied atomically: automatic surrender terms, a negotiated deal, or a staff-imposed settlement.

### 15.1 The lock

Entering `SETTLEMENT` locks every belligerent until terms are agreed or imposed.

| Blocked | Unaffected |
|---|---|
| Declaring or receiving wars | Building and mining **inside existing cities** |
| Founding cities | PvP, movement, everything vanilla |
| Tier upgrades and payment | The City GUI, read-only |
| Placing or breaking checkpoints | OPAC party management, chat |
| Disbanding cities | Claims already held |
| Capture of any kind | |

Occupations freeze with their locks held open. The lock stops *expansion*, not play — a locked nation carries on building inside what it owns, which is why weeks of lock is tolerable while weeks of frozen gameplay would not be.

Every citizen of a locked nation gets a persistent HUD banner and a login message naming the war and how to reach the peace screen.

**The lock only engages if at least one city is occupied.** A war that reaches settlement with no ground taken closes immediately as a white peace: cooldowns written, no lock, no negotiation. Without this guard, declaring war and letting it lapse would freeze a target's expansion at no cost to the declarer.

### 15.2 Clauses

The clause list is a **registry**, `nationwars:peace_clause`, so new clause types are added without touching the apply pipeline.

| Clause | Parameters | Validation at apply |
|---|---|---|
| `TransferCity` | `cityId`, `toNationId` | City owned by a belligerent; recipient has war score to cover it (§15.4) |
| `ReleaseOccupation` | `cityId` | Currently occupied in this war |
| `Tribute` | `from`, `to`, `value` | Deducted from the payer's cities' banked payment, highest tier first |
| `Ceasefire` | `durationHours` | Writes `warCooldowns` both ways |

Zero transfer clauses is a white peace.

### 15.3 Surrender terms

`/war surrender` (defender primary, or any belligerent for its own account) applies immediately:

```
for cityId in occupiedCityIds owned by the surrendering nation:
    TransferCity(cityId, occupier)
Ceasefire(defaultPostWarCooldown)
```

Surrendering with nothing occupied concedes the war at the cost of the cooldown only — a legitimate way for an overmatched nation to stop a hopeless siege. Evasion surrender (§14.4) applies these same terms without the nation's participation.

### 15.4 War score

Score is the currency of the negotiating table. It makes multi-party spoils division automatic rather than a shouting match, and stops a coalition member who did nothing from claiming the best city.

Earned per nation, per war, all values config:

| Event | Default |
|---|---|
| Checkpoint captured | 10 |
| Checkpoint successfully retaken in defence | 5 |
| City occupied (first time, per city) | 100 |
| City successfully held to war's end | 50 |
| Each 10 min of Ready participation while `ACTIVE` | 1 |

At settlement, a nation may only *receive* spoils it can pay for:

```
cityValue = tierCost(tier) × cityValueTierWeight
          + bankedPayment × cityValueBankWeight
          + checkpointCount × cityValueCheckpointWeight
```

Accepting a `TransferCity` clause deducts `cityValue` from the recipient's war score. A clause the recipient cannot afford fails validation, with the shortfall named in the error. Tribute is priced the same way.

Score is aggregated off-thread (§4.4) and displayed live in `/war status` so allies can see what they are earning while the campaign runs, not just at the end.

### 15.5 Negotiated peace

**This is how wars are meant to end.** The screen must be sufficient alone: belligerents conclude a month-long campaign in game, with no staff involved and nothing agreed off-server. Staff finalization (§15.7) exists for when that fails.

Available during an `ACTIVE` war via `/war negotiate`, and during `SETTLEMENT`.

```
┌──────────────────────────────┬──────────────────────────────┐
│  OUR COALITION GIVES         │  THEIRS GIVES                │
├──────────────────────────────┼──────────────────────────────┤
│  ▸ Cities        [+]         │  ▸ Cities        [+]         │
│    · Ironhold  (occupied)  ✕ │    · Redmarch  (occupied)  ✕ │
│  ▸ Tribute       [ 512 ]     │  ▸ Tribute       [   0 ]     │
├──────────────────────────────┴──────────────────────────────┤
│  Ceasefire: [ 48 ] hours                                    │
│  Your war score: 340   ·  Redmarch costs 280  ·  Left: 60   │
│  Ratified: Aldmark ✓   Brenhold ✓   Caskeep ⧗                │
│                              [ Propose ]  [ Clear ]         │
└─────────────────────────────────────────────────────────────┘
```

**Ratification.** A deal applies when **both sides agree**. A side's agreement is given by its coalition primary, with one exception: a clause transferring a city owned by a non-primary member also requires that member's leader to sign. A primary negotiates for the coalition but cannot sign away an ally's property.

If the two sides cannot agree, staff ratify (§15.7). That is the only role staff have in a peace: breaking a deadlock, not brokering one.

A deadlock is flagged automatically for staff attention when a settlement has been open for `deadlockThreshold` (default 7 days) or `deadlockRejections` (default 3) offers have been rejected. Flagged wars appear in `/nationwars staff war list --deadlocked`.

**Asynchronous by default.** Over a month-long war, leaders may not share a timezone.

* Proposing needs only the proposer online and Ready.
* Offers wait in each recipient's inbox and are surfaced on their next login.
* `offerExpiry` defaults to 48 h.
* Ratification is incremental — signatories sign as they log in, and the deal applies when the last required signature lands.
* Every signature re-validates against live state; a city that changed hands since drafting voids the offer with an explanation rather than applying silently.

**Security.** The screen has no slots, so it uses dedicated packets. The server holds the authoritative settlement and issues a single-use token; the accept packet carries the token and the settlement hash, and the server re-validates every clause from its own copy. The client's clause list is never trusted. C2S packets are rate-limited.

**Command fallback** for vanilla clients:

```
/war negotiate offer city <city>        /war negotiate demand city <city>
/war negotiate offer tribute <value>    /war negotiate ceasefire <hours>
/war negotiate review | send | clear    /war negotiate accept | reject | counter
```

### 15.6 Applying a settlement

Atomic. Pre-validation runs off-thread; the commit runs on the main thread under the global write lock.

1. Validate every clause against live state and war score. Any failure aborts the whole settlement, naming the clause.
2. `TransferCity`: set owner, bump `transferCount`, move claims (§16.3), keep tier, banked payment and checkpoints, deduct war score, set `ACTIVE` with a fresh occupation lock so the loser cannot instantly counter-attack.
3. `ReleaseOccupation`: unfreeze checkpoints, restore holder.
4. `Tribute`, then `Ceasefire`.
5. Release any occupation no clause covers.
6. Clear `lockedByWarId` on all belligerents; notify.
7. `phase = ENDED`, record the settlement, announce terms, audit-log the whole transaction as one reversible entry.

### 15.7 Staff finalization and the backstop

Staff resolve wars only when the belligerents cannot — a dispute, an inactive leader, a bug.

```
/nationwars staff war settle <warId>                          — superuser settlement screen
/nationwars staff war settle <warId> apply-occupations        — transfer every occupied city
/nationwars staff war settle <warId> status-quo               — release everything
/nationwars staff war settle <warId> transfer <city> <nation>
/nationwars staff war settle <warId> tribute <from> <to> <value>
/nationwars staff war settle <warId> ceasefire <hours>
/nationwars staff war settle <warId> review | clear
/nationwars staff war finalize <warId>                        — apply atomically
```

Staged clauses persist across restarts and are visible to every belligerent leader via `/war status`. Staff settlements ignore war score limits — that is the point of an imposed peace — and are audit-logged with the full clause list.

**Backstop.** `settlementWindow` (default 14 days) bounds the lock. If nobody settles, the default outcome applies — every occupied city transfers to its occupier — the lock lifts, and it is announced as an imposed truce. Both sides see a countdown inside the final 48 h.

`settlementWindow = 0` makes the lock indefinite. Only for servers with reliable staff, and pair it with monitoring on old `SETTLEMENT` wars.

---

## 16. Territory and OPAC claims

### 16.1 Checkpoints project territory

Each checkpoint claims **5 chunks**: its own, plus the four cardinal neighbours.

```
        ┌───┐
        │ N │
    ┌───┼───┼───┐
    │ W │ ● │ E │        ● = chunk containing the checkpoint
    └───┼───┼───┘
        │ S │
        └───┘
```

The City Core claims the same plus shape by default. A city's territory is the **union** of every claim set it owns, deduplicated — overlapping shapes cost nothing.

This replaces v1.2's radius-derived claims. Territory now follows the defences: to hold ground you must plant and keep a flag on it, and losing checkpoints visibly shrinks your map presence.

Shapes are config (`checkpointClaimShape`, `cityCoreClaimShape`): `PLUS` (default), `SINGLE`, `SQUARE` (3×3), or `NONE`.

### 16.2 Claim lifecycle

| Event | Effect |
|---|---|
| Checkpoint placed | Its 5 chunks are claimed; placement is refused if any is held by another nation (§9.1.8) |
| Checkpoint broken | Its chunks are released **unless** still covered by another checkpoint or the core |
| Checkpoint moved | Old set released, new set claimed, in one transaction |
| City transferred | Whole union re-registered to the new owner (§16.3) |
| City disbanded | Whole union released, per `releaseClaimsOnDisband` |

Set computation is a pure function and runs off-thread; the resulting OPAC calls are applied on the main thread in one batch.

### 16.3 Claim ownership and transfer

Claims are registered under the **nation leader's player UUID**, so citizen access follows OPAC's party sharing.

* The leader's OPAC player config must permit party access, or citizens are locked out of their own city. Checked at founding, with the exact `/openpac player-config` command given to the leader.
* If OPAC's server config enables party-owned claims, use the party claim owner instead. Detect at startup and branch.
* City claims bypass per-player claim limits via `claim(...)`; city territory does not consume a player's personal budget.
* **Leader changes require re-registration** under the new leader's UUID, or citizens lose access. Detected in the validation sweep (§21.6).

At settlement, for each chunk in a transferring city's union: re-claim from the loser's leader to the winner's; leave third-party claims alone and log them; claim unclaimed chunks for the winner. Batch into one tick.

### 16.4 War protection override

OPAC claims exist to stop griefing, which is exactly what they do to a siege: attackers who cannot break a door or land a hit have no way to fight for a checkpoint. Protection must lift between belligerents while a war is live, and come back afterwards.

**Derive the override; never store it.** The naive approach — flip OPAC's protection settings at war start and restore them at war end — leaves the world unprotected if the server crashes between the two, and a month-long war is a long time to hold that risk. Instead the override is a **pure function of live state**, evaluated per event:

```
allowed(player, pos, action) =
      war = activeWarCovering(chunkOf(pos))
   && war.phase == ACTIVE
   && player's nation and the chunk's owning nation are in opposing coalitions of that war
   && chunk ∈ claim union of a city in war.targetCityIds
   && action ∈ warProtectionOverride
```

Nothing is written, so nothing needs restoring. When the war ends the function stops returning true and protection is back on the next event, with no cleanup step to fail.

**Implementation.** Handle the same Forge events OPAC does, at `EventPriority.LOWEST` with `receiveCanceled = true`. OPAC cancels at its own priority; if the action is war-sanctioned, un-cancel it. This leaves OPAC's configuration untouched and works regardless of how its protection is configured per-player.

> ⚠️ **Integration risk.** This assumes OPAC's protection is expressed as cancellations of standard Forge events. If it uses mixins or internal hooks for some actions, those are not observable this way. Verify per action category against the target build during M2. Fallback: mutate the relevant OPAC sub-config and persist an **override journal** — a record of every mutation, replayed and reversed on startup — so a crash mid-war is recoverable. Prefer the event route; the journal exists because the fallback would otherwise reintroduce exactly the failure mode this section avoids.

**Scope.** Deliberately narrow:

| Dimension | Limit |
|---|---|
| Time | `ACTIVE` phase only — not `PREPARATION`, not `SUSPENDED`, not `SETTLEMENT` |
| Space | Chunks in the claim union of cities in `targetCityIds`, not the defender's whole territory |
| People | Members of an opposing coalition only; neutrals and allies are unaffected |
| Actions | The `warProtectionOverride` list only |

The time limit does the most work. `ACTIVE` requires both coalitions to be war-ready, so **war-time destruction is only possible while defenders are online to contest it**. Log off and the war suspends, protection snaps back, and your builds are safe until you return. That property falls out of the existing suspension rule rather than needing its own machinery.

**Action categories** (`warProtectionOverride`):

| Action | Default | Note |
|---|---|---|
| `blockBreak` | allowed | Needed to breach fortifications |
| `blockPlace` | allowed | Needed to bridge, tower, and wall off |
| `pvp` | allowed | Without it there is no fighting over a checkpoint |
| `explosions` | allowed | TNT is the siege engine this mod does not otherwise provide |
| `fireSpread` | blocked | Uncontainable and disproportionate to a one-hour occupation |
| `containerAccess` | blocked | Preserves §11.8: the mod moves territory, not property |
| `entityDamage` | blocked | Slaughtering farm animals is griefing, not warfare |

Servers wanting full-loot warfare enable `containerAccess`; servers wanting bloodless flag-capture reduce the list to `pvp` alone.

**Restoration on captured cities.** A city that transfers at settlement leaves `targetCityIds` with its war, so the override evaluates false immediately and the new owner's claims protect normally. No transition step required.

---

## 17. Logging, audit and reversal


Every privileged action is recorded with enough state to undo it. The design target is a disgruntled member or a compromised leader account: whatever they did, staff can see it and roll it back without hand-rebuilding anything.

### 17.1 Entry format

| Field | Notes |
|---|---|
| `entryId` | ULID — sortable by time, unique without coordination |
| `timestamp`, `actorUuid`, `actorName` | |
| `actorNationId`, `actorRole` | `LEADER`, `MODERATOR`, `MEMBER`, `STAFF`, `SYSTEM` |
| `source` | `COMMAND`, `GUI`, `BLOCK`, `AUTO` |
| `actionType` | Registry id, e.g. `nationwars:city_disband` |
| `targets` | Affected city/checkpoint/war/nation ids |
| `before`, `after` | NBT snapshots, scoped to what the action changed |
| `reversible` | Whether an inverse exists |
| `revertOf`, `revertedBy` | Entry ids linking a revert to its original |

### 17.2 What is logged

City founding, disbandment (issue, cancel, completion), rename; checkpoint place, break, move; tier upgrade and payment insertion; war declaration, join, surrender, withdrawal, evasion surrender; peace proposal, ratification, settlement application; every city transfer and claim change; all staff actions; and every automatic invariant repair (§5.8).

Ordinary building, mining, chat and movement are not logged. This is a governance log, not a block logger — pair it with CoreProtect or similar for that.

### 17.3 Reversal

```
/nationwars staff log player <name> [since] [limit]
/nationwars staff log city <city> | nation <nation> | war <warId>
/nationwars staff log show <entryId>            — full before/after diff
/nationwars staff revert <entryId>
/nationwars staff revert-session <player> <since>   — reverts everything by that actor, newest first
```

Rules:

* Reversal is a **compensating action**, itself logged with `revertOf` set. Nothing is ever erased.
* Entries form a dependency graph. Reverting an entry that later entries depend on is refused, and the command names exactly which entries must be reverted first. `revert-session` handles ordering automatically by walking newest-first.
* Irreversible actions are flagged at write time — consumed items whose payer has since left, a settlement whose counterparty nation no longer exists. Staff get a best-effort partial revert and an explicit list of what could not be restored.
* `revert-session` is the compromised-account remedy: one command, one timestamp, everything that account did is undone in order.

`auditRetentionDays` (default 90) governs storage; `auditRevertWindowDays` (default 30) bounds what can still be reverted, since reverting a month-old city transfer after three subsequent wars is rarely coherent.

### 17.4 Storage and threading

* Append-only, one gzipped file per day: `world/data/nationwars-audit/YYYY-MM-DD.jsonl.gz`.
* Written by a **single dedicated writer thread** off a bounded queue. Entries are enqueued from the main thread in microseconds.
* An in-memory index (entry id, actor, targets, timestamp) supports queries without decompressing; it is rebuilt on startup from the retention window, off-thread, and queries before it is ready return a "still indexing" notice rather than incomplete results.
* If the queue saturates, the writer switches to synchronous mode and logs a warning. **Audit entries are never dropped** — an audit log with holes is worse than a slow tick.

### 17.5 Diagnostic logging

Separate from the audit log, which is a governance record, the mod writes a normal diagnostic log for debugging and for post-mortems on "why did that happen".

* Dedicated Log4j2 logger `nationwars`, with a **rolling file appender** at `logs/nationwars/nationwars.log`, rolling daily and at `logFileSizeMb` (default 32), keeping `logFileHistory` (default 14) archives, gzipped.
* Registered programmatically at mod construction so no `log4j2.xml` edit is required, and set `additivity=false` so mod output does not also flood `latest.log`. `logToServerConsole` (default `WARN`) controls what still reaches the main console.
* Every line carries the ids it concerns, so a war can be reconstructed with `grep warId=...`:

```
[13:42:07] [Server thread] INFO  capture  — checkpoint flip cp=8f3a… city=2b71… from=Aldmark to=Brenhold war=c04e… progress=1.00 attackers=3 defenders=0
[13:42:07] [Server thread] INFO  war      — occupation city=2b71… by=Brenhold war=c04e… lockUntil=14:42:07 occupied=2/5
[13:42:08] [nationwars-worker-1] DEBUG claims — computed union city=2b71… chunks=17 took=1.4ms
```

### 17.6 Log categories

Levels are set per subsystem, so a server chasing a capture bug can raise `capture` to `DEBUG` without drowning in claim traffic.

```toml
[logging]
default = "INFO"
categories = { capture = "INFO", war = "INFO", claims = "INFO", threading = "WARN",
               audit = "INFO", config = "INFO", protection = "INFO", persistence = "INFO" }
```

| Category | Covers |
|---|---|
| `capture` | Capture progress, flips, occupation, cosmetic effects |
| `war` | Declaration, coalitions, phases, suspension, evasion, counter-offensives |
| `claims` | Claim computation and OPAC calls |
| `protection` | War protection override decisions (`DEBUG` logs every allow, which is verbose but decisive when someone reports "I couldn't hit them") |
| `threading` | Pool saturation, queue depth, tasks exceeding `slowTaskThresholdMs` |
| `persistence` | Saves, loads, migrations, invariant repairs |
| `config` | Load, validation failures, reloads and what each reload changed |
| `audit` | Writer health, index rebuilds, revert operations |

`/nationwars staff loglevel <category> <level>` changes a level live, without a reload, for the current session — the fastest path from a bug report to useful output.

### 17.7 Crash and anomaly context

On any exception escaping a mod handler, the log line includes the acting player, the city/war ids in scope, and the current phase. `/nationwars staff dump` writes a full JSON state snapshot to `logs/nationwars/dump-<timestamp>.json` for bug reports — cities, wars, coalitions, scores, tracker states and config — with player UUIDs included and nothing else personally identifying.

---

## 18. Persistence

* `NationWarsSavedData` on the Overworld `ServerLevel`, at `world/data/nationwars.dat`.
* Holds cities, checkpoints, wars, coalitions, staged settlements, war scores, evasion trackers, nation state.
* Dimension stored per record as a `ResourceLocation`, so all dimensions share one file.
* Snapshot on the main thread, serialize off it, write via the I/O layer.
* Force-save on war phase transitions, occupations, settlements and disbandments.
* Block entities store only a UUID back-reference; the saved data is authoritative. An unknown UUID logs a warning and the block removes itself.
* **Schema version** in the root compound with a migration chain, present from v1.

### 18.1 Restart behaviour

* Wars, occupations, locks, staged settlements, war scores and evasion trackers persist.
* Nobody is Ready for 3 minutes after a restart, so active wars self-suspend without a special rule.
* Occupation locks, war deadlines and evasion clocks run in wall-clock time and are not paused by downtime. **Exception:** evasion time does not accrue while the server is down — nobody could have logged in. The tracker records server uptime windows and subtracts downtime, or an overnight outage would count as evasion against every belligerent.
* Capture progress resets to 0 on load.

---

## 19. Networking and client

Channel `nationwars:main`, protocol version 1.

### 19.1 S2C

| Packet | Cadence |
|---|---|
| `SyncCityPacket` | Join, change |
| `SyncCheckpointStatePacket` | Every 10 ticks while contested, within 128 blocks |
| `SyncWarStatePacket` | Change, plus every 30 s |
| `SyncCoalitionPacket` | Coalition membership and pending entries |
| `SyncWarScorePacket` | Every 60 s while `ACTIVE` |
| `SyncReadinessPacket` | State transitions, with shield countdown |
| `SyncCombatTagPacket` | Combat tag start and countdown |
| `SyncEvasionWarningPacket` | At warning thresholds |
| `OpenPeaceDealPacket` | On negotiate or incoming offer |
| `SyncSettlementPacket` | Change, incl. ratification progress |
| `CheckpointEffectPacket` | Break-and-respawn effect (§11.5) |

Never send another nation's readiness roster.

### 19.2 C2S

`RequestCityInfoPacket`, `DeclareWarPacket`, `ProposeSettlementPacket`, `SettlementResponsePacket`. Rate-limited and fully re-validated server-side. Menu interactions use `clickMenuButton`.

### 19.3 Rendering

* **Core:** beam in the owner's colour; second offset beam in the occupier's while occupied.
* **Checkpoint:** banner in the holder's colour; progress ring when contested; chain overlay when `FROZEN` or `SEALED`; shatter-and-reform on capture or cosmetic break.
* **HUD:** target cities with held/total, occupation badges with countdowns, war deadline in days and hours, a clear `ACTIVE` vs suspended indicator, coalition roster, own war score. During settlement, the lock banner, terms and ratification status.
* **Boss bar:** capture progress inside a zone.
* **Indicators:** shield countdown, AFK badge, combat tag countdown, evasion warning.

### 19.4 Vanilla clients

Everything in the HUD is reachable via `/city info`, `/war status`, `/war negotiate review` and chat. Guard sends with a channel-presence check so absent clients are skipped, not kicked.

---

## 20. Commands and permissions

### 20.1 Player commands

```
/city info [name] | list [nation] | checkpoints
/city rename <name> | sethome <name>
/city disband <name> confirm|cancel      — nation leader only, broadcast

/nation info [nation] | cities | allies

/war declare <nation>                    — leader only
/war join <warId> attackers              — voluntary offensive entry
/war status [id]                         — incl. coalitions, war score, terms
/war list
/war surrender | withdraw
/war counteroffensive                    — defender primary, §14.6
/war negotiate ...

/afk
```

### 20.2 Staff

"Staff" covers admins **and** moderators.

**No permission mod is required.** Nodes are registered with Forge's built-in `PermissionAPI` via `PermissionGatherEvent.Nodes`. Forge's default resolver answers from vanilla operator level, so on a bare server `staffPermissionLevel` (default 2) governs everything and the mod works out of the box. If a permission mod is installed — LuckPerms via its Forge bridge, FTB Ranks, or anything else implementing a `PermissionHandler` — it takes over resolution automatically and the nodes below become assignable per player or group. The mod neither depends on nor detects any specific one.

Nodes are granular so moderators can hold day-to-day powers without settlement or revert authority (Appendix B).

```
/nationwars staff city transfer|release|delete|revalidate <city>
/nationwars staff war cancel|extend|settle|finalize <warId>
/nationwars staff war list [--deadlocked]
/nationwars staff coalition add|remove <warId> <nation> <side>
/nationwars staff evasion <nation> <warId>        — inspect or reset the clock
/nationwars staff readiness <player>
/nationwars staff loglevel <category> <level>
/nationwars staff log ... | revert ... | revert-session ...
/nationwars staff perf | dump | reload
```

Every staff action is audit-logged with executor, arguments and timestamp, and is itself revertible where an inverse exists.

---

## 21. Edge cases

**21.1 Nationless players.** Cannot found cities or place checkpoints. Nothing else changes for them.

**21.2 Party disbanded.** Poll `getPartyById` per city owner every `nationValidationInterval` (default 5 min), or subscribe to OPAC lifecycle events if exposed. Cities become `DORMANT`; the nation is removed from any coalition; if a coalition empties, the war ends `VOID`; cities are removed after `dormantCityExpiry`.

**21.3 Unloaded chunks.** No force-loading — a month-long war would pin large areas open for the majority of time nobody is fighting. Capture already requires a player present, which loads the chunk. Decay is handled by lazy evaluation: each checkpoint stores `lastEvaluatedTime`, and on chunk load the elapsed decay is applied in one step. Because decay is linear and monotonic toward zero, this is exactly equivalent to having ticked throughout, at zero cost while unloaded.

**21.4 Player leaves a nation mid-war.** Immediately neutral in capture zones; loses checkpoint rights. Cities are unaffected.

**21.5 Leader changes.** Ownership is by party UUID and unaffected, but claims must be re-registered under the new leader (§16.3), and pending disbands are cancelled.

**21.6 Validation sweep.** Every `nationValidationInterval`: verify party existence, leader identity vs claim ownership, invariants (§5.8), sky columns of loaded cities, and evasion clocks. Planning runs off-thread; repairs commit on the main thread and are audit-logged.

**21.7 Simultaneous captures.** The occupation check runs once per tick after all checkpoint updates, so a city occupies exactly once even if several checkpoints flip together.

**21.8 Third parties in a capture zone.** Only members of the two coalitions affect progress. Others are neutral but can still fight.

**21.9 A city targeted by two wars.** Permitted, since dogpiling is allowed. Only one coalition can occupy at a time, and the occupation lock applies globally — a city occupied in war A cannot be captured in war B until the lock expires.

**21.10 A nation loses its last city.** It survives with zero cities, cannot be declared on, and may found again after the cooldown.

**21.11 A locked nation's leader stops playing.** The lock is nation-level, so it persists; the backstop is the exit. Consider granting `MODERATOR` ratification rights if this proves common (§23.3).

**21.12 Build limit.** `getMaxBuildHeight()` is 320 in the Overworld (top placeable y=319). Never hardcode.

**21.13 Core placed by command.** A block entity with no matching city record is inert. Only the §8 pathway creates cities.

**21.14 Checkpoint claim conflicts on upgrade.** A tier upgrade that would let checkpoints be placed into another nation's claims is not blocked — placement itself is (§9.1.8). The upgrade only grants reach.

**21.15 Ally enters a war after its cities were already lost to a different war.** Pending entry is skipped for cities under an active occupation lock elsewhere; they cannot be double-targeted.

---

## 22. Performance

* **Main-thread per-tick work** is one AABB query per contested checkpoint plus one position delta per player. Everything else is event-driven or offloaded (§4).
* **Sky column checks** use the section shortcut (§4.5), reducing a 2,900-block scan to ~20 comparisons in the common case.
* **Claim computation, war score, settlement validation, audit writes and persistence encoding** all run off the main thread.
* **Capture** touches only unfrozen checkpoints in `ACTIVE` wars in loaded chunks.
* **Audit index** is memory-resident for the retention window only; older days stay compressed on disk.
* **Rendering** is scoped to render distance; checkpoint sync to 128 blocks.

`/nationwars staff perf` reports per-system main-thread cost (average and p99), worker queue depth, audit queue depth and I/O backlog.

---

## 23. Open questions

1. **Does OPAC express all protection as cancellable Forge events?** The single largest implementation unknown (§16.4). Verify per action category in M2 — the answer decides whether the override is a clean derived function or needs the journal fallback.
2. **Counter-offensive score ratio.** At 1.0 the defender must have out-fought the attacker outright. Lower values make the turn easier and wars swingier; higher values make counter-attacks rare and decisive.
3. **Should `containerAccess` be overridable at all?** It is off by default and conflicts with §11.8's promise that property is untouched. Servers wanting full-loot war will want it; leaving the option present invites confusion about what a war costs.
4. **Tier-2 entry cost.** With `min(2) = max(1) = 5`, a city must place all 5 tier-1 checkpoints before upgrading. At `minCheckpointSpacing` 3 and radius 5 that is geometrically tight — worth playtesting whether it feels like consolidation or like busywork.
5. **Evasion clock during a counter-offensive.** Currently unchanged: the original attacker can evasion-surrender once their own cities are at stake, which is thematically right but means a failed invasion can cost you cities without you ever logging back in.
6. **Should staff be able to ratify before the deadlock threshold?** Currently they can (`settle`/`finalize` have no gate), while §15.5 says they should only break deadlocks. Consider whether the command should warn, or refuse, on a war not yet flagged.

---

## 24. Implementation milestones

| Milestone | Scope | Done when |
|---|---|---|
| **M0** Foundations | Project, OPAC dependency, config framework, registries, **threading layers (§4)**, `SavedData` skeleton | Worker pool, striped locks and the I/O writer exist before any feature uses them |
| **M1** Logging & audit | Rolling diagnostic log with per-category levels, audit entry format, writer thread, index, query commands | Every subsequent milestone logs and audits from day one rather than retrofitting |
| **M2** Blocks & founding | Both blocks, sky column prevention and section-shortcut scanning, founding, command-only disband, **OPAC protection-override probe (§16.4)** | A nation founds a city; the core cannot be mined; the override approach is validated against the real OPAC build before anything depends on it |
| **M3** Checkpoints & claims | Placement, break/move by any citizen, plus-shape claims, spacing feasibility validation | Claims follow checkpoints; breaking releases only uncovered chunks |
| **M4** GUI & tiers | Menu, config-driven tier list, config payment values with tag support | An arbitrary tier list and custom modded ores work with no code change |
| **M5** Activity & combat | Trackers, shield, AFK, readiness, combat tag and combat-log kill | AFK machines fail; disconnecting mid-fight drops your inventory |
| **M6** War & coalitions | Declaration, alliance enrolment, pending entry, suspension, evasion clock and surrender, **counter-offensive** | A three-nation coalition war runs; a nation that hides for 3 days loses; a repelled defender can turn the war around |
| **M7** Capture & occupation | Zones, progress, flips, cosmetic break effect, occupation, lock, retakes, **war protection override** | Flags shatter and reform in the captor's colour; attackers can actually fight inside claimed territory, and only while defenders are online |
| **M8** Settlement & peace deals | Settlement pipeline, lock and white-peace guard, war score, multi-party ratification, peace screen, backstop | Three leaders end a month-long war on negotiated terms with no staff present |
| **M9** Staff tooling | Settlement staging, reversal, `revert-session`, perf reporting, permission nodes | A compromised leader's day is undone with one command |
| **M10** Client | Beams, flags, effects, HUD, boss bar, indicators; vanilla fallback | Playable with the mod client-side, readable without it |
| **M11** Hardening | Invariant repair, migrations, load testing under concurrent wars, localisation | A corrupted save loads with warnings; 50 concurrent wars hold tick time |

**Sequencing notes.** M0 first: retrofitting a threading model onto synchronous code is a rewrite. M1 second, so every feature is audited from birth. M5 gates M6 (readiness), M6 gates M7 (capture needs a war), and **M8 must ship with M7** — the settlement lock means a war reaching settlement with no way to resolve it deadlocks two coalitions. M9 is genuinely optional for a first playable release if M8 does its job.

---

## Appendix A — Configuration

Forge `ModConfigSpec`, `Type.SERVER`, at `<world>/serverconfig/nationwars-server.toml`. **No gameplay constant is hardcoded**; anything below can be changed without recompiling.

### Tuning workflow

The defaults in this document are first guesses, not balance claims. They exist so playtesting has somewhere to start, and the intent is that measured values replace them as defaults once a server has found the sweet spot.

`/nationwars staff reload` re-reads the file live. Three classes of value:

| Class | Examples | Reload behaviour |
|---|---|---|
| **Hot** | Capture rates, durations, war score weights, thresholds, log levels | Applied immediately; in-flight wars pick up new values on their next tick |
| **Warm** | Tier costs, payment values, claim shapes | Applied immediately to new actions; existing cities keep their current tier and claims until they next change |
| **Structural** | Tier count, radii, checkpoint minimums and maximums | Applied with a validation pass; cities now violating a limit are reported and grandfathered, never auto-destroyed. Reducing a maximum below an existing city's count is logged and permitted |

Every reload logs a diff of what changed, at `config` level, so a mid-playtest tweak is traceable in the log afterwards.

`/nationwars staff perf` alongside `/nationwars staff dump` gives the measurements to tune against: how long captures actually take, how much war score a campaign generates, how often evasion warnings fire.

### Placement and cities

| Key | Default |
|---|---|
| `allowedDimensions` / `blockedDimensions` | `["minecraft:overworld"]` / `[]` |
| `requireSurfacePlacement` / `surfaceTolerance` | `true` / `4` |
| `columnRevalidateInterval` | `600 s` |
| `minCoreDistance` | `192` |
| `maxCitiesPerNation` / `maxCitiesPerMember` | `5` / `2` |
| `cityFoundCooldown` / `foundingGracePeriod` | `30 min` / `15 min` |
| `cityDisbandDelay` | `5 min` |
| `dormantCityExpiry` | `7 days` |
| `minCheckpointSpacing` / `minCoreClearance` | `3` / `3` |
| `checkpointMoveGrace` | `60 s` |
| `checkpointRespawnDelay` | `3 s` |

### Tiers and payment

| Key | Default |
|---|---|
| `tiers` (list of `radius`, `cost`, `minCheckpoints`, `maxCheckpoints`) | `5/0/1/5`, `8/128/5/8`, `13/512/8/13`, `21/2048/13/21` |
| `payments.values` | iron 1, gold 3, emerald 6, diamond 9, netherite 36 |
| `payments.blockMultiplier` | `9` |

### Ranks

| Key | Default |
|---|---|
| `cityFoundRank` / `checkpointPlaceRank` / `cityUpgradeRank` | `MEMBER` / `MEMBER` / `MODERATOR` |
| `alliesCanPlaceCheckpoints` | `false` |
| `staffPermissionLevel` | `2` |

### Capture

| Key | Default |
|---|---|
| `captureTickInterval` / `captureRadius` / `captureZoneHeight` | `10 ticks` / `5` / `8` |
| `baseCaptureRate` / `defenderRecoveryRate` / `decayRate` | `1/45` / `1/20` / `1/90` per s |
| `attackerStackBonus` / `attackerStackCap` | `0.5` / `3.0` |
| `checkpointLockout` | `15 s` |
| `creativeCanCapture` | `false` |
| `occupationLockDuration` | `60 min` |
| `occupationSuspendsClaimProtection` | `false` |

### Activity and combat

| Key | Default |
|---|---|
| `loginShieldDuration` / `afkThreshold` | `3 min` / `5 min` |
| `activityMoveThreshold` | `0.05` blocks² |
| `afkExitShieldSeconds` | `0` |
| `loginShieldGrantsInvulnerability` | `false` |
| `combatLogKill` / `combatTagDuration` | `true` / `20 s` |
| `combatLogGraceOnServerStop` | `true` |

### War

| Key | Default |
|---|---|
| `warPrepDuration` | `6 h` |
| `warDuration` / `warDurationMax` | `7 days` / `30 days` |
| `presenceGraceDuration` | `3 min` |
| `warEvasionLimit` / `warParticipationMinimum` | `72 h` / `60 min` |
| `evasionAppliesToAttackers` | `true` |
| `maxConcurrentWars` | `50` |
| `defaultPostWarCooldownHours` | `168` (7 days) |
| `allianceCascadeDepth` | `1` |
| `allowCounterOffensive` | `true` |
| `counterOffensiveScoreRatio` | `1.0` |
| `counterOffensiveMinDuration` / `counterOffensivePrep` | `24 h` / `6 h` |
| `pendingEntryExpiry` | `= warDuration` |
| `warWindowStart` / `warWindowEnd` | unset |

### Settlement

| Key | Default |
|---|---|
| `settlementWindow` (`0` = indefinite) | `14 days` |
| `settlementLockScope` | `FULL` |
| `offerExpiry` | `48 h` |
| `deadlockThreshold` / `deadlockRejections` | `7 days` / `3` |
| `score.checkpointCapture` / `checkpointDefended` | `10` / `5` |
| `score.cityOccupied` / `cityHeld` | `100` / `50` |
| `score.participationPer10Min` | `1` |
| `cityValueTierWeight` / `BankWeight` / `CheckpointWeight` | `1.0` / `0.5` / `10` |

### Territory

| Key | Default |
|---|---|
| `checkpointClaimShape` / `cityCoreClaimShape` | `PLUS` / `PLUS` |
| `syncClaims` / `releaseClaimsOnDisband` | `true` / `true` |
| `warProtectionOverride` | `["blockBreak", "blockPlace", "pvp", "explosions"]` |

### Logging

| Key | Default |
|---|---|
| `logging.default` | `INFO` |
| `logging.categories` | per §17.6 |
| `logToServerConsole` | `WARN` |
| `logFileSizeMb` / `logFileHistory` | `32` / `14` |
| `slowTaskThresholdMs` | `50` |

### Infrastructure

| Key | Default |
|---|---|
| `workerThreads` (`0` = auto) | `0` |
| `workerQueueCapacity` | `512` |
| `lockStripes` | `64` |
| `auditRetentionDays` / `auditRevertWindowDays` | `90` / `30` |
| `nationValidationInterval` | `5 min` |

---

## Appendix B — Permission nodes

Granular so moderators can hold routine powers without settlement or revert authority. Falls back to `staffPermissionLevel` when no permission mod is present.

| Node | Grants |
|---|---|
| `nationwars.staff.inspect` | `log`, `readiness`, `evasion`, `dump`, `perf` |
| `nationwars.staff.city` | `transfer`, `release`, `delete`, `revalidate` |
| `nationwars.staff.war` | `cancel`, `extend`, `coalition add/remove` |
| `nationwars.staff.settle` | `settle`, `finalize` — imposing peace terms |
| `nationwars.staff.revert` | `revert`, `revert-session` |
| `nationwars.staff.config` | `reload` |
| `nationwars.staff.*` | All of the above |

Suggested split: moderators get `inspect` and `city`; admins get everything. `settle` and `revert` are the two that rewrite history and should be held narrowly.
