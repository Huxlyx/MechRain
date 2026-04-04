# Changelog mechrain-server

## [Unreleased]

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