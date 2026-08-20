# Nations & Checkpoints (`nationwars`)

A server-side territorial warfare mod for Minecraft Forge. Player parties
([Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims))
are **nations**. Nations found **cities** anchored by a City Core block,
defended by capturable **checkpoints**. During a declared **war**, an
attacking coalition that holds every checkpoint of a city **occupies** it;
ownership only changes hands at a negotiated (or staff-imposed) peace
settlement.

See [`docs/warfront-spec.md`](docs/warfront-spec.md) for the full design —
data model, threading model, war lifecycle, config, and more.

**Target environment**

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 (`[47,)`) |
| Java | 17 |
| Hard dependency | Open Parties and Claims (OPAC) |

## Local setup

1. Install a JDK 17 distribution (e.g. [Temurin 17](https://adoptium.net/)) and make sure `JAVA_HOME` points at it.
2. Clone the repo and run the Gradle wrapper once to bootstrap the workspace:

   ```sh
   ./gradlew --version
   ```

3. Generate IDE run configurations:

   - **IntelliJ IDEA**: open the repo as a Gradle project, then run the generated `genIntellijRuns` task (or use *Run > Edit Configurations*, the ForgeGradle-provided `client`/`server`/`data` configs appear automatically after an initial sync).
   - **Eclipse**: `./gradlew genEclipseRuns`, then import as an existing Gradle project.

4. Launch a dev client or server from your IDE's run configurations (`client`, `server`, `gameTestServer`, `data`), or from the CLI:

   ```sh
   ./gradlew runClient
   ./gradlew runServer
   ```

   The dev server/client working directory is `run/` (`run-data/` for the `data` config) and is gitignored — drop OPAC's dev-time jar in `run/mods/` (or as a `libs/` flatDir dependency, see `build.gradle`) since it's a hard dependency.

## Building

```sh
./gradlew build
```

The mod jar is written to `build/libs/`. Local builds that aren't on a
release tag get an axion-derived `X.Y.Z-SNAPSHOT` version (see
[Versioning and releases](#versioning-and-releases)).

Useful tasks:

| Task | Purpose |
|---|---|
| `./gradlew build` | Compile, test, and produce the mod jar |
| `./gradlew runClient` / `runServer` | Launch a dev instance |
| `./gradlew runData` | Run data generators (`src/generated/resources`) |
| `./gradlew runGameTestServer` | Run registered game tests headlessly |

## Versioning and releases

There's no version number to hand-edit anywhere in the repo. Every push to
`main` or `dev` (merged PRs only) computes the next version from git tags
and [Conventional Commits](https://www.conventionalcommits.org/) prefixes
via [`.github/scripts/next-version.sh`](.github/scripts/next-version.sh):

- Start from the highest existing `vX.Y.Z` release tag.
- If any commit since that tag has a `feat:` (or `feat(scope):`) subject, bump **minor** (`X.Y.Z` → `X.(Y+1).0`).
- Otherwise (`fix:` or anything else) bump **patch** (`X.Y.Z` → `X.Y.(Z+1)`).

That computed version is used differently per branch:

- **`main`** — [`release-main.yml`](.github/workflows/release-main.yml) tags the commit `vX.Y.Z`, builds, and publishes a GitHub Release with the jar attached.
- **`dev`** — [`release-dev.yml`](.github/workflows/release-dev.yml) never tags a release; it builds `X.Y.Z-dev.N` and publishes a GitHub **pre-release**. `N` is the count of existing `vX.Y.Z-dev.*` tags for that same prospective version, plus one — so it restarts at `1` as soon as the prospective `X.Y.Z` changes (e.g. once a `feat:` lands on `dev`, the next pre-release jumps straight to the new minor's `-dev.1`, rather than continuing the old version's counter).

Example: last release was `1.0.0`. A `fix:` PR merges to `dev` → next dev
build is `1.0.1-dev.1`. A `feat:` PR merges next → the prospective version
is now `1.1.0`, so the following dev build is `1.1.0-dev.1`, not
`1.0.1-dev.2`.

Grab the latest stable jar from the repo's [Releases](../../releases) page,
or the latest pre-release build if you want current `dev` work.

## Contributing

`main` and `dev` are protected — all changes land via pull request, no
direct pushes.

- **`dev`** is the active development branch. Target your PRs here.
- **`main`** tracks stable releases; `dev` is merged into `main` when it's ready to cut a release.

Workflow:

1. Branch from `dev`: `git checkout -b feature/your-change dev`.
2. Make your change. Keep it scoped — see [`docs/warfront-spec.md`](docs/warfront-spec.md) for the design constraints a change should respect (e.g. the threading model in §4, "nothing is hardcoded" in §1).
3. Open a PR into `dev`, titled with a [Conventional Commits](https://www.conventionalcommits.org/) prefix — `feat: ...` or `fix: ...`. On squash merge the PR title becomes the commit subject that [`next-version.sh`](.github/scripts/next-version.sh) reads to decide the version bump (see [Versioning and releases](#versioning-and-releases)).
4. Once merged, CI publishes a `-dev.N` pre-release automatically — no manual version bump needed.

## License

MIT — see [`LICENSE`](LICENSE). Portions of this repository (the Forge MDK
scaffold: `LICENSE.txt`, `CREDITS.txt`, `README.txt`, `changelog.txt`,
`gradlew`/`gradlew.bat`, `gradle/`) are provided by the Forge project under
their own terms.
