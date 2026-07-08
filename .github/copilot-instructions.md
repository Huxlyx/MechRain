# Copilot Instructions for MechRain

## Project Overview

MechRain is a multi-module Java project providing device monitoring and control infrastructure:
- **mechrain-common**: Shared interfaces and utilities (IDeviceDescriptor, IIdProvider, beans, etc.) used by all modules
- **mechrain-server**: TCP-based server listening on a port, managing device connections and a registry, routing data to sinks (InfluxDB, VictoriaMetrics, Dummy), implementing UDP discovery service
- **mechrain-cli**: Interactive terminal client connecting to the server via TCP and displaying device/system information
- **mechrain-cli-launcher**: Bootstrap JAR entry point for launching the CLI

## Build, Test, and Lint

### Prerequisites
- **Java 17+** (Maven uses `<release>17</release>`)
- **Maven 3.6+** (for `mvn` command)

### Build Commands

**Full build (all modules):**
```bash
# Build common first (dependency for others)
cd mechrain-common && mvn clean install
cd ../mechrain-server && mvn clean verify
cd ../mechrain-cli && mvn clean verify
cd ../mechrain-cli-launcher && mvn clean verify
```

**Build individual modules:**
```bash
cd <module> && mvn clean verify
```

**Quick build (skip tests):**
```bash
cd <module> && mvn clean package -DskipTests
```

**Assembly JAR (runnable JAR with dependencies):**
```bash
cd <module> && mvn clean package
# Output: <module>/target/<module>-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

### Running Modules

**Server:**
```bash
cd mechrain-server
mvn package
java -jar target/mechrain-server-0.1.1-jar-with-dependencies.jar
```

**CLI:**
```bash
cd mechrain-cli-launcher
mvn package
java -jar target/mechrain-cli-bootstrap-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

### Test Discovery
The build uses `mvn verify` which includes test phases. Currently, no explicit `*Test.java` files exist in the codebase—tests may be added or are planned. The Maven configuration is ready to compile and run them.

## High-Level Architecture

### Module Dependencies
```
mechrain-common (no dependencies on other modules)
    ↑
    ├── mechrain-server
    ├── mechrain-cli
    └── mechrain-cli-launcher (depends on mechrain-cli)
```

### Shared Interfaces (mechrain-common)
- **IDeviceDescriptor**: Describes device metadata and capabilities
- **IIdProvider**: Provides IDs for devices and other entities
- **IDataSink**: Interface for routing data (implemented by InfluxDB, VictoriaMetrics, Dummy adapters)
- **MechRainFory**: Dependency injection container using Apache Fory framework

### Server Architecture (mechrain-server)
- **Main entry point**: `de.mechrain.Server`
- **Discovery**: `UdpDiscoveryService` listens for MECH-RAIN-HELLO / CLI-HELLO (and test variants) on UDP and responds with server IP + TCP port
- **Device protocol**: TCP listener accepts 3-byte handshake (MRP.DEVICE_ID, 0x00, 0x01) followed by device ID registration
- **Device lifecycle**: `Device` class manages:
  - Read/request threads for inbound/outbound data units
  - 60-second heartbeat timer when idle
  - Task scheduling via `MeasurementTask` timers
  - Sink connections (add/remove/connect/disconnect lifecycle)
- **Data routing**: `MRP` protocol constants, data unit types (TextDataUnit, AckDataUnit, HeartbeatDataUnit) routed to registered sinks
- **Configuration**: `ServerConfig` persists device registry to `conf/device_registry.json` using Gson with custom TypeAdapter for sink types
- **CLI service**: `cmdline/CliService` accepts TCP connections from CLI clients; `CliAppender` wires logs to CLI clients via Log4j
- **Logging**: Structured Log4j loggers for server, UDP, device, data, and config areas; annotation processors enable PluginProcessor for Log4j2Plugins.dat generation

### CLI Architecture (mechrain-cli)
- **Main entry point**: `de.mechrain.cli.MechRainCLI`
- **Terminal management**: `MechRainTerminal` handles interactive terminal sessions using JLine 3 + Jansi for colors
- **Output handling**: `ConsoleOutputRunner` manages console output formatting
- **Logging**: Structured logging via `LogMessage` and `LogConfig`
- **Dependencies**: JLine (terminal interaction), Jansi (color output), Apache Commons Lang

### Key Patterns

1. **Dependency Injection**: Apache Fory framework (MechRainFory) used for bean management; see `beans` package in mechrain-common
2. **Data Unit Protocol**: Objects representing messages (TextDataUnit, AckDataUnit, HeartbeatDataUnit) for type-safe serialization
3. **Sink Abstraction**: IDataSink interface with Gson TypeAdapter for polymorphic serialization; allows hot-swappable backends (InfluxDB, VictoriaMetrics, Dummy)
4. **Graceful Shutdown**: Devices clean up threads, timers, and connections on disconnect; robust socket timeout handling (3 consecutive timeouts = connection dead)
5. **Configuration as Code**: Servers and clients use Gson for JSON serialization; configs persist in `conf/` directory

## Key Conventions

### Package Naming
- `de.mechrain.*` for server and common packages
- `de.mechrain.cli.*` for CLI packages
- Organize by feature/layer: `protocol`, `device`, `log`, `cmdline`, `beans`, `util`

### Logging
- Use structured loggers: `private static final Logger log = LoggerFactory.getLogger(...);`
- Loggers in mechrain-server: server, UDP, device, data, config areas
- Log4j 2.25.3 with annotation processors enabled; PluginProcessor generates Log4j2Plugins.dat at compile time
- CLI logs routed via `CliAppender` to connected CLI clients

### Java Version
- Target Java 17 compilation (Maven: `<release>17</release>`)
- Use Records (Java 16+), sealed classes, and other Java 17 features where appropriate

### Configuration
- JSON format (Gson) for all config files
- Location: `conf/device_registry.json` (persisted device registry)
- Custom TypeAdapters for polymorphic types (e.g., IDataSink implementations)

### Testing (Not yet in place)
- Test files should follow `*Test.java` naming (Maven Surefire convention)
- Maven `verify` phase runs tests; use `mvn clean verify` to validate changes
- Current poms include surefire/failsafe configuration structure; add test classes to `src/` directory alongside main code

### GitHub Workflows
- Path-based filtering in `.github/workflows/maven.yml`: builds triggered only when respective module paths change
- Common module built if it changes; CLI/Server rebuilt if common changes
- Uses JDK 21 in CI; ensure local development is compatible

## Dependency Management Notes

- **mechrain-common**: Core dependency for all other modules; install to local Maven repo first (`mvn install`)
- **Server sinks**: InfluxDB (influxdb-java 2.25), VictoriaMetrics (supported via IDataSink), Dummy (test/demo)
- **CLI interaction**: JLine 3.30.0 (terminal), Jansi 2.4.2 (color), Commons Lang 3.19.0 (utilities)
- **Apache Fory 1.3.0**: Lightweight dependency injection framework

## When Making Changes

1. **Always build mechrain-common first** if making changes there, then rebuild dependent modules (server, CLI)
2. **Update CHANGELOG.md** in mechrain-server for significant changes (features, fixes, improvements)
3. **Configuration changes**: Ensure Gson serialization/deserialization stays in sync; test with sample configs
4. **Device protocol changes**: Validate 3-byte handshake and MRP constants are consistent across server and CLI
5. **New sinks**: Implement IDataSink, add Gson TypeAdapter, update ServerConfig for persistence
6. **Logging additions**: Declare structured loggers in appropriate `log` package classes

## Resources

- **Changelog**: `mechrain-server/CHANGELOG.md` — detailed feature history and implementation notes
- **Build configuration**: Each module's `pom.xml` lists dependencies, plugins, and compile settings
- **Device registry**: `mechrain-server/conf/device_registry.json` (generated/persisted at runtime)
