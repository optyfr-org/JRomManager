# JDK 25 AOT cache for jpackage launchers

## Goal

Ship a HotSpot AOT cache (JEP 483 / 514 / 515) with each of the five jlink/jpackage launchers so cold start and early warmup improve, without replacing the existing GraalVM native installers.

AOT here is not GraalVM `native-image`. The JVM still runs. Expected gain on a class-heavy start is on the order of the JDK's own figures (roughly 30–50% off startup). It will not beat the native binaries. Do not treat a smaller gain as a failed build; record the numbers.

## Decisions

- Scope: jpackage image only (`JRomManager`, `JRomManagerSwing`, `JRomManagerCLI`, `JRomManagerServer`, `JRomManagerFullServer` in root `build.gradle.kts`).
- One `.aot` file per launcher. Flags differ (`--enable-native-access` is present on FX/Swing/CLI and absent on the servers), and JEP 483 requires that option to match. Do not share a cache across launchers.
- Prebuild on the release OS with **that image's** `bin/java`, after `jlink`, before `jpackage`. Never commit caches. Never train with the CI JDK and run on the jlink image.
- Install path: `$APPDIR/aot/<Launcher>.aot`, referenced from launcher JVM args. jpackage expands `$APPDIR` in `--java-options`. Escape `$` in the Kotlin DSL (`\$APPDIR`).
- Production mode is the default (`AOTMode=auto`). A missing or rejected cache warns and continues. Do not ship `-XX:AOTMode=on` (JDK 27 renames it to `required`); a stale cache must not block startup.
- Training loads startup **and** one fixture: a new tiny DAT (one machine, one rom) under `dist/aot-fixture/`, copied into a temp work dir. It is data, not a module-path entry. Do not use the MAME 0.288 test DATs.
- `gradle run`, the zip dist (`build/distributions`), Docker (`prep_docker.sh`), and GraalVM native installers are out of scope.
- Do not enable ZGC on these launchers. JDK 25 AOT does not support it (JEP 516 is JDK 26).

## Hard gate: `--add-opens`

JEP 483 ignores the cache if `--add-exports`, `--add-opens`, `--add-reads`, `--illegal-native-access`, `--limit-modules`, `--patch-module`, or `--upgrade-module-path` is present. Every launcher currently passes `--add-opens java.base/java.lang.invoke=ALL-UNNAMED` (Fory on JDK 25). FX also opens `javafx.controls/javafx.scene.control.skin` (column autofit; already caught and skipped in `ProfilePanelController`).

Before any cache wiring:

1. Build the jlink image.
2. Start each launcher with its current JVM args **minus** both `--add-opens` flags. Keep `--enable-native-access`, `-XX:MaxRAMPercentage=75`, `--sun-misc-unsafe-memory-access=allow`, and `-Dfile.encoding=UTF-8`.
3. Load the tiny DAT, run one scan, and round-trip a cache/report through `ForyPersistence`.

If Fory or startup fails, **stop**. Do not remove the flags, do not ship caches, and write the blocker in the PR. Leave `test` / `run` `--add-opens` in `jrm.java-conventions.gradle.kts` alone either way; those are not the packaged launchers.

If the spike passes, delete both `--add-opens` entries from the five `jvmArgs` lists in `build.gradle.kts` only.

## Training runs

Use the image `java`, the same module (`-m jrm.merged.module/<main>`), and the same JVM args the shipped cfg will have, plus `-XX:AOTCacheOutput=<file>`. JEP 514 trains and assembles on exit. The process must exit 0. Timeout and fail the build if it does not.

Do not put training-only flags in the shipped cfg. Pass fixture location and the train switch as `-D` properties on the training command only (`jrm.aot.train`, `jrm.aot.fixture`). Confirm during smoke that an extra `-D` on training does not make production reject the cache. If it does, keep the properties off the production command (that is the intended shape) and drop any property the smoke shows is compared.

| Launcher | Train | Then |
| --- | --- | --- |
| CLI | Non-interactive stdin: `load <fixture>\nscan\nexit\n` | EOF/`exit` already returns |
| Server, FullServer | `--bind 127.0.0.1 --http <free-port> --work <temp>`. Property hook after Jetty starts: `Profile.load` + one `Scan`, then `stop()` / `terminate()` so the JVM exits without `System.exit` during cache write | GET `/` once before stop, to load the static servlet |
| JavaFX, Swing | Property hook: show the main window, load the fixture profile, scan once, `Platform.exit()` / dispose and return | Linux CI is headless: `xvfb-run`. Do not add `-Dprism.order=sw` to shipped args. GPU-only classes missed in training load normally later |

Server/FullServer have no command stream on Linux (`waitStop` joins Jetty unless `-d` or Windows). Do not pass `-d` in training; debug mode is not a production flag. The property hook is the clean exit.

Windows server training can use the same hook. Do not rely on piping `stop` only on Windows.

The hook lives behind `jrm.aot.train` and must be a no-op when the property is absent. No user-facing flag.

## Build wiring

- New Gradle task `aotCaches` on the root project: `dependsOn` the beryx `jlink` image task; `jpackage` `dependsOn` `aotCaches`.
- Write the five files into the image at `app/aot/` (whatever directory jpackage treats as `$APPDIR`; confirm against a generated `.cfg` before freezing the path).
- Add `-XX:AOTCache=\$APPDIR/aot/<Launcher>.aot` to each launcher's `jvmArgs` only after the add-opens gate passes.
- If beryx resolves `$APPDIR` at config time or drops it, patch the generated `.cfg` `[JavaOptions]` after image creation and before `jpackage`. Do not hard-code an absolute path.
- Caches are OS/arch/JDK-build specific. The existing release jobs already split win-x64, linux-x64, macos-x64, macos-aarch64. No cross-copy. Linux jpackage job must install `xvfb` before `aotCaches`.
- Skip `aotCaches` when `jpackage` is not in the task graph, so a normal `build`/`test` does not train.

## Failure modes

- Spike fails: stop, as above.
- Training non-zero or timeout: fail `jpackage`. Do not publish an installer with a missing cache for a launcher that advertises `-XX:AOTCache`.
- Smoke with `-XX:AOTCache=... -XX:AOTMode=on` prints an AOT warning or exits non-zero: fail. Typical causes: leftover `--add-opens`, module/`--enable-native-access` mismatch, cache built with a different `java` than the image.
- Jetty webapp class loaders and signed classes are not cached (JEP 483). JDK and merged-module classes still are. Do not try to fix that here.
- Custom class loaders used by Fory-generated code may also miss the cache. The fixture round-trip is what we can cache; the rest loads JIT.
- Installer growth: five caches can add tens to a few hundred MB. Record sizes in the PR. Do not compress or strip caches by hand; the format is private.

## Validation

1. Spike (gate) on one OS before editing launcher flags.
2. After wiring, on that same OS, time each launcher to a ready marker (CLI: process exit after `exit`; server: port accept; FX/Swing: first stage shown) with the cache and with `-XX:AOTMode=off`. Record both times and cache file sizes in the PR. No percentage gate.
3. Smoke each launcher with `-XX:AOTMode=on` and the shipped JVM args (no train property). Must exit 0 with no AOT rejection warning.
4. Confirm a normal user start (no train property) does not load the fixture, does not exit early, and still loads/saves a Fory cache after a real profile open.
5. `jpackage` on the four release OS/arch jobs produces five caches each. Native release workflow is unchanged.

## Out of scope

- GraalVM native-image changes.
- Zip dist, Docker image, `gradle run`.
- JEP 544 AOT code compilation (not in JDK 25).
- Sharing one cache across launchers.
- First-launch regeneration under the user home.
