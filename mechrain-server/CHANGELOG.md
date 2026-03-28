# Changelog mechrain-server

## [Unreleased]

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