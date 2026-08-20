# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A server-side territorial warfare mod for Minecraft Forge (mod id `nationwars`, display name "Nations & Checkpoints"). Player parties from Open Parties and Claims (OPAC) become **nations**; nations found **cities** anchored by a City Core block, defended by capturable **checkpoints**. During a declared **war**, an attacking coalition that holds every checkpoint of a city **occupies** it; ownership only changes at a negotiated (or staff-imposed) peace settlement.

The full design — data model, threading model, war lifecycle, config, GUI, commands — is specified in [`docs/warfront-spec.md`](docs/warfront-spec.md). **Read the relevant section there before implementing any feature spec'd in it** — it is the source of truth for behavior, not this file. The codebase is currently at the ForgeGradle MDK scaffold stage (`NationWarsMod.java`, `Config.java` are template examples), so most of the spec is not yet implemented.

Target environment: Minecraft 1.20.1, Forge 47.4.10 (`[47,)`), Java 17. Hard runtime dependency on Open Parties and Claims (OPAC) — treat its API as main-thread-only (see spec §3.1, §4).

## Commands

```sh
./gradlew build                          # compile, checkstyle, test, produce mod jar (build/libs/)
./gradlew build -x checkstyleMain -x checkstyleTest   # build without style checks (what CI's build job runs)
./gradlew checkstyleMain checkstyleTest  # style checks only (what CI's lint job runs)
./gradlew test                           # unit tests only
./gradlew test --tests "org.pixelfire.nationwars.SomeTest"   # single test class
./gradlew runClient                      # launch dev client (working dir `run/`)
./gradlew runServer                      # launch dev server (working dir `run/`, `--nogui`)
./gradlew runGameTestServer              # run registered game tests headlessly
./gradlew runData                        # run data generators -> src/generated/resources
```

`run/` (and `run-data/`) are gitignored dev working directories. OPAC's dev-time jar goes in `run/mods/`, or as a `libs/` flatDir dependency (see comments in `build.gradle`).

CI (`.github/workflows/ci.yml`) runs on every PR as two independent jobs: `build` (compile + test, checkstyle skipped) and `lint` (checkstyle only). Match that locally before pushing.

## Checkstyle

Config: `config/checkstyle/checkstyle.xml`, `maxWarnings = 0` (any warning fails the build). Deliberately narrow — no tabs, no trailing whitespace, 180-char line max, no unused/redundant imports, standard identifier naming. No brace-placement or Javadoc rules.

## Versioning and releases

No version number is hand-edited anywhere. Every merged PR to `main` or `dev` computes the next version from the highest `vX.Y.Z` git tag plus Conventional Commits prefixes since that tag, via [`.github/scripts/next-version.sh`](.github/scripts/next-version.sh): a `feat:`/`feat(scope):` subject bumps minor, anything else bumps patch. `main` tags and publishes the computed version as a GitHub Release (releases are created as drafts so asset uploads work on immutable-release repos); `dev` builds `X.Y.Z-dev.N` as a pre-release without tagging. Local/untagged builds fall back to axion-release-plugin's nearest-tag `X.Y.Z-SNAPSHOT`. Release workflows only run when relevant paths change.

PR titles must carry a Conventional Commits prefix (`feat: ...` / `fix: ...`) since squash-merge makes the PR title the commit subject that `next-version.sh` reads.

Branching: `main` and `dev` are protected, no direct pushes. `dev` is the active development branch — target PRs there; `main` tracks stable releases and is updated by merging `dev` in. Branch new work from `dev`.

## Architecture (per spec — most not yet built)

The mod is designed for concurrency from the ground up, driven by one constraint: Minecraft's world state (`Level`, `ChunkAccess`, `BlockEntity`, `ServerPlayer`, and the entire OPAC API) is main-thread-only. Everything else should move off it. See spec §4 for full detail; the key structure:

- **World layer** (main thread only) — block/entity I/O, OPAC calls, packet dispatch.
- **State layer** (any thread, lock-free reads) — `NationRegistry` holding `ConcurrentHashMap`s of **immutable Java records** (`City`, `Checkpoint`, `War`, `Coalition`); mutation produces a new instance. Cross-record consistency uses striped locks keyed by city/war id, ordered by UUID to avoid deadlock; rare multi-record atomic changes (settlement, transfer) take a single global write lock.
- **Compute layer** (worker pool) — claim computation, war score aggregation, settlement validation, sky column analysis, audit indexing. Decisions are made on the main thread; work is done off it — a worker computes *what* should happen, only the main thread commits it. Never block the main thread on a future (no `.join()`/`.get()`).
- **I/O layer** (dedicated single writer) — audit log append, persistence serialization.

Core design principles from the spec (§1) that should guide any implementation choice: OPAC is the single source of truth for party/membership data — this mod only stores city/checkpoint/war/audit state keyed by OPAC party UUID; nothing is hardcoded (every gameplay constant, including tier ladders and payment values, is config-driven and validated at config load); griefing is prevented where possible and otherwise reversible via an audit log with rollback; there is no offline raiding or offline evasion — capture and war participation require live players.

Package root: `org.pixelfire.nationwars` (`mod_group_id` in `gradle.properties`), source under `src/main/java/org/pixelfire/nationwars`.
