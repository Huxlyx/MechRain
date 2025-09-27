package de.mechrain.cmdline;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;

import de.mechrain.Server;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.log.CliAppender;
import de.mechrain.log.LogEventSink;
import de.mechrain.log.Logging;

public class CliConnector implements LogEventSink {
	
	private static final Logger LOG = LogManager.getLogger(Logging.CLI);

	private final Socket socket;
	private final CliAppender appender;
	private final ObjectOutputStream oos;
	private final CliThread cliThread;
	private boolean removed = false;

	public CliConnector(final Socket socket, final CliAppender appender, final Server server) throws IOException {
		this.socket = socket;
		this.appender = appender;
		this.oos = new ObjectOutputStream(socket.getOutputStream());
		this.cliThread = new CliThread(server, socket.getInputStream(), oos);
		cliThread.start();
	}

	@Override
	public void handleLogEvent(final LogEvent logEvent) {
		if (socket.isClosed()) {
			if ( ! removed) {
				appender.removeSink(this);
				removed = true;
			}
			return;
		}

		try {
			oos.writeObject(logEvent);
			oos.reset();
		} catch (final IOException e) {
			if ( ! removed) {
				appender.removeSink(this);
				removed = true;
				cliThread.end();
				cliThread.interrupt();
			}
		}
	}

	private static class CliThread extends Thread {

		private final Server server;
		private final ObjectInputStream ois;
		private final ObjectOutputStream oos;
		private boolean run = true;

		private CliThread(final Server server, final InputStream is, final ObjectOutputStream oos) throws IOException {
			this.server = server;
			this.ois = new ObjectInputStream(is);
			this.oos = oos;
		}
		
		private void end() {
			this.run = false;
		}

		@Override
		public void run() {
			try {
				while (run)
				{
					final Object object = ois.readObject();
					LOG.trace(() -> "Received " + object.getClass().getSimpleName());
					if (object instanceof DeviceListRequest) {
						final DeviceRegistry registry = server.getRegistry();
						final DeviceListResponse response = new DeviceListResponse();
						response.setDeviceList(registry.getDevices());
						oos.writeObject(response);
						oos.reset();
					} else {
						LOG.warn("Unknown request");
					}
				}
			} catch (final IOException | ClassNotFoundException e) {
				LOG.warn("CliConnector encountered error", e);
				run = false;
			} finally {
				try {
					ois.close();
				} catch (final IOException e) {
					LOG.warn("CliConnector encountered error #2", e);
				}
			}
		}
	}
}
