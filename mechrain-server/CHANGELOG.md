# Changelog mechrain-server

## [Unreleased]

## [1.0.19] - 2026-07-09

### Changed
- **Apache Fory bumped from 0.16.0 to 1.3.0** (`mechrain-common`, `mechrain-cli`): verified serialization/deserialization round-trip of CLI beans still works correctly.

### Fixed
- **Server fat jar failed to start (`SecurityException: Invalid signature file digest for Manifest main attributes`)**: bumping Fory to 1.3.0 pulled in a signed transitive dependency; `maven-shade-plugin` (unlike `maven-assembly-plugin`, used by `mechrain-cli`) does not strip `META-INF` signature files by default, so the merged manifest digest no longer matched and the JVM refused to load the jar. Added a shade `<filter>` excluding `META-INF/*.SF`, `*.DSA`, and `*.RSA` from all merged artifacts.

## [1.0.18] - 2026-07-08

### Fixed
- **CLI connector crashed on transient network errors**: a `SocketException` (e.g. "No route to host" during a brief network blip) or `SocketTimeoutException` while reading from the CLI socket immediately tore down the whole `CliConnector` session. `CliThread.run()` now tolerates up to 3 consecutive transient read failures with a short backoff before giving up, mirroring the existing read-timeout handling in `Device`. (#2)
- **No supervision for the CLI-Service accept thread**: if the `CLI-Service` thread (accepting incoming CLI connections) ever died from an uncaught error, no new CLI connections could be accepted until a full server restart. `Server.startCliServiceThread()` now installs an `UncaughtExceptionHandler` that logs the failure and automatically restarts the thread. (#2)

## [1.0.17] - 2026-06-21

### Fixed
- **UDP discovery reports wrong IP on multi-NIC hosts**: `getLocalNonLoopbackAddress()` iterated network interfaces and returned the first non-loopback IPv4 address. On hosts with Docker or other virtual bridges this was a non-routable address (e.g. `172.18.0.1`) instead of the LAN IP. Replaced with `getLocalAddressFor(remoteAddress)` which briefly connects a datagram socket (no packets sent) to the requester's address, letting the OS routing table pick the correct outgoing interface. The reported IP now always matches the interface the discovery packet arrived on.

## [1.0.16] - 2026-06-21

### Added
- **Protocol versioning and handshake**: `mechrain-common` now exposes a `ProtocolVersion.PROTOCOL_VERSION` integer constant (currently `2`) as the single source of truth for CLI ↔ Server protocol compatibility. The server includes its protocol version in the initial `ServerInfoResponse`. The CLI checks this on connect and prints a yellow warning if the versions differ (soft: connection is not refused). The CLI then replies with a `HandshakeRequest` carrying its own protocol version, which the server logs as a `WARN` if mismatched.
- **Graceful unknown-message handling**: `receiveAndDeserialize` calls on both server (`CliConnector`) and client (`ConsoleOutputRunner`) are now wrapped so that unrecognised or undeserializable messages produce a warning and skip the message rather than terminating the session. Unknown configure-device requests now log a warning and continue instead of breaking the config loop.
- **Replace device**: new `replace <id>` command in device-config mode. Transfers sinks, tasks, and description from a disconnected target device to the device currently being configured. The replacing device keeps its own ID. The replaced device remains in the registry with its sinks and tasks cleared and its description updated to `"Replaced by Device <id>"`, so the old hardware can still reconnect safely. Implemented via `DeviceRegistry.transferDevice()` and a new `ReplaceDeviceRequest` CLI bean.



### Added
- **"Last contact" column in device overview**: the device table now shows a `Last contact` column (format `dd.MM HH:mm:ss`) for offline devices, indicating when they last disconnected. Shows `never` if the device has never connected since the server started. Connected devices leave this column blank. The timestamp is recorded on the server side in `Device.lastContactAt` (epoch millis) and carried through `IDeviceDescriptor`, `DeviceListResponse.DeviceData`, and rendered in `ConsoleOutputRunner`.

## [1.0.14]- 2026-05-04

### Fixed
- **`e.printStackTrace()` calls replaced**: replaced all 11 occurrences across the codebase with proper alternatives:
  - `UdpDiscoveryService`: removed duplicate call (already followed by `LOG.error`); consolidated into a single `LOG.error` with message.
  - `Device` (heartbeat timer): replaced with `LOG.error`.
  - `CliService`: replaced with `e.printStackTrace(System.err)` — Log4j2 is intentionally avoided here to prevent a circular dependency through `CliAppender`.
  - `LogConfig` (CLI): collapsed redundant `FileNotFoundException`/`IOException` catch pairs into a single `IOException` catch with `System.err.println`.
  - `MechRainTerminal` (CLI): replaced with `e.printStackTrace(System.err)`.
  - `LauncherMain`: replaced with `e.printStackTrace(System.err)` to match surrounding `System.err` output.

## [1.0.13]- 2026-05-21

### Fixed
- **`VictoriaMetricsSink.connect()` resource leak**: `HttpURLConnection.disconnect()` was only called on the success path; an `IOException` thrown after `conn.connect()` or `conn.getResponseCode()` would leave the connection open. Wrapped the two calls in a `try-finally` so `disconnect()` is always invoked.
- **`DeviceRegistry.updateDeviceId()` atomicity**: the three-step remove/setId/add sequence inside `CliConnector` was not atomic — a concurrent lookup could find the device absent during the update window. Extracted into a new `DeviceRegistry.updateDeviceId(oldId, newId, device)` method guarded by `synchronized(deviceList)`.
- **Server graceful shutdown**: added `volatile boolean running` flag and a JVM shutdown hook (`SIGTERM`) that saves config, disconnects all devices, and closes the server socket to unblock the `accept()` loop cleanly. Removed duplicate `e.printStackTrace()` from the accept-loop catch block.

### Changed
- **Typos**: "epected" → "expected" in `UdpDiscoveryService`; "Unkown" → "Unknown" in `CliConnector`.

## [1.0.12]- 2026-05-01

### Fixed
- **Handshake partial-read and accept-loop stall**: replaced `is.read(handshakeBytes)` (which may return fewer than 3 bytes) with `is.readNBytes(3)` in the device accept loop. Added a 5-second `SO_TIMEOUT` on the accepted socket before reading the handshake so a stalling client cannot block all future device connections indefinitely. Also added an EOF guard on the subsequent device-ID byte read.
- **Device `connect`/`disconnect` thread-safety**: `connected` and `isDisconnecting` are now `volatile`; the check-and-set at the top of `disconnect()` is wrapped in a `synchronized(lifecycleLock)` block so concurrent calls from timer threads, `ReadThread`, and `RequestThread` are correctly serialised. The `finally` block that resets the flags is likewise synchronized. Both join calls now skip `join()` when the calling thread is the thread being joined, preventing a permanent 5-second self-join stall and a misleading warning log.
- **`queueRequest()` throwing on full queue**: `ArrayBlockingQueue.add()` throws `IllegalStateException` when the queue is at capacity; changed to `offer()` with a `WARN` log so a full queue is handled gracefully without crashing the calling timer thread.
- **`CliConnector` cleanup race**: the `removed` flag was a plain `volatile boolean`, so two threads could both pass the `if (!removed)` guard and execute cleanup (double socket close, double sink removal). Changed to `AtomicBoolean` with `compareAndSet(false, true)`. Moved `cleanup()` out of `WriteThread` onto `CliConnector` itself; `CliThread` now receives an `onClose` callback so both threads independently trigger cleanup via the same guarded method.
- **`InputStream` resource leak in launcher**: `getLatestCliReleaseUrl()` did not close the stream if `readAllBytes()` threw; replaced with try-with-resources. Also switched to explicit `StandardCharsets.UTF_8` instead of the platform default charset.

## [1.0.11]- 2026-04-17

### Fixed
- **`[???] null` log events in CLI**: Log4j 2.6+ uses GC-free logging with reusable `MutableLogEvent` objects even for synchronous loggers. `CliConnector.handleLogEvent()` was queuing the original (mutable) event into `pendingEvents` for async delivery by `WriteThread`. By the time `WriteThread` processed the event, Log4j had already recycled it — clearing `loggerName` to `null` and `level` to `OFF` — producing `[???] null` in the CLI for all non-historical live events. Fixed by calling `logEvent.toImmutable()` before queuing: for `MutableLogEvent` this creates an immutable snapshot; for already-immutable mementos replayed from history it is a no-op. Also documented `LogEventSink.handleLogEvent()` with this lifetime requirement.

## [1.0.10]- 2026-04-16

### Fixed
- **CliAppender not found at startup (proper fix)**: replaced `maven-assembly-plugin` with `maven-shade-plugin` 3.6.0 using `Log4j2PluginCacheFileTransformer` (from `org.apache.logging.log4j:log4j-transform-maven-shade-plugin-extensions:0.2.0`) to correctly merge `Log4j2Plugins.dat` from all JARs. Also added `ServicesResourceTransformer` and `Multi-Release: true`. Removed the deprecated `packages` scanning workaround added in 1.0.9.

## [1.0.9]- 2026-04-16

### Fixed
- **Timer threads blocked by CLI write**: `CliAppender.append()` held the `sinks` lock while writing log events to CLI clients over TCP. When a client's socket entered a half-open state (e.g. laptop shut down), the write would block indefinitely, stalling every timer thread that tried to log — causing `Timer.scheduleAtFixedRate` to accumulate missed periods and fire them all at once on unblock, producing the simultaneous queue-overflow burst.
- **Non-blocking log delivery to CLI**: `CliConnector` now moves all log writes to a dedicated `WriteThread` that drains an unbounded `LinkedBlockingQueue`. `handleLogEvent()` does a non-blocking `offer()` so the Log4j lock is never held during a socket write. `WriteThread` closes the socket on `IOException`, which also unblocks the `CliThread` read loop and fixes a socket resource leak (previously only the streams were closed, not the socket itself).
- **Proactive device disconnect on queue backup**: device timer tasks now call `disconnect()` on a daemon thread when the request queue reaches half-capacity (10/20 items), rather than waiting up to 3.5 minutes for the `ReadThread` SO_TIMEOUT to detect a dead connection.

## [1.0.7] - 2026-04-12

### Added
- **Structured task/sink descriptors**: `ITaskDescriptor` and `ISinkDescriptor` interfaces in mechrain-common; `DeviceData` now carries typed `TaskData`/`SinkData` inner classes instead of opaque maps. All sink and task implementations expose display metadata.
- **CLI config mode diagram**: color-coded task→sink flow diagram shown in config mode.

### Fixed
- **`removeTask` timer leak**: the `ITask` overload of `removeTask` was not cancelling the timer; the cancelled timer was also not removed from the timers list, leaking a stale reference. Pending requests for the removed task are now also drained from the queue.

## [1.0.6] - 2026-04-10

### Fixed
- **CLI receive loop silent death**: `ConsoleOutputRunner` now catches `IOException`, `DeserializationException`, and `RuntimeException` separately so any error is displayed in the terminal instead of silently killing the receive thread.
- **`ConcurrentModificationException` on CLI connect**: `CliAppender` now synchronizes `logEvents` access in both `append()` and `addSink()` to prevent concurrent modification when a new CLI client connects while events are being appended.
- **`CliService` accept loop**: now catches `Exception` (not just `IOException`) so an unexpected error during connection setup does not permanently kill the accept loop.

## [1.0.5] - 2026-04-10

### Fixed
- **Request queue robustness**: increased queue capacity from 10 to 20; task timers now stagger their initial fire by 200 ms each on connect to avoid a burst of simultaneous requests at t=0; `WARN` log with device ID and description when queue size reaches 15; `ERROR` log with device ID and description when a task is dropped due to a full queue.
- **`ITask.queueTask()` returns `boolean`**: callers can now detect a failed enqueue. `ChanneledMeasurementTask` was using `add()` (throws on full queue) — both implementations now use `offer()` consistently.

## [1.0.4] - 2026-04-04

### Fixed
- **`ChanneledMeasurementTask` channel lost on restart**: Gson could serialize the `final int channelId` field but not deserialize it, and `List<MeasurementTask>` caused the concrete subtype to be lost entirely. Added a `TaskAdapter` (mirrors `SinkAdapter` pattern) that explicitly writes a `"type"` discriminator and all fields. Added `getChannelId()` to `ChanneledMeasurementTask`. Backwards compatible: old JSON without a `"type"` field is handled by inferring channeled from the presence of `channelId` (sentinel −1 = absent).

## [1.0.3] - 2026-04-04

### Added
- **Adaptive polling**: `MeasurementTask` now supports a dynamic interval mode (`adaptive`, `minIntervalMs`, `changeThreshold`, `speedupFactor`, `slowdownFactor`). The timer fires at `minIntervalMs`; `checkAdaptiveGate()` gates actual polls by comparing elapsed time against `currentIntervalMs`. `onValueReceived()` speeds up (toward `minIntervalMs`) when the delta exceeds the threshold, or slows down (toward the base interval) otherwise. `Device.notifyMeasurement()` dispatches value updates from `ReadThread` after sink routing.
- **Adaptive polling logging**: logs `INFO` when the threshold is exceeded and the interval speeds up (includes delta, threshold, old/new interval); logs `DEBUG` on slowdown.
- **Adaptive polling configuration via CLI**: `CliConnector.addTask()` prompts for adaptive parameters after the base interval, with `YES_NO_SUGGESTIONS` and `MEASUREMENT_SUGGESTIONS` tab-completion for MRP types.
- **Dynamic tab-completion for interactive prompts**: `ConsoleRequest` carries an optional `String[] suggestions` field. The server populates it for known-value prompts (MRP measurement type, yes/no); the CLI installs a temporary `StringsCompleter` for that single `readLine()` call.
- **Metrics: total and CLI sections**: `MetricsResponse` now carries `totalMetrics` (sum of all device metrics) and `cliMetrics` (server↔CLI traffic). The CLI renders these as "— All Devices —" (bold white) and "— Server ↔ CLI —" (bold magenta) sections after per-device tables.
- **`MechRainFory.serializeAndSend` with byte callback**: new overload accepts an optional `LongConsumer bytesSentCallback` so callers can track sent bytes without breaking encapsulation.

### Fixed
- **First-connection race condition**: `CliConnector` started `CliThread` (which sends `ServerInfoResponse`) before `CliService` finished calling `addSink()` (which replays stored log events). Both wrote to the same `DataOutputStream` concurrently, corrupting the length-prefixed framing and causing `ConsoleOutputRunner` to hang on `readFully()`. All writes in `CliThread` now go through a `synchronized (dos)` block in `send()`.
- **Fory codegen log suppression**: `LoggerFactory.disableLogging()` (Fory's own logger) is now called as the first line of `Server.main()`, suppressing `INFO CompileUnit:55 - Generate code for …` messages on first serialization.

## [1.0.0] - 2026-03-28

### Added
- Server version greeting: on CLI connect, `CliConnector` immediately sends a `ServerInfoResponse` bean containing the server version. The CLI displays it as `"Connected to MechRain Server v1.0.0"`.
- Server version embedded at build time via Maven resource filtering (`mechrain-server.properties`) and loaded at runtime by `ServerVersion`.

### Changed
- All modules bumped to 1.0.0 — first stable release.

## [0.2.0] - 2026-03-28

### Added
- Per-device message and byte metrics (`DeviceMetrics`) using pre-aggregated 5-minute time buckets stored in a `TreeMap`. Memory is bounded at ~966 KB per device regardless of message volume (max 8,640 buckets over a 30-day retention window).
- Four metric windows exposed via CLI: last hour, day, week, and month.
- New CLI↔server protocol beans: `MetricsRequest`, `MetricsResponse`, `DeviceMetricsData` (registered in both Fory instances).
- `MetricsRequest` handler in `CliConnector`: iterates all registered devices, snapshots all four windows, and returns a `MetricsResponse`.
- `show metrics` command in CLI: renders a per-device table (Msgs Sent / Msgs Recv / Bytes Sent / Bytes Recv) with human-readable byte formatting (B / KB / MB / GB).
- `--update` flag in `mechrain-cli-launcher`: backs up the current CLI JAR with a timestamp and downloads the latest release from GitHub.
- `--install` flag in `mechrain-cli-launcher`: writes `mechrain.bat` and adds the install directory to the user PATH using PowerShell `[Environment]::SetEnvironmentVariable` (no `setx` 1024-character truncation).
- Download progress bar for CLI JAR downloads (block characters, percentage, bytes transferred).

### Fixed
- Thread shutdown in `Device`: removed improper `Thread.currentThread().interrupt()` propagation inside `join()` catch blocks during intentional disconnect, which caused cascading `InterruptedException` across subsequent joins. Added a 5-second join timeout.
- Messages sent were not being tracked in `DeviceMetrics`.

## [0.1.1] - 2026-02-07
- Version: 0.1.1-SNAPSHOT (in development)
- Date: 2026-02-07

### Added
- UDP discovery service (MECH-RAIN-HELLO / CLI-HELLO; test variants MECH-RAIN-TEST / CLI-TEST) that responds with the server IP and TCP port for devices and CLI connections. Implementation: `UdpDiscoveryService` which uses the first non-loopback IPv4 address.
- TCP device listener and protocol handshake implemented in `Server`:
  - Accepts device TCP connections and expects a 3-byte handshake starting with MRP.DEVICE_ID, 0x00, 0x01.
  - Reads device id and registers or reuses devices via `DeviceRegistry`.
- Device lifecycle and communication stack (`Device`):
  - Socket keepalive and SO_TIMEOUT applied to detect connection loss.
  - Dedicated read and request threads for inbound/outbound data units.
  - Heartbeat timer that queues a heartbeat data unit every 60s when no tasks are present.
  - Task scheduling via `MeasurementTask` timers (per-device timers) with add/remove/reset semantics.
  - Sinks support (add/remove) and per-sink connect/disconnect lifecycle hooks.
- Persistent device registry and configuration via `ServerConfig`:
  - Saves/restores config under `conf/device_registry.json`.
  - Gson-based custom TypeAdapter for `IDataSink` supporting dummy, InfluxDB and VictoriaMetrics sink types and their properties/filters.
- Protocol support and data unit handling:
  - MRP enum with protocol constants (device id, measurement types, status, heartbeat, build id, etc.).
  - Parsing and validation of data units; handling of TextDataUnit (status/error/build id), AckDataUnit, HeartbeatDataUnit and routing of generic data units to sinks.
- CLI service over TCP that accepts CLI connections and wires logs to CLI clients via a `CliAppender` (see `cmdline/CliService` and logging integration).
- Build configuration (from `pom.xml`): Java 17 compilation target, Log4j annotation processors enabled, and Maven Assembly configured to create a jar-with-dependencies with `de.mechrain.Server` as main class.

### Changed
- Logging integration: structured loggers for server, UDP, device, data and config areas (see `de.mechrain.log` package).

### Fixed / Improved
- Robust handling of intermittent socket timeouts: read loop counts consecutive SocketTimeoutExceptions and treats the connection as dead after a configurable number (currently 3) of consecutive timeouts.
- Proper cleanup and shutdown of per-device threads, timers and heartbeat tasks on disconnect.
- JSON sink serialization/deserialization supports filters and builder-style reconstruction, improving portability of config files.

## [0.1.0] - Initial baseline
- This entry summarizes the initial code baseline found in the repository (no release tag was detected while creating this changelog; date was not available in code). The list below reflects the initial implemented feature set:

### Added
- Project skeleton and main entrypoint `de.mechrain.Server`.
- Core protocol definitions (`de.mechrain.protocol.MRP`) and data unit parsing/validation utilities.
- Device abstraction with queuing, tasks and sinks (`de.mechrain.device.*`).
- Device registry for tracking known devices and basic add/remove operations.
- Sink implementations and adapters for time-series backends (InfluxDB and VictoriaMetrics) plus a Dummy sink for testing.
- CLI access support and a logging appender for CLI clients.
- Basic configuration persistence under `conf/` (JSON files).

### Notes / Assumptions
- The repository did not contain explicit release tags or a CHANGELOG prior to this work; the above entries were inferred from source code comments, file contents and `pom.xml` (version `0.1.1-SNAPSHOT`). If you keep a git history with annotated tags or additional release notes elsewhere, I can incorporate those exact commit messages and dates into this changelog.
- File locations referenced in the changes: `src/` (Java sources), `conf/device_registry.json` (persisted registry), `pom.xml` (build/version metadata).
- TCP device listener and protocol handshake implemented in `Server`:
  - Accepts device TCP connections and expects a 3-byte handshake starting with MRP.DEVICE_ID, 0x00, 0x01.
  - Reads device id and registers or reuses devices via `DeviceRegistry`.
- Device lifecycle and communication stack (`Device`):
  - Socket keepalive and SO_TIMEOUT applied to detect connection loss.
  - Dedicated read and request threads for inbound/outbound data units.
  - Heartbeat timer that queues a heartbeat data unit every 60s when no tasks are present.
  - Task scheduling via `MeasurementTask` timers (per-device timers) with add/remove/reset semantics.
  - Sinks support (add/remove) and per-sink connect/disconnect lifecycle hooks.
- Persistent device registry and configuration via `ServerConfig`:
  - Saves/restores config under `conf/device_registry.json`.
  - Gson-based custom TypeAdapter for `IDataSink` supporting dummy, InfluxDB and VictoriaMetrics sink types and their properties/filters.
- Protocol support and data unit handling:
  - MRP enum with protocol constants (device id, measurement types, status, heartbeat, build id, etc.).
  - Parsing and validation of data units; handling of TextDataUnit (status/error/build id), AckDataUnit, HeartbeatDataUnit and routing of generic data units to sinks.
- CLI service over TCP that accepts CLI connections and wires logs to CLI clients via a `CliAppender` (see `cmdline/CliService` and logging integration).
- Build configuration (from `pom.xml`): Java 17 compilation target, Log4j annotation processors enabled, and Maven Assembly configured to create a jar-with-dependencies with `de.mechrain.Server` as main class.

### Changed
- Logging integration: structured loggers for server, UDP, device, data and config areas (see `de.mechrain.log` package).

### Fixed / Improved
- Robust handling of intermittent socket timeouts: read loop counts consecutive SocketTimeoutExceptions and treats the connection as dead after a configurable number (currently 3) of consecutive timeouts.
- Proper cleanup and shutdown of per-device threads, timers and heartbeat tasks on disconnect.
- JSON sink serialization/deserialization supports filters and builder-style reconstruction, improving portability of config files.

## [0.1.0] - Initial baseline
- This entry summarizes the initial code baseline found in the repository (no release tag was detected while creating this changelog; date was not available in code). The list below reflects the initial implemented feature set:

### Added
- Project skeleton and main entrypoint `de.mechrain.Server`.
- Core protocol definitions (`de.mechrain.protocol.MRP`) and data unit parsing/validation utilities.
- Device abstraction with queuing, tasks and sinks (`de.mechrain.device.*`).
- Device registry for tracking known devices and basic add/remove operations.
- Sink implementations and adapters for time-series backends (InfluxDB and VictoriaMetrics) plus a Dummy sink for testing.
- CLI access support and a logging appender for CLI clients.
- Basic configuration persistence under `conf/` (JSON files).

### Notes / Assumptions
- The repository did not contain explicit release tags or a CHANGELOG prior to this work; the above entries were inferred from source code comments, file contents and `pom.xml` (version `0.1.1-SNAPSHOT`). If you keep a git history with annotated tags or additional release notes elsewhere, I can incorporate those exact commit messages and dates into this changelog.
- File locations referenced in the changes: `src/` (Java sources), `conf/device_registry.json` (persisted registry), `pom.xml` (build/version metadata).