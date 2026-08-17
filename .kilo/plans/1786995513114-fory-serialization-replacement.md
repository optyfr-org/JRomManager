# Replace Java serialization with Apache Fory

## Context

All cache/report/NFO persistence goes through `SignedObjectStore` (`jrmcore/src/main/java/jrm/security/SignedObjectStore.java`): JRMH + HMAC-SHA256 + `ObjectOutputStream`. Loads use `DeserializationFilter` allowlists (DEFAULT vs REPORT) and per-call depth (100 vs TrntChkReport `1_000_000`).

HMAC is already a per-workspace 32-byte `SecureRandom` key in `settings/.cache-hmac` (`CacheIntegrityKey`). Path-derived `SHA-256("JRM-CACHE-INTEGRITY-"+workPath)` is rejected. Keep that key; do not reintroduce derivation.

Fory 1.6.1 native mode (`withXlang(false)`) is the Java-only replacement. Strict class registration replaces the filter. Native mode honors leftover `readObject`/`writeObject` via a slow JDK-compat path, so those hooks **must be removed** or the 10–50x gain is lost.

CVE-2026-64606 (lambda registration bypass) is fixed in Fory ≥ 1.4.0. Stay on 1.6.1, keep registration on, do not persist lambdas.

## Locked decisions

| Topic | Choice |
| --- | --- |
| HMAC | Keep `CacheIntegrityKey` random 32-byte key. No HKDF, no path binding. |
| Legacy payloads | Hard cut. Reject JRMH v1, bare `0xACED`, and work-path HMAC. First load rebuilds caches; ProfileNFO stats reset. |
| Encoding | Fory Java-native field serialization + explicit post-load. Not JDK hooks. |
| Schema | `withCompatible(false)` (schema-consistent). Field changes invalidate caches. |
| Instances | Three reused `ThreadSafeFory`: CACHE (depth 100), REPORT (depth 100), TRNTCHK (depth 1_000_000). |

## Architecture

```
write: object → Fory.serialize → HMAC(key, bytes) → JRMH | 0x02 | hmacLen | hmac | fory
read:  verify magic/version/HMAC → Fory.deserialize → afterLoad(root)
```

Envelope version becomes `2`. Same HMAC algorithm and key file. `SignedObjectStore` stays the only production entry point.

Replace `DeserializationFilter.Mode` with `SignedObjectStore.Codec { CACHE, REPORT, TRNTCHK }`. Drop the `maxDepth` argument from public read APIs; depth lives on the Fory instance.

Call sites:

- CACHE: `Profile.loadCache`, `ProfileNFO.load`, `DirScan.load`
- REPORT: `Report.load`, `DirUpdaterResults.load`
- TRNTCHK: `TrntChkReport.load`

Delete `DeserializationFilter` after the switch. Package-private `openObjectInputStream` goes with it.

## Fory setup

New `jrm.security.ForyPersistence` (or similar) owns three static `ThreadSafeFory` instances. Register every persisted class with **explicit numeric IDs** before first use. Reuse instances; never build per call.

Shared builder:

```java
Fory.builder()
    .withXlang(false)
    .requireClassRegistration(true)
    .withCompatible(false)
    .withRefTracking(true)          // Profile cycles, inner this$0, Child trees
    .withMaxDepth(depth)
    .withDeserializeUnknownClass(false)
    .withCheckJdkClassSerializable(true)
    .buildThreadSafeFory();
```

Pin `implementation 'org.apache.fory:fory-core:1.6.1'` on `:jrmcore` only.

JDK 25: add `--add-opens=java.base/java.lang.invoke=ALL-UNNAMED` to subproject `test.jvmArgs` (already opens `java.lang` / `reflect` / `util`) and to jlink launchers in `build.gradle` (`JRomManager`, Swing, CLI, Server, FullServer).

## Registration lists

IDs are per Fory instance. Use disjoint ranges so lists stay readable (e.g. 100–199 shared container types, 200–399 profile data, 400–449 NFO, 500–599 report, 600–619 torrent). Keep a single `registerXxx(ThreadSafeFory)` helper for types shared by CACHE and REPORT.

**CACHE** (Profile, ProfileNFO, DirScan `Map<String,Container>`):

- `Profile`
- `NameBase`, `AnywareBase`, `Anyware`, `AnywareList`, `AnywareListList`
- `Machine`, `Machine.SWList`, `MachineList`, `MachineListList`
- `Software`, `Software.Part`, `Software.Part.DataArea`, `Software.Part.DiskArea`, `SoftwareList`, `SoftwareListList`
- `EntityBase`, `Entity`, `Rom`, `Disk`, `Sample`, `Samples`, `SamplesList`
- `Device`, `Device.Instance`, `Device.Extension`, `Driver`, `Input`, `Slot`, `SlotOption`
- `Container`, `Archive`, `Directory`, `FakeDirectory`, `Entry`
- `ProfileNFO`, `ProfileNFOStats`, `ProfileNFOMame`
- enums: `AnywareStatus`, `EntityStatus`, `Entity.Status`, `Rom.LoadFlag`, `Container.Type`, `Entry.Type`, `Driver.StatusType`, `Driver.SaveStateType`, `Machine.SWStatus`, `Machine.DisplayOrientation`, `Machine.CabinetType`, `Software.Supported`, `Software.Part.DataArea.Endianness`, `Systm.Type`
- `jtrrntzip.TrrntZipStatus`
- JDK if not built-in: `java.io.File`, `java.lang.StringBuilder`, `java.time.Instant`, `EnumSet` of `TrrntZipStatus`

**REPORT** (plus profile-data types because `Subject.ware` and `EntryNote.entity` embed them):

- All CACHE profile-data + container types above
- `Report`, `Report.Stats`
- `Subject`, `SubjectSet`, `SubjectSet.Status`, `ContainerSubject`
- `ContainerUnknown`, `ContainerUnneeded`, `ContainerTZip`, `RomSuspiciousCRC`
- `Note`, `EntryNote`, `EntryExtNote`
- `EntryAdd`, `EntryOK`, `EntryMissing`, `EntryMissingDuplicate`, `EntryUnneeded`, `EntryWrongName`, `EntryWrongHash`
- `DirUpdaterResults`, `DirUpdaterResults.DirUpdaterResult`

**TRNTCHK**:

- `TrntChkReport`, `TrntChkReport.Child`, `TrntChkReport.ChildData`, `TrntChkReport.Status`

Do not register `jrm.security.*`, UI handlers, `Session`, `ProfileSettings`, `CatVer`, `NPlayers`, `Sources`/`Source` (Machine.source is transient; rebuilt after load), or lambdas.

If a first CACHE Profile round-trip fails with an unregistered class, add that class with a new stable ID and a regression test. Do not disable registration.

## Domain changes (required for native-field speed)

Remove every persisted `readObject` / `writeObject` / `serialPersistentFields`. Leaving them makes Fory take the JDK-compat serializer.

Files:

- `NameBase`, `EntityBase`, `Entity`
- `Anyware`, `AnywareList`, `AnywareListList`
- `Machine`, `MachineList`, `MachineListList`
- `SoftwareList`, `SoftwareListList`
- `ProfileNFO`, `ProfileNFOStats`, `ProfileNFOMame`
- `Report`, `Subject`, `SubjectSet`

Keep `implements Serializable` (low churn). Keep existing `initTransient()` methods.

Fory skips `transient` (same as today). Native mode treats reference fields as nullable by default.

### Post-load (Fory does not call constructors or old hooks)

Add a small `afterLoad` on each root (called from `SignedObjectStore` after deserialize, or from each `load`):

**Profile** — walk `machineListList`:

- `AnywareListList` / `AnywareList` `initTransient()` (clear `filteredList`)
- each `Anyware`/`Machine` `initTransient()` (rewire `rom`/`disk`/`sample` `.parent`, reset clones/collision)
- `Machine.initTransient()` also resets `deviceMachines`
- existing `initializeProfile()` still runs (`buildParentClonesRelations`, settings, systems, years, catver, nplayers, filters)

**ProfileNFO** — existing `bindToProfileFile(file)` is enough.

**DirScan map** — no hook. Transient `up2date` / `relAW` default.

**Report** — replace old `readObject` body:

- `subject.parent = report`
- `note.parent = subject`
- rebuild `subjectHash`
- new `FilterPredicate`
- `handler` is already set in `Report.load`

If `ware` is a live `Anyware`, run the same `initTransient` walk on that subgraph.

**TrntChkReport** — `nodes`, `all`, and `Child.parent` are already persistent.

- set transient `uidCnt` to `max(uid)+1` so new nodes do not collide
- new `FilterPredicate`
- `file` / `fileModified` already set in `load`

**DirUpdaterResults** — no hook if `DirUpdaterResult.this$0` round-trips.

### Non-static inner classes

These persist and capture `this$0`: `Machine.SWList`, `Device.Instance`, `Device.Extension`, `DirUpdaterResults.DirUpdaterResult`, `TrntChkReport.Child`.

First implementation keeps them and relies on `withRefTracking(true)`. If Fory cannot restore `this$0`, convert the persisted ones to `static` and pass the outer reference as an explicit field (TrntChkReport.Child would store `TrntChkReport report`).

`Profile.ProfileHandler` is not persisted.

## SignedObjectStore API

`write`: `fory.serialize(object)` instead of `ObjectOutputStream`. Choose codec from a new overload `write(session, file, object, Codec)` or infer from runtime type (`Profile`/`ProfileNFO`/`Map` → CACHE, `Report`/`DirUpdaterResults` → REPORT, `TrntChkReport` → TRNTCHK). Prefer an explicit codec argument at write sites to avoid `instanceof` surprises.

`read`: verify JRMH + version `2` + HMAC, then `fory.deserialize`. Reject v1, `0xACED`, and legacy HMAC with the same `SecurityException` messages (update “Unsigned Java serialization” tests to still pass).

Remove `DeserializationFilter` parameters. `Codec` selects the Fory instance.

## Tests

Replace `DeserializationFilterTest` graph round-trips with Fory + HMAC tests (same fixtures):

- ProfileNFO, Report (`ContainerUnknown` + `TrrntZipStatus`), DirUpdaterResults, DirScan `Map`, TrntChkReport parent/child tree
- Profile: at least one `Machine` with `Rom`/`Disk`/`Sample` and verify `parent` after load
- CACHE Fory rejects a REPORT-only type (and the reverse) — registration is the new allowlist
- Keep `SignedObjectStoreTest`: v2 magic, tamper reject, v1 reject, `0xACED` reject, work-path HMAC reject
- After a failed deserialize, the same Fory instance still reads a valid payload (Fory security guidance)

Run (never global `-x test`):

```
./gradlew :jrmcore:test --tests "jrm.security.*" --tests "jrm.profile.manager.ProfileNFOPathRebindTest" -x :jrmcli:test -x :jrmserver:test -x :jrmstandalone:test -x :jrmfx:test -x :WebClient:test
```

If Profile construction in unit tests is too heavy, add a focused `ForyPersistenceTest` that builds a minimal `Machine`+`Rom` graph without a full DAT parse.

## Risks

- **Unregistered class mid-graph** — first real Profile cache save will surface misses. Fix by adding IDs; do not turn registration off.
- **Inner `this$0`** — fallback: make persisted inner classes static.
- **StringBuilder / File / EnumSet / Instant** — register or add a tiny custom serializer if Fory has no built-in.
- **Report embeds Anyware** — REPORT instance must include the profile-data ID set, not just report classes.
- **TrntChkReport depth** — only the TRNTCHK instance uses 1_000_000. Do not raise CACHE/REPORT.
- **Hard cut** — users lose ProfileNFO have/scanned/fixed until the next scan. Expected.

## Out of scope

- Reading JRMH v1 / bare Java / work-path HMAC
- Changing `CacheIntegrityKey` or `CachePathGuard`
- Fory xlang, IDL, JSON, SIMD module
- Removing `implements Serializable` / `serialVersionUID`
- Rewriting Profile/Report object models beyond hook removal and post-load
