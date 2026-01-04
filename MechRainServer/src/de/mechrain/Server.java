package de.mechrain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import de.mechrain.cmdline.CliService;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.log.CliAppender;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;
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
	
	private final boolean testMode;
	
	private Server(final boolean testMode) {
		this.config = new ServerConfig();
		this.registry = config.maybeRestore(CONFIG_TYPE.DEVICE_REGISTRY, () -> new DeviceRegistry());
		this.testMode = testMode;
	}

	public DeviceRegistry getRegistry() {
		return registry;
	}
	
	public void saveConfig() {
		config.save(CONFIG_TYPE.DEVICE_REGISTRY, registry);
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
			
			final Thread cliThread = new Thread(new CliService(appender, cliSocket, this));
			cliThread.setName("CLI-Service");
			cliThread.setDaemon(true);
			cliThread.start();
					
			LOG.info("Listening for Connections");
			while (true) {
				try {
					final Socket client = deviceSocket.accept();
					LOG.info("Got connection");
					final InputStream is = client.getInputStream();
					final OutputStream os = client.getOutputStream();

					final byte[] handshakeBytes = new byte[3];
					is.read(handshakeBytes);
					if (handshakeBytes[0] != MRP.DEVICE_ID.byteVal || handshakeBytes[1] != (byte) 0x00 || handshakeBytes[2] != (byte) 0x01) {
						LOG.error("Invalid handshake received: " + Util.BYTES2HEX(handshakeBytes));
						client.close();
						continue;
					} else {
						LOG.debug(() -> "Handshake: " + Util.BYTES2HEX(handshakeBytes));
					}
					
					final int deviceId = is.read() & 0xFF;
					final Device device = getRegistry().getOrAddDevice(deviceId);
					
					LOG.debug(() -> "Connected to device " + device);
					
					/* if device loses connection and shortly after connects again it like still shows as connected */
					if (device.isConnected()) {
						device.disconnect();
					}
					device.connect(client, is, os);
					/* 45s ~ 900 ml bei 5V 	 -> 20ml/s */
					/* 45s ~ 600 ml bei 3.3V -> 13ml/s */
				} catch (IOException e) {
					e.printStackTrace();
					LOG.error(() -> "Error", e);
				};
			}
		}
	}

	public static void main(final String[] args) throws IOException, InterruptedException {
		boolean testMode = false;
		if (args.length > 0 && args[0].equalsIgnoreCase("--test")) {
			System.setProperty("mechrain.testmode", "true");
			testMode = true;
			LOG.info("!!!! Starting server in TEST mode !!!!");
		}
		final Server server = new Server(testMode);
		server.run();
	}
}
