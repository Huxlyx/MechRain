package de.mechrain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import de.mechrain.cmdline.CliService;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.CliAppender;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;
import de.mechrain.util.ServerConfig;
import de.mechrain.util.ServerConfig.CONFIG_TYPE;
import de.mechrain.util.Util;

public class Server {
	private static final Logger LOG = LogManager.getLogger(Logging.SERVER);

	private static final int UDP_PORT = 5000;
	
	private final ServerConfig config;
	private final DeviceRegistry registry;
	
	private Server() {
		this.config = new ServerConfig();
		this.registry = config.maybeRestore(CONFIG_TYPE.DEVICE_REGISTRY, () -> new DeviceRegistry());
	}

	public DeviceRegistry getRegistry() {
		return registry;
	}
	
	private void run() throws IOException {
		try (final ServerSocket deviceSocket = new ServerSocket(0);
				final ServerSocket cliSocket = new ServerSocket(0)) {
			final int devicePort = deviceSocket.getLocalPort();
			final int cliPort = cliSocket.getLocalPort();
			
//			config.save(CONFIG_TYPE.DEVICE_REGISTRY, registry);

			final Thread udpThread = new Thread(new UdpDiscoveryService(UDP_PORT, devicePort, cliPort));
			udpThread.setDaemon(true);
			udpThread.start();
			
			final CliAppender appender = LoggerContext.getContext(false).getConfiguration().getAppender("CliAppender");
			if (appender == null) {
				throw new IllegalArgumentException("No CLI Appender available");
			}
			final Thread cliThread = new Thread(new CliService(appender, cliSocket, this));
			cliThread.setDaemon(true);
			cliThread.start();
					
			LOG.info("Listening for Connections");
			while (true) {
				try {
					final Socket client = deviceSocket.accept();
					LOG.info("Got connection");
					final InputStream is = client.getInputStream();
					final OutputStream os = client.getOutputStream();

					final byte[] handshakeBytes = new byte[4];
					is.read(handshakeBytes);
					LOG.debug(() -> "Handshake: " + Util.BYTES2HEX(handshakeBytes, 4));
					
					final int deviceId = handshakeBytes[3];
					final Device device = getRegistry().getOrAddDevice(deviceId);
					
					LOG.debug(() -> "Connected to device " + device);
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

	public static void main(String[] args) throws IOException, InterruptedException {
		final Server server = new Server();
		server.run();
	}
}
