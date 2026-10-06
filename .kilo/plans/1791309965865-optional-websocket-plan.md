# Optional WebSocket (Server + FullServer, GWT autodetect + LPR fallback)

## Goal
Add opt-in WebSocket transport for the actions channel on both `jrm.server.Server` (simple) and `jrm.fullserver.FullServer` (full), with:
- Server-side boolean flag only (client JS autodetects, no build-time flavor).
- WS carries only actions traffic (client JSON cmds inbound, server push outbound); `/session`, `/datasources/*`, `/upload/*`, `/download/*`, `/images/*` stay on plain HTTP.
- When server runs in WS mode it still accepts classic LPR (`POST /actions/cmd`, `GET /actions/init`, `GET /actions/lpr`), so a client behind a proxy/firewall that blocks WS keeps working.

Decisions already taken: flag `--websocket`/`--ws`, default OFF, env override `jrm.server.websocket`; WS-for-actions-only; auth by reusing HTTP session cookie (JSESSIONID) at upgrade time; endpoint path `/ws` (NOT `/actions/ws`, which collides with `ActionServlet`'s `/actions/*` mapping); single active socket per WebSession (new open replaces old); socket-only pushes while WS open (queue paused); transports mutually exclusive (WS open stops LPR); client waits for probe outcome before starting any channel; fallback = full re-init (`GET /actions/init` once, then `/actions/lpr`).

## Current state (verified in code)
- `jrmserver/src/main/java/jrm/server/shared/handlers/ActionServlet.java`: `POST /actions/cmd` -> `new LongPollingReqMgr(sess).process(json)`; `GET /actions/init` -> `doInit(sess)` + `doLPR(resp,sess)`; `GET /actions/lpr` -> 20s blocking poll on `WebSession.getLprMsg()` (LinkedBlockingDeque), batch up to 100, wrap multi in `Global.multiCMD`.
- `jrmserver/.../shared/lpr/LongPollingReqMgr.java` implements `ActionsMgr` (`send`/`sendOptional` -> `session.getLprMsg().add(...)`, `isOpen()->true`); static `cmds` map for `saveAllSettings()`.
- `jrmserver/.../shared/actions/ActionsMgr.java` routes all `cmd` strings; `processActions` updates `session.setLastAction(now)` and rejects unauthenticated sessions.
- `WebClient/src/main/java/jrm/webui/client/Client.java`: after `POST /session`, starts `lprTimer` loop (`/actions/init` once then `/actions/lpr`), `sendMsg` POSTs to `/actions/cmd` via SmartGWT `RPCManager`. Dispatch via `processCmd` -> `EatSleepRaveRepeat`.
- Servers: `jrm/server/Server.java#initialize()` and `jrm/fullserver/FullServer.java#initialize()` build Jetty 12.1.12 + ee9 `ServletContextHandler(SESSIONS)`, `GzipHandler`, session timeout 300s. No websocket deps. FullServer adds Basic auth (`admin`/`user` on `/*`), HTTP/HTTPS/HTTP2 connectors, `ForwardedRequestCustomizer`. Simple Server adds `LocalAdminFilter` on `/*`.
- `WebSession` holds `lprMsg` queue; `close()` poisons queue with `""`; `closeAll()` on terminate. No WS handle.
- No `jakarta.websocket` / Jetty websocket artifacts in `jrmserver/build.gradle`. GWT 2.13, no WS wrapper lib (`debug.gradle` has commented `gwt-websockets`).

## Plan (ordered tasks)

### 1. Server: dependency + flag plumbing
- Add to `jrmserver/build.gradle` (both `main` runtime and native binaries): `org.eclipse.jetty.ee9:jetty-ee9-websocket-jakarta-server:12.1.12` (plus `jakarta.websocket:jakarta.websocket.client-api` only for tests via `jetty-ee9-websocket-jakarta-client` / Jetty client test). Keep version in line with existing Jetty 12.1.12.
- `AbstractServer`: add `protected static boolean websocketEnabled = false` + accessor.
- `Server.Args` and `FullServer.Args`: add `@Parameter(names={"--websocket","--ws"}, description="Enable websocket actions channel (LPR stays as fallback)") private boolean websocket = false;`
- `Server.initFromEnv` / `FullServer.initFromEnv`: add `jrm.server.websocket` env/system-property override. `parseArgs` assigns to `AbstractServer.websocketEnabled`. Log `websocket: true/false` at startup alongside ports.
- Acceptance: `--help` shows flag; default run unchanged (no WS endpoint registered, no behavior change).

### 2. Server: WS endpoint + ActionsMgr implementation (shared, used by both servers)
- New package `jrm.server.shared.ws`:
  - `WsActionMgr implements ActionsMgr`: mirrors `LongPollingReqMgr` but `send(msg)` writes socket-only to Jakarta `Session.getAsyncRemote().sendText(msg)` (NOT to `lprMsg` queue — queue paused while WS open), `sendOptional` same semantics (skip when closed / coalesce rules preserved), `isOpen()` checks `wsSession.isOpen()`, `setSession/unsetSession/saveSettings` same registry pattern in own static `ConcurrentHashMap<String, WsActionMgr> wsCmds` keyed by WebSession id (add to `TestWebSessions.resetStaticState()`); keep `LongPollingReqMgr.saveAllSettings()` semantics by also iterating `wsCmds` on shutdown.
  - Lookup bridge (critical): worker threads spawn from request threads holding whatever mgr created them (LPR mgr from `/actions/cmd`, or a stale mgr). Server pushes must reach the live socket. Therefore `WsActionMgr.send/sendOptional` are the push path, and any code resolving "current mgr for session" (workers, `ProgressActions` created during WS `onMessage`) must resolve the live `WsActionMgr` from the `wsCmds` registry when a WS is open for that session. Concretely: on WS `onMessage`, build action handlers with the `WsActionMgr`; on LPR `/actions/cmd` while a WS is open for the same session, prefer the registered `WsActionMgr` for the resulting worker's progress pushes (fallback to LPR mgr when no WS open). Document resolution rule in `WsActionMgr` javadoc.
  - Single-socket policy: `wsCmds` holds at most one entry per session id; a second `onOpen` for the same session closes the previous socket (normal closure) and replaces the entry. Two browser tabs sharing one JSESSIONID therefore converge on the newest tab; the replaced tab's `onclose` triggers its own LPR fallback with re-init.
  - `ActionSocket` (`@ServerEndpoint("/ws")` — distinct path to avoid overlap with `ActionServlet`'s `/actions/*` mapping — with custom `Configurator` to capture `HttpSession` from upgrade handshake): `onOpen` validates `WebSession` from `HttpSession.getAttribute("session")` + `hasUser()`, else close with `1008/4401`. On success: create `WsActionMgr`, register (replacing old), run shared init (see below). `onMessage(String)` -> `mgr.process(msg)` (same `Json.parse` path, action handlers built with the `WsActionMgr`). `onClose/onError` -> `unsetSession` (persist settings), remove from `wsCmds` only if the closing socket is the registered one (guard against closing a replaced socket removing the new entry).
  - Extract `doInit(WebSession, ActionsMgr)` logic out of `ActionServlet` into shared helper (e.g. `ActionInit.init(mgr, sess)` calling `ProfileActions/CatVerActions/NPlayersActions.loaded` + `Progress.reload`), then make both `ActionServlet.doInit` and `ActionSocket.onOpen` delegate to it. No behavior change for LPR.
- Registration (both `Server.initialize()` and `FullServer.createContext()` or equivalent): only when `websocketEnabled`: call `JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, container) -> container.addEndpoint(ActionSocket.class))` on the same `ServletContextHandler` BEFORE it starts (so session cookies, `LocalAdminFilter`, FullServer Basic-auth constraint, and `ForwardedRequestCustomizer` apply unchanged). Set container max text message size to cover largest push (reuse `Global.multiCMD` batching unbounded-string risk: cap or chunk; verify against 20s-LPR 100-msg batches). Ensure endpoint sits inside `GzipHandler` the same way (Gzip must pass through Upgrade requests; verify no buffering of `/ws`).
- Message framing: reuse exact JSON strings as LPR (including `Global.multiCMD` batching helper if needed). No protocol change; `Q_.*` payloads unchanged.
- HttpSession keep-alive: WS frames do not touch `HttpSession.lastAccessedTime`. Two-sided: (a) server touches the `HttpSession` (e.g. `httpSession.setAttribute("lastWsAction", Instant.now())`) on every `WsActionMgr.send` push; (b) client sends a `Global.ping` no-op cmd every ~120s while WS is open — new `case "Global.ping"` in `ActionsMgr.processActions` that only updates `session.setLastAction(now)` (already done at method top) and touches the HttpSession, sending no reply. Covers active scans and idle-but-open sockets. Without this, WS-only clients die at the 300s session timeout. Add integration test: WS-only traffic (pushes + idle ping) survives > session idle.
- Shutdown: `AbstractServer.terminate()` / `WebSession.closeAll()` must also close open WS sessions (normal closure, server going away) and persist settings via `wsCmds` iteration. `SessionListener.sessionDestroyed` -> close registered WS (idempotent with `onClose` via the registered-instance guard).
- FullServer/HTTP2 note: WS upgrade requires HTTP/1.1; HTTP/2-only clients will fail the probe and correctly fall back to LPR. Do not attempt RFC 8441 CONNECT in this change.

### 3. Server: capability advertisement + LPR coexistence
- Extend `/session` JSON (`fillAndSendJSO`) with `"websocket": true/false` reflecting `websocketEnabled` (hint only, not authoritative).
- No changes to `/actions/cmd|/lpr|/init` semantics; in WS mode they remain fully functional fallback. `requireAuthenticatedSession` logic reused for upgrade (same 401 semantics -> WS close code).
- WSS: when FullServer serves HTTPS, WS URL uses `wss://`; no extra TLS config (inherits connector/SSLReload).

### 4. GWT WebClient: autodetect + dual transport (mutually exclusive)
- `Client.java` (GWT 2.13, no new third-party dep; small JSNI wrapper around browser native `WebSocket`, following the existing JSNI style in `EnhJSO.java`):
  - After `/session` success, read `websocket` hint. If `false` -> current LPR path unchanged.
  - If `true` -> wait for probe outcome before starting any channel (no parallel LPR): attempt `ws(s)://<host>/ws` (scheme from `Window.Location.getProtocol()`, host from `Window.Location.getHost()` so reverse proxies work; cookies sent automatically). Timeout ~3s: on `onopen` -> enter WS mode and STOP any LPR timer; on `onerror`/`onclose`-before-open/timeout -> fall back to LPR (`lpr(init=true)` loop as today).
  - WS mode: inbound `onmessage` -> existing `processCmd(data)`; outbound `sendMsg` -> `ws.send(msg)` if `readyState==OPEN`, else POST to `/actions/cmd` (covers transient reconnect; server routes pushes to live WS mgr when open per §2 lookup rule). Initial init comes from server `onOpen` (same payloads as `/actions/init`), so no `/actions/init` call in WS mode.
  - Resilience: on WS `onclose` after established session -> full re-init fallback (`GET /actions/init` once to re-seed profile/catver/nplayers + `Progress.reload`, then resume `/actions/lpr` loop) because pushes during WS were socket-only and never queued; 401 still triggers page reload. Guard against reconnect storms (single fallback transition, backoff). While WS open, send `{"cmd":"Global.ping"}` every ~120s (no-op heartbeat keeping the HttpSession alive; server replies nothing).
  - Keep `Q_.send()` -> `Client.sendMsg` signature unchanged; only transport inside branches. Add `A_Session.getWebsocket()` accessor for the `websocket` hint (absent -> false).
- Keep SmartGWT `RPCManager` usage for all non-actions traffic untouched.

### 5. Native image + config
- `graalvmNative` (`jrmserver`, `jrmfullserver`): add reachability metadata for new Jetty/Jakarta WS classes and annotations (run agent via `Test` tasks per `gradle/native-image.gradle` predicate, merge into `src/main/resources/META-INF/native-image/...`). Verify both `nativeCompile` tasks still build; WS-bean init must be lazy (no executor in image heap — follow `ForyPersistence` lazy-init precedent).
- No new system properties beyond `jrm.server.websocket`.

### 6. Tests + validation
- Unit: `WsActionMgrTest` (send/sendOptional socket-only + isOpen-false skip / getSession / setSession-null / process routing, mirroring `LongPollingReqMgrTest`); init-helper equivalence test (WS-open init emits same messages as `ActionServlet.doInit`); single-socket replacement test (second open closes first, registry points at new); stale-close guard test (closing replaced socket does not evict new entry).
- Integration (Jetty test, both `Server` and `FullServer` init paths with flag on/off, extend `ServerCompositeTest` pattern): unauthenticated upgrade rejected; authenticated upgrade connects; client cmd over WS triggers server push over same socket; LPR still works when WS enabled; WS disabled -> upgrade fails and LPR works; session-timeout keep-alive (HttpSession survives > idle with only WS traffic); concurrent pushes from worker threads via live-mgr resolution (`/actions/cmd` while WS open pushes over socket); replace-reconnect (second tab takes over); drop-recovery (WS close -> client re-init -> LPR resumes with `Progress.reload` state).
- Client: SuperDev/manual check — WS-enabled server + browser: Network tab shows `101` on `/actions/ws`, no `/actions/lpr` polling; kill WS (e.g. block via devtools/proxy) -> falls back to LPR with no lost functionality; WS-disabled server -> pure LPR as today. `datasources/upload/download` traffic remains HTTP in both modes.
- Full matrix: `Server` x (WS off/on) x (direct/proxy-blocked) + `FullServer` x (WS off/on, http/https) x (direct/proxy-blocked).
- Command hints (Windows per repo memory: `cmd /c gradlew.bat --no-daemon ...`, never global `-x test`): e.g. `:jrmserver:test --tests "jrm.server.shared.ws.*"`, `:jrmserver:test --tests "jrm.server.shared.handlers.ActionServletTest"`, `:WebClient:gwtCompile`.

## Risks / edge cases
- Gzip/upgrade interaction; Basic-auth + upgrade (FullServer); `LocalAdminFilter` + upgrade (simple Server) — all must pass through existing filter/security chain by mounting WS on the same context.
- HttpSession expiry for WS-only clients (mitigated by touch-on-message; needs test).
- Jetty 12 ee9 Jakarta WS container setup differs from old `NanoHTTPd` WS (removed in 2.5.0) — do not resurrect old code; follow Jetty 12 ee9 docs.
- Idle timeouts / ping: set WS idle timeout >= session timeout and/or application heartbeat; client fallback covers middlebox kills.
- Native-image reflection for `@ServerEndpoint` + Jetty WS internals.

## Out of scope
- Tunneling datasources/uploads/downloads over WS; HTTP/2 extended-CONNECT WS; new auth protocol/token params; client-side build flavors; standalone (`jrmstandalone`) and CLI changes.
