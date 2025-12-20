package de.mechrain.cmdline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;

import de.mechrain.Server;
import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.DeviceConfigRequest;
import de.mechrain.cmdline.beans.DeviceConfigResponse;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.DeviceResetRequest;
import de.mechrain.cmdline.beans.EndConfigureDeviceRequest;
import de.mechrain.cmdline.beans.ICliBean;
import de.mechrain.cmdline.beans.RemoveDeviceRequest;
import de.mechrain.cmdline.beans.RemoveSinkRequest;
import de.mechrain.cmdline.beans.RemoveTaskRequest;
import de.mechrain.cmdline.beans.SetDescriptionRequest;
import de.mechrain.cmdline.beans.SetIdRequest;
import de.mechrain.cmdline.beans.SetLedMode1Request;
import de.mechrain.cmdline.beans.SetNumPixelsRequest;
import de.mechrain.cmdline.beans.SetLedAllRgbRequest;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.sink.VictoriaMetricsSink;
import de.mechrain.device.task.ChanneledMeasurementTask;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.CliAppender;
import de.mechrain.log.LogEventSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.DeviceSettingChangeDataUnit;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.LedMode1DataUnit;
import de.mechrain.protocol.LedMode1DataUnit.LedMode1Builder;
import de.mechrain.protocol.LedAllRgbDataUnit;
import de.mechrain.protocol.LedAllRgbDataUnit.LedAllRgbBuilder;
import de.mechrain.protocol.MRP;
import de.mechrain.util.Util;
import de.mechrain.util.Util.ParsedTime;

public class CliConnector implements LogEventSink {

	private static final Logger LOG = LogManager.getLogger(Logging.CLI);

	private final Socket socket;
	private final DataOutputStream dos;
	private final CliAppender appender;
	private final CliThread cliThread;
	private boolean removed = false;

	public CliConnector(final Socket socket, final CliAppender appender, final Server server) throws IOException {
		this.socket = socket;
		this.appender = appender;

		this.dos = new DataOutputStream(socket.getOutputStream());
		this.cliThread = new CliThread(server, socket.getInputStream(), dos);
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
			MechRainFory.serializeAndSend(de.mechrain.cmdline.beans.LogEvent.fromLog4jEvent(logEvent), dos);
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
		private final DataInputStream dis;
		private boolean run = true;

		private CliThread(final Server server, final InputStream is, final DataOutputStream dos) throws IOException {
			this.server = server;
			this.dis = new DataInputStream(is);
			this.dos = dos;
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
					final ICliBean object = MechRainFory.deserialize(data);
					LOG.trace(() -> "Received " + object.getClass().getSimpleName());
					if (object instanceof DeviceListRequest) {
						final DeviceRegistry registry = server.getRegistry();
						final DeviceListResponse response = new DeviceListResponse();
						response.setDeviceList(registry.getDevices());
						MechRainFory.serializeAndSend(response, dos);
					} else if (object instanceof DeviceConfigRequest cdr) {
						final int deviceId = cdr.getDeviceId();
						final DeviceRegistry registry = server.getRegistry();
						final Optional<Device> device = registry.getDevice(deviceId);
						if (device.isEmpty()) {
							LOG.error(() -> "Device with id " + deviceId + " not found");
						} else {
							try {
								configureDevice(device.get());
							} finally {
								MechRainFory.serializeAndSend(SwitchToNonInteractiveRequest.INSTANCE, dos);
							}
						}
					} else {
						LOG.warn("Unhandled request " + object.getClass().getSimpleName());
					}
				}
			} catch (final IOException e) {
				LOG.warn(() -> "CliConnector encountered error and disconnected: " + e.getClass().getSimpleName() + " " +  e.getMessage(), e);
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

		private void configureDevice(final Device device) throws IOException {
			MechRainFory.serializeAndSend(new DeviceConfigResponse(new DeviceListResponse.DeviceData(device)), dos);
			boolean isConfiguring = true;
			while (isConfiguring) {
				int len = dis.readInt();
				final byte[] data = new byte[len];
				dis.readFully(data);
				final ICliBean object = MechRainFory.deserialize(data);
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

						device.queueRequest(du);
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
				} else if (object instanceof SetNumPixelsRequest setNumPixelsRequest) {
					LOG.debug(() -> "Changing number of pixels to " + setNumPixelsRequest.numPixels);
					try {
						final DeviceSettingChangeDataUnit du = new DeviceSettingChangeBuilder()
								.settingId(MRP.NUM_PIXELS)
								.settingValue(setNumPixelsRequest.numPixels)
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating num pixel change request " + e);
						return;
					}
				} else if (object instanceof SetLedAllRgbRequest setLedRgbRequest) {
					LOG.debug(() -> "Changing RGB to " + setLedRgbRequest.r + " " + setLedRgbRequest.g + " " + setLedRgbRequest.b);
					try {
						final LedAllRgbDataUnit du = new LedAllRgbBuilder()
								.red(setLedRgbRequest.r)
								.green(setLedRgbRequest.g)
								.blue(setLedRgbRequest.b)
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating LED change request " + e);
						return;
					}
				} else if (object instanceof SetLedMode1Request setLedModeRequest) {
					LOG.debug(() -> "Changing LED mode to " + setLedModeRequest.mode);
					try {
						final LedMode1DataUnit du = new LedMode1Builder()
								.mode(setLedModeRequest.mode)
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating device id change request " + e);
						return;
					}
				} else if (object instanceof DeviceResetRequest) {
					LOG.debug(() -> "Resetting device");
					try {
						// TODO: use proper data unit
						final DeviceSettingChangeDataUnit du = new DeviceSettingChangeBuilder()
								.settingId(MRP.RESET)
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating device reset request " + e);
						return;
					}
				} else if (object instanceof RemoveDeviceRequest) {
					LOG.debug(() -> "Removing device " + device);
					final DeviceRegistry registry = server.getRegistry();
					registry.removeDevice(device.getId());
					server.saveConfig();
					isConfiguring = false;
				} else if (object instanceof RemoveSinkRequest removeSinkRequest) {
					final int sinkId = removeSinkRequest.id;
					device.removeSink(sinkId);
					LOG.info(() -> "Removed sink with id " + sinkId);
					server.saveConfig();
				} else if (object instanceof RemoveTaskRequest removeTaskRequest) {
					final int taskId = removeTaskRequest.id;
					device.removeTask(taskId);
					LOG.info(() -> "Removed task with id " + taskId);
					server.saveConfig();
				} else if (object instanceof EndConfigureDeviceRequest) {
					isConfiguring = false;
				} else {
					LOG.error(() -> "Unknown configure request " + object.getClass().getSimpleName());
					break;
				}
			}
		}

		private void addTask(final Device device) throws IOException {
			try {
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
				
				final MeasurementTask task;
				
				switch (measurement) {
					case SOIL_MOISTURE_PERCENT:
					case SOIL_MOISTURE_ABS:
						final String channelStr = ask("Channel (0-7)");
						try {
							final int channel = Integer.parseInt(channelStr);
							if (channel < 0 || channel > 7) {
								LOG.error(() -> "Channel must be between 0 and 7");
								return;
							}
							task = new ChanneledMeasurementTask(time.value, time.unit, measurement, channel);
						} catch (final NumberFormatException e) {
							LOG.error(() -> "Channel must be a number between 0 and 7", e);
							return;
						}
						break;
					default:
						task = new MeasurementTask(time.value, time.unit, measurement);
						break;
				}
				
				/* determine id and assign lowest unused value starting from 0 */
				final int nextId = Util.determineNextFreeId(device.getTasks());				
				task.setId(nextId);
				
				device.addTask(task);
				device.addTimer(task);
				
				LOG.info(() -> "Added new task " + task);
				server.saveConfig();
			} finally {
				MechRainFory.serializeAndSend(SwitchToNonInteractiveRequest.INSTANCE, dos);
			}
		}
		
		private void addSink(final Device device) throws IOException {
			try {
				final IDataSink sink;
				final String type = ask("Sink type (Influx|VM|Dummy)");
				if ("influx".equalsIgnoreCase(type)) {
					final InfluxSink.Builder influxSinkBuilder = new InfluxSink.Builder();
					final String host = ask("Host (default 127.0.0.1)");
					influxSinkBuilder.host(host == null || host.isEmpty() ? "127.0.0.1" : host);
					final String port = ask("Port (default 8086)");
					influxSinkBuilder.port(Integer.parseInt(port == null || port.isEmpty() ? "8086" : port));
					final String user = ask("User");
					if (user == null || user.isEmpty()) {
						LOG.error(() -> "User required");
						return;
					}
					influxSinkBuilder.user(user);

					final String password = ask("Password");
					if (password == null || password.isEmpty()) {
						LOG.error(() -> "Password required");
						return;
					}
					influxSinkBuilder.password(password);

					final String dbName = ask("Database Name");
					if (dbName == null || dbName.isEmpty()) {
						LOG.error(() -> "Database name required");
						return;
					}
					influxSinkBuilder.dbName(dbName);

					final String measurementName = ask("Measurement Name");
					if (measurementName == null || measurementName.isEmpty()) {
						LOG.error(() -> "Measurement name required");
						return;
					}
					influxSinkBuilder.measurementName(measurementName);

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
					influxSinkBuilder.filter(mrps);
					sink = influxSinkBuilder.build();
				} else if ("vm".equalsIgnoreCase(type)) {
					final VictoriaMetricsSink.Builder vmSinkBuilder = new VictoriaMetricsSink.Builder();
					final String host = ask("Host (default 127.0.0.1)");
					vmSinkBuilder.host(host == null || host.isEmpty() ? "127.0.0.1" : host);

					final String port = ask("Port (default 8428)");
					vmSinkBuilder.port(Integer.parseInt(port == null || port.isEmpty() ? "8428" : port));

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
					vmSinkBuilder.filter(mrps);
					
					final String measurementName = ask("Measurement name");
					if (measurementName == null || measurementName.isEmpty()) {
						LOG.error(() -> "Measurement name required");
						return;
					}
					vmSinkBuilder.measurementName(measurementName);
					
					sink = vmSinkBuilder.build();
				} else if ("dummy".equalsIgnoreCase(type)) {
					sink = new DummySink();
				} else {
					LOG.error(() -> "Unkown sink type " + type);
					return;
				}

				/* determine id and assign lowest unused value starting from 0 */
				final int nextId = Util.determineNextFreeId(device.getSinks());				
				sink.setId(nextId);
				
				device.addSink(sink);
				LOG.info(() -> "Added new sink " + sink);
				server.saveConfig();
			} finally {
				MechRainFory.serializeAndSend(SwitchToNonInteractiveRequest.INSTANCE, dos);
			}
		}
		
		private String ask(final String request) throws IOException {
			final ConsoleRequest consoleRequest = new ConsoleRequest();
			consoleRequest.setRequest(request);
			MechRainFory.serializeAndSend(consoleRequest, dos);
			int len = dis.readInt();
			final byte[] data = new byte[len];
			dis.readFully(data);
			final Object response = MechRainFory.deserialize(data);
			if (response instanceof ConsoleResponse consoleResponse) {
				return consoleResponse.getResponse();
			} else {
				LOG.error(() -> "Expected console response but got " + response.getClass().getSimpleName());
				return null;
			}
		}
	}
}
