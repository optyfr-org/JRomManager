# Coalesce FX progress UI updates

Replace per-call `Platform.runLater()` in `ProgressTask` with a Task-style coalesced `ObjectProperty<PData>`. Workers only mutate state; at most one FX runnable is pending; the controller applies the latest snapshot.

## Problem

`ProgressTask.sendSetProgress` already throttles to 100ms, but every accepted pulse still:

1. Copies `PData`
2. Queues `Platform.runLater(() -> controller.setFullProgress(lastPData))`

Scan / `ProgressInputStream.read` still flood the FX event queue. Each apply SAX-parses every info row via `NeutralToNodeFormatter.toNodes` and rebuilds nodes.

`valueProperty` / `progressProperty` cannot be reused: `value` is the Scan/Profile/Fix result, and one double cannot cover 3 bars + N thread rows.

## Decision

Coalesced `ObjectProperty<PData>` using the `Task.updateValue` `AtomicReference`/`AtomicBoolean` pattern. Drop the 100ms throttle. Structural changes (`setInfos` / `extendInfos` / `clearInfos`) travel in the same snapshot so layout and values cannot race.

`canCancel` and `close()` stay as rare direct FX calls (`canCancel` already `runLater`; `close()` is already invoked from `succeeded`/`failed` on the FX thread).

`Option.LAZY` becomes a no-op for FX: coalesce already drops extra hops.

## Implementation

### 1. `ProgressTask` — publish, do not hop on every mutation

In `jrmfx/src/main/java/jrm/fx/ui/progress/ProgressTask.java`:

- Add `private final AtomicBoolean scheduled = new AtomicBoolean();`
- Add `private final ObjectProperty<PData> progressData = new SimpleObjectProperty<>();`
- Expose `progressDataProperty()` (package or public).
- Replace `sendSetProgress` / the `runLater` calls in `setInfos`, `extendInfos`, `clearInfos` with `publish()`:

```java
private void publish() {
    if (scheduled.compareAndSet(false, true)) {
        Platform.runLater(() -> {
            scheduled.set(false);
            final PData snapshot;
            synchronized (this) {
                snapshot = new PData(this.data);
            }
            progressData.set(snapshot);
        });
    }
}
```

- Keep mutating `this.data` under the existing `synchronized` methods (`setInfos`, `extendInfos`, `setProgress`, `setProgress2`, `setProgress3`, `clearInfos`).
- Copy **on the FX thread**, once per pulse — not on every `setProgress`. `ProgressInputStream` can call `setProgress` per read without allocating snapshots.
- If already on the FX thread (`Platform.isFxApplicationThread()`), apply immediately (`progressData.set(new PData(data))`) without scheduling. Matches `Task.updateValue`.
- Delete `lastEvent`, `lastPData`, and the 100ms / force / LAZY gate in `sendSetProgress`. Always `cleanup()` for pb1 then `publish()`.
- Leave `canCancel(boolean)` as a one-shot `runLater` (rare). Leave `close()` as a direct `progress.close()`.

### 2. `ProgressController` — bind once, apply snapshot

In `jrmfx/src/main/java/jrm/fx/ui/progress/ProgressController.java`:

- In `setTask`, subscribe to `task.progressDataProperty()` and apply the current value if non-null (worker may have published before bind; today bind happens in the `Progress` constructor **before** `Thread.startVirtualThread`, so this is a safety net).
- On each snapshot:
  1. If `threadCnt` / `multipleSubInfos` differ from the current panels, call `setInfos` or `extendInfos`.
  2. Call `setFullProgress(pd)`.
- In `setFullProgress`, skip `NeutralToNodeFormatter.toNodes` when the info/subinfo string for that row is unchanged vs the last applied string (store `String[] lastInfos` / `lastSubinfos`). SAX + new `Label`s every pulse is the remaining FX-thread cost.
- Keep the existing progress-bar skip (`(int)(bar.getProgress()*100) != (int) perc`).

### 3. Tests

`ProgressTaskTest` mocks `Platform.runLater` and does not execute the runnable. After this change:

- State mutations (`getCurrent`, cancel, setInfos) must still pass without running the runnable.
- Add a test that N rapid `setProgress` calls queue `runLater` **once** (verify `Platform.runLater` invoked at most once while the first runnable has not run; after it runs, a later `setProgress` may queue again).
- Existing tests that stub `runLater` as no-op remain valid because worker-side fields (`pb1.val`, etc.) update before `publish()`.

`ProgressControllerTest` (TestFX): add a case that two `setFullProgress` calls with the same info strings do not replace pane children (identity or child count unchanged).

Do **not** run unrelated subproject tests. After implementation:

```
./gradlew :jrmfx:test --tests "jrm.fx.ui.progress.*" -x :jrmcore:test -x :jrmcli:test -x :jrmserver:test -x :jrmstandalone:test -x :WebClient:test
```

Never use global `-x test`.

## Out of scope

- `jrmstandalone` Swing progress, `jrmserver` WebSocket progress.
- Changing `ProgressHandler` or scan call sites (`DirScan`, `ProgressInputStream`).
- Replacing `NeutralToNodeFormatter` itself (only skip when the string is unchanged).
- AnimationTimer.

## Risks

- **Stale snapshot vs concurrent mutate:** copy is under `synchronized (this)` on the FX thread; worker methods that touch `data` must stay `synchronized` (`setProgress` is currently **not** synchronized — only `sendSetProgress` is). Make `setProgress` / `setProgress2` / `setProgress3` / `clearInfos` synchronized, or synchronize the `data` mutations and the FX copy on the same lock.
- **Layout race:** first snapshot with new `threadCnt` must rebuild panels before writing labels. Apply layout in the same listener as `setFullProgress`.
- **Shutdown:** a `runLater` may fire after `close()`. `setFullProgress` / `setInfos` must no-op if the scene/window is already hidden (`panel.getScene() == null` or window not showing).
