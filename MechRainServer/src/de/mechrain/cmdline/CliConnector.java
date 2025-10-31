package de.mechrain.cmdline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
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
import de.mechrain.cmdline.beans.DeviceResetRequest;
import de.mechrain.cmdline.beans.SetDescriptionRequest;
import de.mechrain.cmdline.beans.SetIdRequest;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.cmdline.beans.DeviceListResponse.DeviceData;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.CliAppender;
import de.mechrain.log.LogEventSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.DeviceSettingChangeDataUnit;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.MRP;
import de.mechrain.util.Util;
import de.mechrain.util.Util.ParsedTime;

public class CliConnector implements LogEventSink {
	
	private static final Logger LOG = LogManager.getLogger(Logging.CLI);

	private final Socket socket;
	private final DataOutputStream dos;
	private final CliAppender appender;
	private final CliThread cliThread;
	private final ThreadSafeFory fory;
	private boolean removed = false;

	public CliConnector(final Socket socket, final CliAppender appender, final Server server) throws IOException {
		this.socket = socket;
		this.appender = appender;
		this.fory = Fory.builder()
				.withLanguage(Language.JAVA)
				.requireClassRegistration(true)
				.buildThreadSafeFory();
		fory.register(AddSinkRequest.class);
		fory.register(AddTaskRequest.class);
		fory.register(SetIdRequest.class);
		fory.register(SetDescriptionRequest.class);
		fory.register(DeviceResetRequest.class);
		fory.register(ConsoleRequest.class);
		fory.register(ConsoleResponse.class);
		fory.register(DeviceListRequest.class);
		fory.register(DeviceData.class);
		fory.register(DeviceListResponse.class);
		fory.register(ConfigDeviceRequest.class);
		fory.register(SwitchToNonInteractiveRequest.class);
		fory.register(de.mechrain.log.LogEvent.class);
		
		this.dos = new DataOutputStream(socket.getOutputStream());
		this.cliThread = new CliThread(server, socket.getInputStream(), dos, fory);
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
			final byte[] data = fory.serialize(de.mechrain.log.LogEvent.fromLog4jEvent(logEvent));
			final int len = data.length;
			dos.writeInt(len);
			dos.write(data);
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
		private final DataOutputStream dos;
		private final ThreadSafeFory fory;
		private final DataInputStream dis;
		private boolean run = true;

		private CliThread(final Server server, final InputStream is, final DataOutputStream dos, final ThreadSafeFory fory) throws IOException {
			this.server = server;
			this.dis = new DataInputStream(is);
			this.dos = dos;
			this.fory = fory;
		}
		
		private void end() {
			this.run = false;
		}

		@Override
		public void run() {
			try {
				while (run)
				{
					int len = dis.readInt();
					final byte[] data = new byte[len];
					dis.readFully(data);
					final Object object = fory.deserialize(data);
					LOG.trace(() -> "Received " + object.getClass().getSimpleName());
					if (object instanceof DeviceListRequest) {
						final DeviceRegistry registry = server.getRegistry();
						final DeviceListResponse response = new DeviceListResponse();
						response.setDeviceList(registry.getDevices());
						final byte[] outData = fory.serialize(response);
						dos.writeInt(outData.length);
						dos.write(outData);
					} else if (object instanceof ConfigDeviceRequest cdr) {
						final int deviceId = cdr.getDeviceId();
						final DeviceRegistry registry = server.getRegistry();
						final Optional<Device> device = registry.getDevice(deviceId);
						if (device.isEmpty()) {
							LOG.error(() -> "Device with id " + deviceId + " not found");
						} else {
							try {
								configureDevice(device.get());
							} finally {
								/* TODO: end config */
								final byte[] outData = fory.serialize(SwitchToNonInteractiveRequest.INSTANCE);
								dos.writeInt(outData.length);
								dos.write(outData);
							}
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
					dis.close();
					dos.close();
				} catch (final IOException e) {
					LOG.warn(() ->  "CliConnector encountered error #2 " + e.getMessage(), e);
				}
			}
		}
		
		private void configureDevice(final Device device) throws ClassNotFoundException, IOException {
			int len = dis.readInt();
			final byte[] data = new byte[len];
			dis.readFully(data);
			final Object object = fory.deserialize(data);
			if (object instanceof AddSinkRequest) {
				addSink(device);
			} else if (object instanceof AddTaskRequest) {
				addTask(device);
			} else if (object instanceof SetIdRequest setIdRequest) {
				final int oldId = device.getId();
				LOG.debug(() -> "Changing id of device from " + oldId + " to " + setIdRequest.newId);
				try {
					final DeviceSettingChangeDataUnit du = new DeviceSettingChangeBuilder()
							.settingId(MRP.DEVICE_ID)
							.settingValue(setIdRequest.newId)
							.build();
					
					device.addRequest(du);
				} catch (final DataUnitValidationException e) {
					LOG.error(() -> "Error validating device id change request " + e);
					return;
				}
				final DeviceRegistry registry = server.getRegistry();
				//TODO: add single method for this
				registry.removeDevice(oldId);
				device.setId(setIdRequest.newId);
				registry.addDevice(device);
				server.saveConfig();
			} else if (object instanceof SetDescriptionRequest setDescriptionRequest) {
				device.setDescription(setDescriptionRequest.description);
				server.saveConfig();
			} else if (object instanceof DeviceResetRequest) {
				LOG.debug(() -> "Resetting device");
				try {
					// TODO: use proper data unit
					final DeviceSettingChangeDataUnit du = new DeviceSettingChangeBuilder()
							.settingId(MRP.RESET)
							.build();
					device.addRequest(du);
				} catch (final DataUnitValidationException e) {
					LOG.error(() -> "Error validating device id change request " + e);
					return;
				}
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
			final byte[] outData = fory.serialize(consoleRequest);
			dos.writeInt(outData.length);
			dos.write(outData);
			int len = dis.readInt();
			final byte[] data = new byte[len];
			dis.readFully(data);
			final Object response = fory.deserialize(data);
			if (response instanceof ConsoleResponse consoleResponse) {
				return consoleResponse.getResponse();
			} else {
				LOG.error(() -> "Expected console response but got " + response.getClass().getSimpleName());
				return null;
			}
		}
	}
}
