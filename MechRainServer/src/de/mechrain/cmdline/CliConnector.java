package de.mechrain.cmdline;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;

import de.mechrain.Server;
import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.ConfigDeviceRequest;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.CliAppender;
import de.mechrain.log.LogEventSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;
import de.mechrain.util.Util;
import de.mechrain.util.Util.ParsedTime;

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
		cliThread.setName("CLI-Thread");
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
					} else if (object instanceof ConfigDeviceRequest cdr) {
						final int deviceId = cdr.getDeviceId();
						final DeviceRegistry registry = server.getRegistry();
						final Device device = registry.getDevice(deviceId);
						if (device == null) {
							LOG.error(() -> "Device with id " + deviceId + " not found");
						}
						try {
							configureDevice(device);
						} finally {
							/* TODO: end config */
							oos.writeObject(SwitchToNonInteractiveRequest.INSTANCE);
							oos.reset();
						}
					} else {
						LOG.warn("Unhandled request " + object.getClass().getSimpleName());
					}
				}
			} catch (final IOException | ClassNotFoundException e) {
				LOG.warn(() -> "CliConnector encountered error and disconnected: " + e.getMessage(), e);
				run = false;
			} finally {
				try {
					ois.close();
				} catch (final IOException e) {
					LOG.warn(() ->  "CliConnector encountered error #2 " + e.getMessage(), e);
				}
			}
		}
		
		private void configureDevice(final Device device) throws ClassNotFoundException, IOException {
			final Object object = ois.readObject();
			if (object instanceof AddSinkRequest) {
				addSink(device);
			} else if (object instanceof AddTaskRequest) {
				addTask(device);
			} else {
				LOG.error(() -> "Unknown configure request " + object.getClass().getSimpleName());
			}
		}
		
		private void addTask(final Device device) throws ClassNotFoundException, IOException {
			final String mrp = ask("Measurement (MRP values like TEMPERATURE)");
			if (mrp == null || mrp.isEmpty()) {
				LOG.error("Measurement required");
				return;
			}
			final MRP measurement;
			try {
				measurement = MRP.valueOf(mrp);
			} catch (final IllegalArgumentException e) {
				LOG.error(() -> "Unkown MRP type " + mrp, e);
				return;
			}
			
			final String interval = ask("Interval (default 60s)");
			final ParsedTime time = Util.parse(interval == null || interval.isEmpty() ? "60s" : interval);
			
			final MeasurementTask task = new MeasurementTask(time.value, time.unit, measurement);
			device.addTask(task);
			device.addTimer(task);
			LOG.info(() -> "Added new task " + task);
			server.saveConfig();
		}
		
		private void addSink(final Device device) throws ClassNotFoundException, IOException {
			final IDataSink sink;
			final String type = ask("Sink type (Influx|Dummy)");
			if ("influx".equalsIgnoreCase(type)) {
				final InfluxSink influxSink = new InfluxSink();
				final String host = ask("Host (default 127.0.0.1)");
				influxSink.setHost(host == null || host.isEmpty() ? "127.0.0.1" : host);
				final String port = ask("Port (default 8086)");
				influxSink.setPort(port == null || port.isEmpty() ? "8086" : port);
				final String user = ask("User");
				if (user == null || user.isEmpty()) {
					LOG.error(() -> "User required");
					return;
				}
				influxSink.setUser(user);
				
				final String password = ask("Password");
				if (password == null || password.isEmpty()) {
					LOG.error(() -> "Password required");
					return;
				}
				influxSink.setPassword(password);
				
				final String dbName = ask("Database Name");
				if (dbName == null || dbName.isEmpty()) {
					LOG.error(() -> "Database name required");
					return;
				}
				influxSink.setDbName(dbName);
				
				final String measurementName = ask("Measurement Name");
				if (measurementName == null || measurementName.isEmpty()) {
					LOG.error(() -> "Measurement name required");
					return;
				}
				influxSink.setMeasurementName(measurementName);
				
				final String filters = ask("Filters (MRP values like TEMPERATURE)");
				if (filters == null || filters.isEmpty()) {
					LOG.error(() -> "At least one filter required");
					return;
				}
				final String[] parts = filters.split(",");
				final List<MRP> mrps = new ArrayList<>();
				for (final String part : parts) {
					try {
						mrps.add(MRP.valueOf(part));
					} catch (final IllegalArgumentException e) {
						LOG.error(() -> "Unkown MRP type " + part, e);
						return;
					}
				}
				influxSink.setFilter(mrps);
				sink = influxSink;
			} else if ("dummy".equalsIgnoreCase(type)) {
				sink = new DummySink();
			} else {
				LOG.error(() -> "Unkown sink type " + type);
				return;
			}
			device.addSink(sink);
			LOG.info(() -> "Added new sink " + sink);
			server.saveConfig();
		}
		
		private String ask(final String request) throws IOException, ClassNotFoundException {
			final ConsoleRequest consoleRequest = new ConsoleRequest();
			consoleRequest.setRequest(request);
			oos.writeObject(consoleRequest);
			oos.reset();
			final Object response = ois.readObject();
			if (response instanceof ConsoleResponse consoleResponse) {
				return consoleResponse.getResponse();
			} else {
				LOG.error(() -> "Expected console response but got " + response.getClass().getSimpleName());
				return null;
			}
		}
	}
}
