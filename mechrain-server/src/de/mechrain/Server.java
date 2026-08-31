package de.mechrain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.fory.logging.LoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import de.mechrain.cmdline.CliService;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.LedIndicatorSink;
import de.mechrain.log.CliAppender;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;
import de.mechrain.signal.ISignal;
import de.mechrain.signal.LogicGateSignal;
import de.mechrain.signal.SignalRegistry;
import de.mechrain.signal.ThresholdSignal;
import de.mechrain.util.ServerConfig;
import de.mechrain.util.ServerConfig.CONFIG_TYPE;
import de.mechrain.util.Util;

/**
 * Main server class for handling device connections and services.
 */
public class Server {
	
	private static final Logger LOG = LogManager.getLogger(Logging.SERVER);

	private static final int UDP_PORT = 5000;
	
	private final ServerConfig config;
	private final DeviceRegistry registry;
	private final SignalRegistry signalRegistry;
	
	private final boolean testMode;

	private volatile boolean running = true;
	private volatile ServerSocket deviceServerSocket;
	
	private Server(final boolean testMode) {
		this.config = new ServerConfig();
		this.registry = config.maybeRestore(CONFIG_TYPE.DEVICE_REGISTRY, () -> new DeviceRegistry());
		this.signalRegistry = config.maybeRestore(CONFIG_TYPE.SIGNAL_REGISTRY, () -> new SignalRegistry());
		wireLedIndicatorSinks();
		wireSignals();
		this.testMode = testMode;
	}

	/**
	 * Wires the live {@link DeviceRegistry} into any restored {@link LedIndicatorSink}
	 * instances, since that reference is transient (not persisted) and is needed to
	 * resolve the sink's target device at measurement-handling time.
	 */
	private void wireLedIndicatorSinks() {
		for (final Device device : registry.getDevices()) {
			for (final var sink : device.getSinks()) {
				if (sink instanceof LedIndicatorSink ledSink) {
					ledSink.setRegistry(registry);
				}
			}
		}
	}

	/**
	 * Wires the live {@link SignalRegistry} (and {@link DeviceRegistry}) into every
	 * {@link Device} and into restored signals that need cross-references
	 * ({@link ThresholdSignal} needs the device registry to resolve its target device;
	 * {@link LogicGateSignal} needs the signal registry to resolve its children), since
	 * those references are transient (not persisted). Also attaches restored
	 * {@link ThresholdSignal}s to their target device's sink list so they receive the
	 * data units they observe.
	 */
	private void wireSignals() {
		for (final Device device : registry.getDevices()) {
			device.setSignalRegistry(signalRegistry);
		}
		for (final ISignal signal : signalRegistry.getSignals()) {
			if (signal instanceof ThresholdSignal thresholdSignal) {
				thresholdSignal.setRegistry(registry);
				registry.getDevice(thresholdSignal.getTargetDeviceId()).ifPresentOrElse(
						device -> {
							if ( ! device.getSinks().contains(thresholdSignal)) {
								device.addSink(thresholdSignal);
							}
						},
						() -> LOG.warn(() -> "ThresholdSignal " + thresholdSignal.getId()
								+ " target device " + thresholdSignal.getTargetDeviceId() + " not found in registry"));
			} else if (signal instanceof LogicGateSignal logicGateSignal) {
				logicGateSignal.setRegistry(signalRegistry);
			}
		}
	}

	public DeviceRegistry getRegistry() {
		return registry;
	}

	public SignalRegistry getSignalRegistry() {
		return signalRegistry;
	}
	
	public void saveConfig() {
		config.save(CONFIG_TYPE.DEVICE_REGISTRY, registry);
	}

	public void saveSignalConfig() {
		config.save(CONFIG_TYPE.SIGNAL_REGISTRY, signalRegistry);
	}
	
	/**
	 * Starts (or restarts) the CLI-Service thread that accepts incoming CLI connections.
	 * If the thread ever terminates due to an uncaught error, it is automatically
	 * restarted so the server keeps accepting new CLI connections without requiring
	 * a full server restart.
	 *
	 * @param appender  the CLI log appender to wire new CLI connectors into
	 * @param cliSocket the server socket CLI clients connect to
	 */
	private void startCliServiceThread(final CliAppender appender, final ServerSocket cliSocket) {
		final Thread cliThread = new Thread(new CliService(appender, cliSocket, this));
		cliThread.setName("CLI-Service");
		cliThread.setDaemon(true);
		cliThread.setUncaughtExceptionHandler((thread, ex) -> {
			LOG.error("CLI-Service thread terminated unexpectedly, restarting it", ex);
			startCliServiceThread(appender, cliSocket);
		});
		cliThread.start();
	}

	private void run() throws IOException {
		try (final ServerSocket deviceSocket = new ServerSocket(0);
				final ServerSocket cliSocket = new ServerSocket(0)) {
			final int devicePort = deviceSocket.getLocalPort();
			final int cliPort = cliSocket.getLocalPort();

			final Thread udpThread = new Thread(new UdpDiscoveryService(UDP_PORT, devicePort, cliPort, testMode));
			udpThread.setName("UDP-Service");
			udpThread.setDaemon(true);
			udpThread.start();
			
			final CliAppender appender = LoggerContext.getContext(false).getConfiguration().getAppender("CliAppender");
			if (appender == null) {
				throw new IllegalStateException("No CLI Appender available");
			}
			
			startCliServiceThread(appender, cliSocket);
					
			deviceServerSocket = deviceSocket;
			LOG.info("Listening for Connections");
			while (running) {
				try {
					final Socket client = deviceSocket.accept();
					LOG.info("Got connection");
					/* Short timeout during handshake — prevents a stalling client from blocking accepts */
					client.setSoTimeout(5_000);
					final InputStream is = client.getInputStream();
					final OutputStream os = client.getOutputStream();

					final byte[] handshakeBytes = is.readNBytes(3);
					if (handshakeBytes.length < 3) {
						LOG.error("Incomplete handshake: only " + handshakeBytes.length + " bytes received");
						client.close();
						continue;
					}
					if (handshakeBytes[0] != MRP.DEVICE_ID.byteVal || handshakeBytes[1] != (byte) 0x00 || handshakeBytes[2] != (byte) 0x01) {
						LOG.error("Invalid handshake received: " + Util.BYTES2HEX(handshakeBytes));
						client.close();
						continue;
					} else {
						LOG.debug(() -> "Handshake: " + Util.BYTES2HEX(handshakeBytes));
					}

					final int deviceIdByte = is.read();
					if (deviceIdByte == -1) {
						LOG.error("EOF reading device ID");
						client.close();
						continue;
					}
					final int deviceId = deviceIdByte & 0xFF;
					final Device device = getRegistry().getOrAddDevice(deviceId);
					device.setSignalRegistry(signalRegistry);
					
					/* if device loses connection and shortly after connects again it like still shows as connected */
					if (device.isConnected()) {
						device.disconnect();
					}
					device.connect(client, is, os);
					
					LOG.debug(() -> "Connected to device " + device);
					/* 45s ~ 900 ml bei 5V 	 -> 20ml/s */
					/* 45s ~ 600 ml bei 3.3V -> 13ml/s */
				} catch (IOException e) {
					if (!running) {
						break;
					}
					LOG.error(() -> "Error accepting connection", e);
				}
			}
		}
	}

	/**
	 * Saves config, disconnects all devices, and closes the server socket.
	 * Called by the JVM shutdown hook on SIGTERM.
	 */
	void shutdown() {
		running = false;
		LOG.info("Shutting down...");
		saveConfig();
		registry.getDevices().forEach(d -> {
			if (d.isConnected()) {
				d.disconnect();
			}
		});
		final ServerSocket ss = deviceServerSocket;
		if (ss != null && !ss.isClosed()) {
			try {
				ss.close();
			} catch (final IOException e) {
				LOG.warn("Error closing server socket during shutdown", e);
			}
		}
	}

	public static void main(final String[] args) throws IOException, InterruptedException {
		LoggerFactory.disableLogging();
		boolean testMode = false;
		if (args.length > 0 && args[0].equalsIgnoreCase("--test")) {
			System.setProperty("mechrain.testmode", "true");
			testMode = true;
			LOG.info("!!!! Starting server in TEST mode !!!!");
		}
		final Server server = new Server(testMode);
		Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "Shutdown-Hook"));
		server.run();
	}
}
