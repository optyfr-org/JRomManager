### Added
- Java FX interface is now fully compliant with variable font size and has now a setting for choosing a font size from XXS to XXL, the interface will dynamically accomodate as soon as the setting is changed
- GraalVM native binaries for the CLI (`jrmcli`), the JavaFX desktop (`jrmfx`), the server (`jrmserver`) and the full server (`jrmfullserver`): instant startup with no JVM installation required
- Native installers built per role with jpackage: Servers (FullServer + Server + CLI + bundled web client) and Desktop (JavaFX GUI), as MSI on Windows, DEB/RPM on Linux and DMG on macOS (unsigned)
- WebSocket endpoint (`/ws`) for server actions as an alternative to long-polling
- Report view shows a copyable results summary and can export a fixDAT of the missing/partial sets ("Create fixDAT"), in the JavaFX interface and the web client
- MAME launch arguments are sanitized to prevent option injection
- Cache, report and NFO persistence now uses Apache Fory serialization (faster loads and saves) inside an HMAC-signed envelope; legacy unsigned caches are rejected once and transparently rebuilt (breaking: the pre-Fory cache format is no longer readable)
- Configurable deserialization depth limit for persisted data

### Changed
- Server authentication hardening: sessions no longer auto-elevate to admin, unauthenticated session and action requests return 401, the simple server binds to loopback by default (containers set `JRM_SERVER_BIND=0.0.0.0`), and local admin is granted only to loopback clients
- Full server HTTPS now uses the Jetty Java ALPN provider; the Conscrypt/OpenSSL dependency is gone
- Third-party SevenZip and Jtrrntzip artifacts are fetched from JitPack, so dependency resolution no longer needs a GitHub token; BouncyCastle migrated to the `jdk18on` artifacts
- All dependency versions are centralized in `gradle/libs.versions.toml` with dependency locking and verification metadata (reproducible builds; drift is reported as warnings, not errors), plus `refresh-locks` and `update-verification-metadata` helper scripts
- Toolchain upgrades: Java 25, Gradle 9.x, Jetty 12.1.12
- CLI: piped stdin is streamed directly without a missing-terminal warning, and backspace works on Git Bash/MSYS2
- Packaged launchers allow unsafe memory access (no more Fory `Unsafe` warning) and prefer the bundled terminfo database
- Scan performance: indexed sample lookups, less redundant UI work, Fory serializers kept warm in a shared pool, and best-effort progress pushes throttled
- Progress reporting: freed worker slots are marked idle, finished container names stay visible while remaining tasks run, and scan/fix progress stays bound to the operation that created it
- Robustness guardrails against pathological inputs: depth caps on folder/tree/archive traversal and bencoding nesting, capped placeholder recursion
- Concurrency hardening of shared server, desktop and session state (progress handlers, session registries, caches, batch result lists)
- Internal decomposition of 21 oversized classes (profile parsing, scan collaborators, CLI command registry, server actions) with no behavior change
- CI/release: split reusable workflows with SHA-pinned actions, CodeQL extended security queries, SonarQube analysis on GraalVM 25, and per-OS native installer jobs

### Fixed
- Scan and fix: fix progress is closed before scan-again, Dir2Dat progress stays on the started worker, torrent-check rows are isolated from table mutations, progress updates are serialized, torrent report pagination and child/parent links are correct, and archive extractions yielding no file are reported as failures
- Server: empty player counts and torrent pagination can no longer go negative, all non-root profile tree nodes are counted, oversized action message batches are capped, unmatched PUT uploads are delegated, missing dat2dir/torrent identifiers are reported accurately, certificate lookup failures carry an accurate message, the log-level flag is honored, and the temporary-report check-then-act race is gone
- Desktop (Swing): torrent table updates marshaled onto the EDT, memory panel updates on the EDT, compressor table edits persist on any column, failed profile renames warn instead of erroring, and DirTree child deletion follows the event child
- JavaFX: cancelled file choosers tolerated, null fix results guarded, secondary progress bars use a reachable indeterminate condition, compressor scan I/O errors yield an empty stream, and `hashCode`/`equals` contracts fixed for ThreadCnt, Sample, Machine and Entry
- Files and archives: ancestor path lookup keeps on-disk casing, owned zip filesystems are always closed after entry processing, last torrent piece bounds-checked, malformed torrents with non-string dictionary keys are rejected instead of crashing, the single-instance lock is held until process exit, and SevenZip no longer creates a temp directory at init
- Settings: auto-save on change so a crash loses nothing, AppImage portable home honored for desktop config, invalid boolean properties fall back to defaults, and `Log.init` honors the supplied limit/count parameters
- Stabilization of the new native binaries: single-threaded SevenZip compression in-image, JavaFX hardware pipeline with bundled shaders and libraries, Fory round-trip for report subjects, WebSocket reflection entries, and trntchk serializer init cost drastically reduced
- CLI `set`/`settings` alias collision resolved

### Security
- Authenticated report-cache poisoning blocked via class allowlisting and refusal of uploads targeting the reports cache directory
- All cache, scan-data and NFO deserialization paths enforce allowlists with HMAC integrity; unsigned Java serialization streams are rejected
- Path traversal fixes: archive entry paths normalized and validated, torrent extraction contained to the destination directory, fix actions contained to the workspace, remote file chooser and XML responses bounded to their base directories, and dir2dat containment checked on resolved paths
- MAME executable paths validated with pre-execution safety checks; Zip Slip checks coverage, opaque-URI traversal prevention, SQL identifier validation, user-controlled content escaping in renderers, and sensitive values redacted from logs
