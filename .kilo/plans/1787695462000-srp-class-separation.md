# SRP class separation (21 types)

Keep public facades. Extract one-job collaborators the same way as `MameLaunch` (jrmcore, no UI) + `MameLauncher` (toolkit adapter). No algorithm or persist-path changes.

Aikido flagged 23 types. Three are already focused and stay out of scope.

## Goal

Split mixed responsibilities out of 21 remaining types so each facade only orchestrates. Callers keep the same public type names and method signatures.

## Constraints

- Facade class names stay; new types do one job.
- Same package as the facade. Package-private unless another package already calls the moved API (then public, no new module exports).
- Do not change algorithms. DAT/filesystem walks stay iterative, depth 100, canonical-path cycle detection (`jrm.fs_walk.iterative_depth_cycle`).
- Dir2Dat / dest-path checks: resolve through `PathAbstractor`; no raw workspace-string containment (`jrmserver.dir2dat.no_raw_workspace_check`).
- Persist (`ProfileNFO`, `Report.save/load`, `DirUpdaterResults`, `TrntChkReport`) stays on `SignedObjectStore` with the same codecs. `Report.write(Session)` is a plaintext log, not SignedObjectStore.
- Tests: that module only. Never global `-x test`. Exclude unrelated subprojects. Add serialize-roundtrip tests only if a persist type moves (none should).
- One extract → compile → that module’s tests → next. No cross-wave mega-PR.
- Do not invent `SoftwareExporter` unless a later change also moves `Machine`/`Rom`/`Disk` export (same DAT-writer-on-entity pattern). Wave 1 skips `Software`.

## Pattern (already done — do not re-merge)

| Core | UI adapter |
|---|---|
| `jrmcore/.../manager/MameLaunch.java` — argv + validation | `jrmfx/.../profile/MameLauncher.java` — dialogs + `ProcessBuilder` |

Standalone `ProfileViewer` still inlines launch (~677–704). Wave 3 adds `jrm.ui.profile.MameLauncher`.

## Decisions (locked)

| Topic | Decision |
|---|---|
| `Software` | Skip. Entity + `export` stay. No `DatXmlExport` in this plan. |
| `DatFileSearch` vs `ProfileNFO.list` | **Different walks.** Extract `searchDats` only. Do **not** route `ProfileNFO.list` through it. `list()` is non-recursive, skips `cache\|properties\|nfo\|jrm1\|jrm2`, loads NFO. `searchDats` is iterative depth 100 + cycle detection, collects `.xml`/`.dat` and launchable MAME binaries. Standalone import uses a file chooser, not this walk. |
| `DatFileSearch` home | `jrmcore` `jrm.profile.manager.DatFileSearch` so the walk lives with other FS algorithms. Move `ProfilePanelControllerSearchDatsTest` to jrmcore. FX controller delegates. |
| ZIP extract on remote chooser | Stays on `RemoteFileChooserXMLResponse` with `CachePathGuard`. Do not extract `ZipExtractOps`. |
| SQL | `SqlIdentifiers` + fragment builders off `SQLUtils`. `SqlDialect` (H2 flags + `ANY(?)` rewrite) off `SQL`. `BeanMapper` only if `convertBeanToMap`/`updateBeanFromMap` still sit on `SQL` after the dialect move. |
| ProfileActions | Four package-private ops: import, scan, fix, settings. Facade keeps JSON in/out + `Worker` + WS notify. |
| Visibility | Same package; package-private first. |

## Out of scope

- `ActionsMgr` — cmd switch only; work already in `*Actions`
- `AdminXMLResponse` — user CRUD XML only
- jrmfx `ProfileViewer` — launch already in `MameLauncher`
- `Software` / `Machine` / `Rom` / `Disk` export extraction
- Changing `SignedObjectStore` load order, HMAC, or codecs
- Changing zip-slip (`resolveZipEntry`, `resolveTorrentEntry`) or SHA-1 piece-boundary hashing

---

## Wave 1 — jrmcore

None of the extract types exist yet. Order: smallest + existing tests first.

### 1. `ThreadOffsetSlots`

- From: `jrm.misc.MultiThreading` (407), `jrm.misc.MultiThreadingVirtual` (289)
- New: `jrm.misc.ThreadOffsetSlots`
- Move: `activeThreads`, `freeOffsets`, `maxActive`, `count`, `getOffset()`, `freeOffsets()`, `allocOffset()`, `freeOffset()`
- Keep: executor, `start()`, adaptive CPU/load, virtual semaphore, `CalledWith`
- Do not change: poll free slot else `activeThreads.size()`; recycle in `finally` even on throw
- Tests: `MultiThreadingOffsetTest` (both classes, throw + success → `freeOffsets()==[0]`, `getOffset()==-1`)

### 2. `BackupArchiveStore`

- From: `jrm.profile.fix.actions.BackupContainer` (167)
- New: same package `BackupArchiveStore`
- Move: `zipfiles`, `getZipFile`, `closeAllFS`, dest-dir + CRC32 parent-path naming (`%08x/<crc2>.zip`)
- Keep: `getInstance*`, `doAction` (`BackupEntry` + zip4j `FASTEST`)
- `Fix` still calls `BackupContainer.closeAllFS()` (facade may delegate)
- Tests: `jrm.profile.fix.actions.*` (`BackupEntryUnsupportedTargetTest`). No dedicated container test; do not add unless `doAction` behavior is touched.

### 3. `DatDirExpander`

- From: `jrm.batch.DirUpdater` (164)
- New: same package `DatDirExpander`
- Move: first `update` branch — list `xml`/`dat`, sort, copy profile settings, mkdir dests from DAT basename, pair `datlist`/`dstlist`
- Keep: SDR loop, `Profile.load`, Scan/Fix, stats, `DirUpdaterResults.save`, `report.save`
- Persist unchanged: `DirUpdaterResults` + `Report` → `SignedObjectStore.Codec.REPORT`
- Tests: `DirUpdaterTest`, `DirUpdaterResultsTest`

### 4. `TorrentPieceHasher` + `TorrentArchiveExtractor`

- From: `jrm.batch.TorrentChecker` (826)
- New: same package
- Hasher gets: `CheckBlocksData`, `checkBlocks*`, `applyPieceHash`, `finalizePiece`, `revalidateRemainingFile`, `hashStream`, `getFileStram`
- Extractor gets: `detectArchives`, `collectCandidateArchives`, `excludeActualTorrentFiles`, `isArchive`, `unarchive`, `unzip`, `resolveZipEntry`
- Keep: ctor/`MultiThreadingVirtual`, `Options`, `check`, filename/filesize path, `removeUnknownFiles`, `resolveTorrentEntry`, progress/`ResultColUpdater`
- Do not change: leftover last-piece hash; zip-slip `startsWith(destDir)`; wrong-size delete must not escape dest
- Persist: `TrntChkReport` stays `Codec.TRNTCHK`
- Tests: `TorrentCheckerTest` (FILENAME/FILESIZE/SHA1, piece boundary, REMOVEUNKNOWN/WRONGSIZE, zip-slip, cancel)

### 5. `ReportLogWriter`

- From: `jrm.profile.report.Report.write(Session)` (Report is 1174)
- New: same package `ReportLogWriter`
- Move: `write` + `parseReportModes`, `createReportDirectory`/`File`, header/settings/stats/body, grouping, `classifySubject`, `compareNotes`, `ReportMode`
- Keep: `AbstractList<Subject>`, filter/clone/flush, `save`/`load`
- `Scan` still calls `report.write(session)`
- Tests: `ReportSummaryTest`. No `write()` unit test today; do not require a new one unless the log format is accidentally touched.

### 6. ProfileNFO HTML renderer

- From: `jrm.profile.manager.ProfileNFO` (489)
- New: same package, e.g. `ProfileNFOHtml` (or package-private renderer)
- Move: `getHTMLVersion`, `getHTMLHaveSets`/`Roms`/`Disks`, `getHTMLCreated`/`Scanned`/`Fixed` and date/unknown constants
- Drop `StatusRendererFactory` from `ProfileNFO`; renderer uses the factory
- Keep: `load`/`save`/`relocate`/`list`/`delete`/JRM XML, `bindToProfileFile`
- Persist unchanged: `Codec.CACHE` + path rebind
- Tests: `ProfileNFOHtmlVersionTest`, `ProfileNFOPathRebindTest`

### Wave 1 verify (after each extract, then once at end)

```
./gradlew :jrmcore:test --tests "jrm.misc.MultiThreadingOffsetTest" --tests "jrm.profile.fix.actions.*" --tests "jrm.batch.*" --tests "jrm.profile.report.*" --tests "jrm.profile.manager.ProfileNFO*" -x :jrmcli:test -x :jrmserver:test -x :jrmstandalone:test -x :jrmfx:test -x :WebClient:test
```

---

## Wave 2 — jrmserver

SQL first, then actions, then XML/FS.

### 7–8. SQL split

- `jrm.fullserver.db.SQLUtils` (~703) → `SqlIdentifiers` (`requireSqlIdentifier`, `backquote`, `requireSelectStatement`) + fragment builders (`append*`, `makeCols`/`makeSet`, `appendParam`). Interface shrinks to `count`/`update`/`query`/`getDb`/`getContext`/`getColumnList`.
- `jrm.fullserver.db.SQL` (~752) → `SqlDialect` (H2 product flags + `findArrayParam`/`convertArrayParams` `ANY(?)` → `IN(...)`). Optional `BeanMapper` for Introspector map conversion.
- Keep: `QueryRunner` execution, schema create/drop/link, `close`
- Tests: `SQLUtilsTest`, `SQLTest`

### 9. `AbstractServer` (~410)

- New: `jrm.server.ServerResourceLocator` (FS / classpath / `jrt:/jrm.merged.module/webclient/` / jar FS), `jrm.server.ServerPaths` (`getWorkPath`, `getLogPath`, `getClientPath`, `getCertsPath`, `getPath`)
- Keep: Jetty lifecycle, gzip, shutdown hook, `waitStop`/`terminate`/`isStarted`/`isStopped`
- Tests: `AbstractServerTest`

### 10. `ProfileActions` (~877)

- New package-private in `jrm.server.shared.actions`: `ProfileImportOps`, `ProfileScanOps`, `ProfileFixOps`, `ProfileSettingsOps`
- Move: `performMameImport`/`doImport`; `performScan`/`runScanAndNotify`/`hasScanAgain`; `performFix`; `importSettings`/`exportSettings`/`setProperty`
- Keep: public `imprt`/`load`/`scan`/`fix`/`loaded` + WS `scanned`/`fixed`/`imported` + dest-path checks via `PathAbstractor`
- Tests: `ProfileActionsTest`

### 11. `TrntChkActions` (~324)

- New: `TrntChkSessionBridge` — session options (`TrntChkMode` + `TorrentChecker.Options`) + SDR persist (`trntchk_sdr` + `saveSettings`) + WS `updateResult`/`end` payloads
- Keep: `start` → worker; facade may still expose `updateResult`/`clearResults`/`end` as thin delegates
- Tests: `TrntChkActionsTest`

### 12. `AnywareListXMLResponse` (~503)

- New: `AnywareListQuery` — `getFilter`/`filter*`, `getSorter`, `buildStream`/`buildList`, `find`, `selectAll`/`None`/`Invert`
- Keep: XML `fetch`/`update`/`custom` envelope, `writeRecord`
- Tests: `AnywareListXMLResponseTest`

### 13. `RemoteFileChooserXMLResponse` (~677)

- Promote nested `CaseInsensitiveFileFinder` to top-level same package (tests already nest `CaseInsensitiveFileFinderTest`)
- New: `RemoteFsCommands` — list / mkdir / rename / recursive delete
- Keep: XML routing, nested `Options`, ZIP extract + `CachePathGuard` + zip-slip tests
- Tests: `RemoteFileChooserXMLResponseTest`

### Wave 2 verify

```
./gradlew :jrmserver:test --tests "jrm.fullserver.db.*" --tests "jrm.server.AbstractServerTest" --tests "jrm.server.shared.actions.ProfileActionsTest" --tests "jrm.server.shared.actions.TrntChkActionsTest" --tests "jrm.server.shared.datasources.AnywareListXMLResponseTest" --tests "jrm.server.shared.datasources.RemoteFileChooserXMLResponseTest" -x :jrmcli:test -x :jrmcore:test -x :jrmstandalone:test -x :jrmfx:test -x :WebClient:test
```

---

## Wave 3 — jrmfx then standalone

### 14. `Dir2DatController` (~448)

- New: `jrm.fx.ui.Dir2DatRun` — validate src/dst, headers → options, `EnumSet<DirScan.Options>`, `new Dir2Dat`
- Keep: FXML, choosers, prefs, `ProgressTask`
- Dest paths through `PathAbstractor` if any containment check is added (do not add raw string checks)
- Tests: `Dir2DatControllerTest`

### 15. `ProfilePanelController` (~1127)

- Move `searchDats` + `PendingDat` + `MAX_DAT_SEARCH_DEPTH` to `jrm.profile.manager.DatFileSearch`
- Move `ProfilePanelControllerSearchDatsTest` to jrmcore; controller test keeps UI coverage
- New: `jrm.fx.ui.ProfileImportTasks` — `ImportDatTask`, `UpdateFromMameTask` (copy NFO, relocate)
- Keep: tree/table/menus
- Tests: `:jrmcore:test --tests "jrm.profile.manager.DatFileSearch*"` and `:jrmfx:test --tests "jrm.fx.ui.ProfilePanelController*"`

### 16. `SettingsPanelController` (~448)

- New: `jrm.fx.ui.MemoryStatusMonitor` — 20s `scheduleAtFixedRate` + `updateMemory`
- Keep: prefs bindings (threads, stylesheet, backup dest, zip/7z, debug)
- Tests: `SettingsPanelControllerTest`

### 17. standalone `ProfileViewer` (~926)

- New: `jrm.ui.profile.MameLauncher` mirroring jrmfx (`MameLaunch` argv + `ProcessBuilder` + Swing dialogs)
- Keep: tables, filters, export, search
- Tests: none today; do not require a new suite unless launch args diverge from `MameLaunchTest`

### 18. standalone `ProfilePanel` (~670) + `DirTreeModel` (~83)

- New: `jrm.ui.profile.manager.ProfileDirIo` — rename / delete / copy import files / drop cache
- `DirTreeModel.treeNodesChanged` / `treeNodesRemoved` delegate FS mutate here; model only fires tree events
- Keep: Swing wiring on `ProfilePanel`
- Tests: `DirNodeTest` still passes; no ProfilePanel test today

### Wave 3 verify

```
./gradlew :jrmcore:test --tests "jrm.profile.manager.DatFileSearch*" --tests "jrm.profile.manager.MameLaunchTest" -x :jrmcli:test -x :jrmserver:test -x :jrmstandalone:test -x :jrmfx:test -x :WebClient:test
./gradlew :jrmfx:test --tests "jrm.fx.ui.Dir2DatControllerTest" --tests "jrm.fx.ui.ProfilePanelController*" --tests "jrm.fx.ui.SettingsPanelControllerTest" --tests "jrm.fx.ui.profile.ProfileViewer*" -x :jrmcli:test -x :jrmcore:test -x :jrmserver:test -x :jrmstandalone:test -x :WebClient:test
./gradlew :jrmstandalone:test -x :jrmcli:test -x :jrmcore:test -x :jrmserver:test -x :jrmfx:test -x :WebClient:test
```

---

## Failure modes

- Offset slots not recycled on throw → progress UI stuck; `MultiThreadingOffsetTest` must stay green.
- Zip-slip regression in torrent extract or remote chooser extract → path escape; existing tests must stay green; do not rewrite `resolveZipEntry`.
- `searchDats` rewritten as recursive or sharing `ProfileNFO.list` → stack overflow / wrong file set / missing MAME binaries.
- Persist codec or `bindToProfileFile` drift on NFO/Report → cache/report load breaks; do not touch `SignedObjectStore`.
- Raw `%work`/`%shared` string containment on dest paths → false reject of abstract chooser paths.

## Rollout

One extract per commit-sized change. Wave 1 → Wave 2 → Wave 3. Do not land SQL + ProfileActions + XML in one PR. Facades remain the only types other modules should import.
