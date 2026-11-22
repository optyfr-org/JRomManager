### Changed
- Third-party dependencies bumped: GraalVM build tools 1.1.18, GWT 2.13.1, Jetty 12.1.15, H2 2.5.252, Fory 1.7.8, Logback 1.6.8, SevenZipJBinding 26.06, Gson 2.15.0, JLine 4.7.0, Commons Codec 1.23 / Lang3 3.22 / Daemon 1.6.1, Lombok 1.18.48; JCommander migrated from `com.beust:1.82` to `org.jcommander:3.0`; Docker base image digest and WebClient submodule pointer refreshed
- Server password hashing moved from argon2-jvm (JNI) to BouncyCastle's pure-Java Argon2: same `$argon2id` MCF format and parameters, so existing hashes verify unchanged and no DB migration is needed; drops the JNA/argon2 native libraries from the server, which also removes that native-image failure class
- Server native images now enable native access at runtime (as the GUI image already did), covering JNI libraries such as SevenZip

### Fixed
- Native binaries parse CLI arguments again with JCommander 3 (validators/converters registered for GraalVM reflection) — fixes the `Can't instantiate validator: NoValueValidator` startup crash on native `jrmfx`/`jrmserver`/`jrmcli`
- SevenZip native resources follow the 26.06 layout (`AllMac`, `Linux-arm64`, `Windows-arm64`): restores 7-Zip support in macOS native images, where the old `Mac-x86_64`/`Linux-aarch64` globs matched nothing
- Server login with Argon2-hashed passwords no longer throws when the native library can't load (deny-with-log, BCrypt fallback for new hashes); malformed stored hashes also deny instead of failing the request
- Zip re-scan no longer risks an NPE when entries were already loaded without a file header (`EntryUpdater` resolves the header up-front)
- Deterministic fix-task ordering: `Container`/`ContainerAction` use explicit total-order comparators instead of an `equals`-inconsistent `Comparable`
- Profiles tree/list responses no longer report `endRow` of -1 on empty results
- Requests with an unsupported `Accept-Language` header no longer fail the session servlet (falls back to the default locale instead of passing null to the bundle lookup)
