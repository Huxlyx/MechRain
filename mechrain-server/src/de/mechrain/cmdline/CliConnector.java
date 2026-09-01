package de.mechrain.cmdline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;

import de.mechrain.Server;
import de.mechrain.common.IDeviceDescriptor;
import de.mechrain.common.MechRainFory;
import de.mechrain.common.beans.AddSinkRequest;
import de.mechrain.common.beans.AddTaskRequest;
import de.mechrain.common.beans.AddSignalRequest;
import de.mechrain.common.beans.ConsoleRequest;
import de.mechrain.common.beans.ConsoleResponse;
import de.mechrain.common.beans.DeviceConfigRequest;
import de.mechrain.common.beans.DeviceConfigResponse;
import de.mechrain.common.beans.DeviceListRequest;
import de.mechrain.common.beans.DeviceListResponse;
import de.mechrain.common.beans.DeviceResetRequest;
import de.mechrain.common.beans.EndConfigureDeviceRequest;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.RemoveDeviceRequest;
import de.mechrain.common.beans.RemoveSignalRequest;
import de.mechrain.common.beans.RemoveSinkRequest;
import de.mechrain.common.beans.RemoveTaskRequest;
import de.mechrain.common.beans.SetDescriptionRequest;
import de.mechrain.common.beans.SetIdRequest;
import de.mechrain.common.beans.SetLedAllRgbRequest;
import de.mechrain.common.beans.SetLedMode1Request;
import de.mechrain.common.beans.SetNumPixelsRequest;
import de.mechrain.common.beans.SetSinkSignalRequest;
import de.mechrain.common.beans.SetTaskSignalRequest;
import de.mechrain.common.beans.SetTestModeRequest;
import de.mechrain.common.beans.SignalListRequest;
import de.mechrain.common.beans.SignalListResponse;
import de.mechrain.common.beans.SwitchToNonInteractiveRequest;
import de.mechrain.device.DeviceMetrics;
import de.mechrain.device.DeviceMetrics.MetricSnapshot;
import de.mechrain.common.beans.DeviceMetricsData;
import de.mechrain.common.beans.MetricsRequest;
import de.mechrain.common.beans.MetricsResponse;
import de.mechrain.common.beans.ServerInfoResponse;
import de.mechrain.common.ProtocolVersion;
import de.mechrain.common.beans.HandshakeRequest;
import de.mechrain.common.beans.ReplaceDeviceRequest;
import de.mechrain.ServerVersion;
import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.sink.LedIndicatorSink;
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
import de.mechrain.protocol.ResetRequestDataUnit;
import de.mechrain.protocol.ResetRequestDataUnit.ResetRequestBuilder;
import de.mechrain.signal.ISignal;
import de.mechrain.signal.InverterSignal;
import de.mechrain.signal.LogicGateSignal;
import de.mechrain.signal.SignalRegistry;
import de.mechrain.signal.StalenessSignal;
import de.mechrain.signal.ThresholdSignal;
import de.mechrain.signal.TimeWindowSignal;
import de.mechrain.util.Util;
import de.mechrain.util.Util.ParsedTime;

public class CliConnector implements LogEventSink {

	private static final Logger LOG = LogManager.getLogger(Logging.CLI);

	private static final String[] MEASUREMENT_SUGGESTIONS = {
		"TEMPERATURE", "HUMIDITY", "SOIL_MOISTURE_PERCENT", "SOIL_MOISTURE_ABS",
		"LIGHT", "DISTANCE_MM", "DISTANCE_ABS", "CO2_PPM"
	};

	private static final String[] YES_NO_SUGGESTIONS = { "yes", "no" };

	private final Socket socket;
	private final DataOutputStream dos;
	private final CliAppender appender;
	private final CliThread cliThread;
	private final WriteThread writeThread;
	private final DeviceMetrics cliMetrics = new DeviceMetrics();
	private final BlockingQueue<LogEvent> pendingEvents = new LinkedBlockingQueue<>();
	private final AtomicBoolean removed = new AtomicBoolean(false);

	public CliConnector(final Socket socket, final CliAppender appender, final Server server) throws IOException {
		this.socket = socket;
		this.appender = appender;

		this.dos = new DataOutputStream(socket.getOutputStream());
		this.cliThread = new CliThread(this::cleanup, server, socket.getInputStream(), dos, cliMetrics);
		cliThread.setName("CLI-Thread");
		cliThread.start();
		this.writeThread = new WriteThread();
		writeThread.setName("CLI-WriteThread");
		writeThread.setDaemon(true);
		writeThread.start();
	}

	@Override
	public void handleLogEvent(final LogEvent logEvent) {
		if (removed.get()) {
			return;
		}
		pendingEvents.offer(logEvent.toImmutable());
	}

	private void cleanup() {
		if (removed.compareAndSet(false, true)) {
			appender.removeSink(this);
			try {
				socket.close();
			} catch (final IOException e) {
				LOG.warn(() -> "Error closing CLI socket: " + e.getMessage());
			}
			cliThread.end();
			cliThread.interrupt();
			writeThread.interrupt();
		}
	}

	private class WriteThread extends Thread {

		@Override
		public void run() {
			while (true) {
				try {
					final LogEvent event = pendingEvents.poll(1, TimeUnit.SECONDS);
					if (event == null) {
						continue;
					}
					synchronized (dos) {
						MechRainFory.serializeAndSend(de.mechrain.common.beans.LogEvent.fromLog4jEvent(event), dos, cliMetrics::recordSent);
					}
				} catch (final IOException e) {
					CliConnector.this.cleanup();
					return;
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private static class CliThread extends Thread {

		private final Runnable onClose;
		private final Server server;
		private final DataOutputStream dos;
		private final DataInputStream dis;
		private final DeviceMetrics cliMetrics;
		private boolean run = true;

		private CliThread(final Runnable onClose, final Server server, final InputStream is, final DataOutputStream dos, final DeviceMetrics cliMetrics) throws IOException {
			this.onClose = onClose;
			this.server = server;
			this.dis = new DataInputStream(is);
			this.dos = dos;
			this.cliMetrics = cliMetrics;
		}

		private void end() {
			this.run = false;
		}

		private void send(final ICliBean bean) throws IOException {
			synchronized (dos) {
				MechRainFory.serializeAndSend(bean, dos, cliMetrics::recordSent);
			}
		}

		/* after this many consecutive transient network errors on read, treat the connection as dead */
		private static final int MAX_CONSECUTIVE_READ_FAILURES = 3;

		@Override
		public void run() {
			int consecutiveReadFailures = 0;
			try {
				send(new ServerInfoResponse(ServerVersion.VERSION, ProtocolVersion.PROTOCOL_VERSION));
				while (run)
				{
					final ICliBean object;
					try {
						object = MechRainFory.receiveAndDeserialize(dis, cliMetrics::recordReceived);
						consecutiveReadFailures = 0;
					} catch (final RuntimeException e) {
						LOG.warn(() -> "Could not deserialize incoming message, skipping: " + e.getMessage());
						continue;
					} catch (final SocketException | SocketTimeoutException e) {
						/* Transient network errors (e.g. "No route to host" during a brief network blip,
						 * or a read timeout) should not immediately tear down the CLI session - retry a
						 * few times with a short backoff before giving up, mirroring Device's read handling. */
						++consecutiveReadFailures;
						final int attempt = consecutiveReadFailures;
						if (attempt >= MAX_CONSECUTIVE_READ_FAILURES) {
							LOG.warn(() -> "CLI connection appears dead after " + attempt + " consecutive network errors, disconnecting: " + e.getMessage());
							throw e;
						}
						LOG.warn(() -> "Transient network error while reading from CLI (attempt " + attempt + "/" + MAX_CONSECUTIVE_READ_FAILURES + "), retrying: " + e.getMessage());
						try {
							Thread.sleep(500);
						} catch (final InterruptedException ie) {
							Thread.currentThread().interrupt();
							return;
						}
						continue;
					}
					LOG.trace(() -> "Received " + object.getClass().getSimpleName());
					if (object instanceof HandshakeRequest hr) {
						if (hr.getProtocolVersion() != ProtocolVersion.PROTOCOL_VERSION) {
							LOG.warn(() -> "CLI protocol version mismatch: server=" + ProtocolVersion.PROTOCOL_VERSION
									+ ", client=" + hr.getProtocolVersion() + ". Some features may not work correctly.");
						} else {
							LOG.debug(() -> "CLI handshake OK (protocol version " + ProtocolVersion.PROTOCOL_VERSION + ")");
						}
					} else if (object instanceof DeviceListRequest) {
						final DeviceRegistry registry = server.getRegistry();
						final DeviceListResponse response = new DeviceListResponse();
						response.setDeviceList(registry.getDevices().stream().map(d -> (IDeviceDescriptor) d).toList(),
								id -> server.getSignalRegistry().getSignal(id).orElse(null));
						send(response);
					} else if (object instanceof SignalListRequest) {
						final SignalListResponse response = new SignalListResponse();
						response.setSignalList(server.getSignalRegistry().getSignals().stream()
								.map(s -> (de.mechrain.common.ISignalDescriptor) s).toList(),
								this::findSignalUsages);
						send(response);
					} else if (object instanceof AddSignalRequest) {
						addSignal();
					} else if (object instanceof RemoveSignalRequest removeSignalRequest) {
						removeSignal(removeSignalRequest.id);
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
								send(SwitchToNonInteractiveRequest.INSTANCE);
							}
						}
					} else if (object instanceof MetricsRequest) {
						final List<DeviceMetricsData> dataList = new ArrayList<>();
						final DeviceMetricsData totalData = new DeviceMetricsData();
						totalData.setDeviceName("All Devices");
						for (final Device device : server.getRegistry().getDevices()) {
							final DeviceMetricsData d = new DeviceMetricsData();
							d.setDeviceId(device.getId());
							d.setDeviceName(device.getDescription());
							fillMetricsData(d, device.getMetrics());
							accumulateMetrics(totalData, d);
							dataList.add(d);
						}
						final DeviceMetricsData cliData = new DeviceMetricsData();
						cliData.setDeviceName("Server \u2194 CLI");
						fillMetricsData(cliData, cliMetrics);
						final MetricsResponse metricsResponse = new MetricsResponse();
						metricsResponse.setDeviceMetricsList(dataList);
						metricsResponse.setTotalMetrics(totalData);
						metricsResponse.setCliMetrics(cliData);
						send(metricsResponse);
					} else {
						LOG.warn("Unhandled request " + object.getClass().getSimpleName());
					}
				}
			} catch (final IOException e) {
				LOG.warn(() -> "CliConnector encountered error and disconnected: " + e.getClass().getSimpleName() + " " +  e.getMessage(), e);
				run = false;
			} finally {
				onClose.run();
				try {
					dis.close();
					dos.close();
				} catch (final IOException e) {
					LOG.warn(() ->  "CliConnector encountered error #2 " + e.getMessage(), e);
				}
			}
		}

		private void configureDevice(final Device device) throws IOException {
			send(new DeviceConfigResponse(deviceData(device)));
			boolean isConfiguring = true;
			while (isConfiguring) {
				final ICliBean object;
				try {
					object = MechRainFory.receiveAndDeserialize(dis, cliMetrics::recordReceived);
				} catch (final RuntimeException e) {
					LOG.warn(() -> "Could not deserialize incoming message during device config, skipping: " + e.getMessage());
					continue;
				}
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
					registry.updateDeviceId(oldId, setIdRequest.newId, device);
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
				} else if (object instanceof SetTestModeRequest setTestModeRequest) {
					LOG.debug(() -> "Changing test mode to " + setTestModeRequest.enabled);
					try {
						final DeviceSettingChangeDataUnit du = new DeviceSettingChangeBuilder()
								.settingId(MRP.TEST_MODE)
								.settingValue(setTestModeRequest.enabled ? 1 : 0)
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating test mode change request " + e);
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
				} else if (object instanceof SetLedMode1Request) {
					LOG.debug(() -> "Changing LED mode to 1");
					try {
						final LedMode1DataUnit du = new LedMode1Builder()
								.build();
						device.queueRequest(du);
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error validating device id change request " + e);
						return;
					}
				} else if (object instanceof DeviceResetRequest) {
					LOG.debug(() -> "Resetting device");
					try {
						final ResetRequestDataUnit du = new ResetRequestBuilder()
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
					send(new DeviceConfigResponse(deviceData(device)));
				} else if (object instanceof RemoveTaskRequest removeTaskRequest) {
					final int taskId = removeTaskRequest.id;
					device.removeTask(taskId);
					LOG.info(() -> "Removed task with id " + taskId);
					server.saveConfig();
					send(new DeviceConfigResponse(deviceData(device)));
				} else if (object instanceof SetSinkSignalRequest setSinkSignalRequest) {
					device.getSinks().stream()
						.filter(s -> s.getId() == setSinkSignalRequest.sinkId)
						.findFirst()
						.ifPresentOrElse(sink -> {
							sink.setSignalId(setSinkSignalRequest.signalId);
							LOG.info(() -> "Set signal " + setSinkSignalRequest.signalId + " on sink " + sink.getId());
							server.saveConfig();
						}, () -> LOG.error(() -> "Sink " + setSinkSignalRequest.sinkId + " not found on device " + device.getId()));
					send(new DeviceConfigResponse(deviceData(device)));
				} else if (object instanceof SetTaskSignalRequest setTaskSignalRequest) {
					device.getTasks().stream()
						.filter(t -> t.getId() == setTaskSignalRequest.taskId)
						.findFirst()
						.ifPresentOrElse(t -> {
							t.setSignalId(setTaskSignalRequest.signalId);
							LOG.info(() -> "Set signal " + setTaskSignalRequest.signalId + " on task " + t.getId());
							server.saveConfig();
						}, () -> LOG.error(() -> "Task " + setTaskSignalRequest.taskId + " not found on device " + device.getId()));
					send(new DeviceConfigResponse(deviceData(device)));
				} else if (object instanceof ReplaceDeviceRequest replaceRequest) {
					final int targetId = replaceRequest.getTargetDeviceId();
					final DeviceRegistry registry = server.getRegistry();
					final Optional<Device> targetOpt = registry.getDevice(targetId);
					if (targetOpt.isEmpty()) {
						LOG.error(() -> "Replace failed: device " + targetId + " not found");
					} else if (targetOpt.get().isConnected()) {
						LOG.error(() -> "Replace failed: device " + targetId + " is still connected");
					} else {
						final boolean hadTasks = !device.getTasks().isEmpty();
						try {
							registry.transferDevice(targetId, device);
						} catch (final IllegalArgumentException e) {
							LOG.error(() -> "Replace failed: " + e.getMessage());
							continue;
						}
						if (device.isConnected() && !hadTasks && !device.getTasks().isEmpty()) {
							device.resetTimers();
						}
						server.saveConfig();
						send(new DeviceConfigResponse(deviceData(device)));
					}
				} else if (object instanceof EndConfigureDeviceRequest) {
					isConfiguring = false;
				} else {
					LOG.warn(() -> "Unknown configure request " + object.getClass().getSimpleName() + ", ignoring");
				}
			}
		}

		private DeviceListResponse.DeviceData deviceData(final Device device) {
			return new DeviceListResponse.DeviceData(device, id -> server.getSignalRegistry().getSignal(id).orElse(null));
		}

		private void addTask(final Device device) throws IOException {
			try {
				final String mrp = ask("Measurement (MRP values like TEMPERATURE)", MEASUREMENT_SUGGESTIONS).trim();
				if (mrp == null || mrp.isEmpty()) {
					LOG.error("Measurement required");
					return;
				}
				final MRP measurement;
				try {
					measurement = MRP.valueOf(mrp);
				} catch (final IllegalArgumentException e) {
					LOG.error(() -> "Unknown MRP type " + mrp, e);
					return;
				}
				
				final String interval = ask("Interval (default 60s)");
				ParsedTime time;
				do {
					time = Util.parse(interval == null || interval.isEmpty() ? "60s" : interval);
				} while (time == null);
				
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
				final String adaptiveStr = ask("Adaptive polling? (yes/no, default: no)", YES_NO_SUGGESTIONS);
				if ("yes".equalsIgnoreCase(adaptiveStr)) {
					task.setAdaptive(true);

					final String minIntervalStr = ask("Min interval (default 5s)");
					final ParsedTime minTime = Util.parse(minIntervalStr == null || minIntervalStr.isEmpty() ? "5s" : minIntervalStr);
					task.setMinIntervalMs(minTime.unit.toMillis(minTime.value));

					final String thresholdStr = ask("Change threshold (default 1.0)");
					try {
						task.setChangeThreshold(Double.parseDouble(thresholdStr == null || thresholdStr.isEmpty() ? "1.0" : thresholdStr));
					} catch (final NumberFormatException e) {
						LOG.warn(() -> "Invalid threshold, using default 1.0");
					}

					final String speedupStr = ask("Speedup factor <1 (default 0.5)");
					try {
						final double sf = Double.parseDouble(speedupStr == null || speedupStr.isEmpty() ? "0.5" : speedupStr);
						if (sf > 0 && sf < 1) {
							task.setSpeedupFactor(sf);
						} else {
							LOG.warn(() -> "Speedup factor must be between 0 and 1, using default 0.5");
						}
					} catch (final NumberFormatException e) {
						LOG.warn(() -> "Invalid speedup factor, using default 0.5");
					}

					final String slowdownStr = ask("Slowdown factor >1 (default 1.5)");
					try {
						final double sd = Double.parseDouble(slowdownStr == null || slowdownStr.isEmpty() ? "1.5" : slowdownStr);
						if (sd > 1) {
							task.setSlowdownFactor(sd);
						} else {
							LOG.warn(() -> "Slowdown factor must be > 1, using default 1.5");
						}
					} catch (final NumberFormatException e) {
						LOG.warn(() -> "Invalid slowdown factor, using default 1.5");
					}
				}

				final int nextId = Util.determineNextFreeId(device.getTasks());				
				task.setId(nextId);
				
				device.addTask(task);
				device.addTimer(task);
				
				LOG.info(() -> "Added new task " + task);
				server.saveConfig();
			} finally {
				/* Send the refreshed device state before switching the CLI back to interactive mode,
				 * so the client's status box update can never race with the newly resumed prompt redraw. */
				send(new DeviceConfigResponse(deviceData(device)));
				send(SwitchToNonInteractiveRequest.INSTANCE);
			}
		}
		
		private void addSink(final Device device) throws IOException {
			try {
				final IDataSink sink;
				final String type = ask("Sink type (Influx|VM|Dummy|Led)");
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
							LOG.error(() -> "Unknown MRP type " + part, e);
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
							LOG.error(() -> "Unknown MRP type " + part, e);
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
				} else if ("led".equalsIgnoreCase(type)) {
					final LedIndicatorSink.Builder ledSinkBuilder = new LedIndicatorSink.Builder();
					boolean cancelled = false;

					MRP measurement = null;
					while (measurement == null && !cancelled) {
						final String measurementStr = ask("Measurement type (or 'cancel')", MEASUREMENT_SUGGESTIONS);
						if (measurementStr == null || "cancel".equalsIgnoreCase(measurementStr)) {
							cancelled = true;
						} else if (measurementStr.isEmpty()) {
							LOG.error(() -> "Measurement type required, please try again");
						} else {
							try {
								measurement = MRP.valueOf(measurementStr);
							} catch (final IllegalArgumentException e) {
								LOG.error(() -> "Unknown MRP type '" + measurementStr + "', please try again");
							}
						}
					}

					Integer targetDeviceId = null;
					while ( ! cancelled && targetDeviceId == null) {
						final String targetDeviceIdStr = ask("Target device ID (blank = this device, id "
								+ device.getId() + ", or 'cancel')");
						if ("cancel".equalsIgnoreCase(targetDeviceIdStr)) {
							cancelled = true;
						} else if (targetDeviceIdStr == null || targetDeviceIdStr.isEmpty()) {
							targetDeviceId = device.getId();
						} else {
							try {
								targetDeviceId = Integer.parseInt(targetDeviceIdStr.trim());
							} catch (final NumberFormatException e) {
								LOG.error(() -> "Invalid target device ID '" + targetDeviceIdStr + "', please try again");
							}
						}
					}

					int stopCount = 0;
					double lastThreshold = Double.NEGATIVE_INFINITY;
					while ( ! cancelled) {
						final int finalStopCount = stopCount;
						final String stopStr = ask("Color stop " + (stopCount + 1)
								+ " as threshold,r,g,b (blank to finish"
								+ (stopCount < 2 ? ", need at least " + (2 - stopCount) + " more" : "") + ", or 'cancel')");
						if ("cancel".equalsIgnoreCase(stopStr)) {
							cancelled = true;
						} else if (stopStr == null || stopStr.isEmpty()) {
							if (stopCount < 2) {
								LOG.error(() -> "At least two color stops are required (have " + finalStopCount + "), please add another");
							} else {
								break;
							}
						} else {
							final String[] parts = stopStr.split(",");
							if (parts.length != 4) {
								LOG.error(() -> "Expected 4 comma-separated values (threshold,r,g,b), got '" + stopStr + "', please try again");
								continue;
							}
							try {
								final double threshold = Double.parseDouble(parts[0].trim());
								final int r = Integer.parseInt(parts[1].trim());
								final int g = Integer.parseInt(parts[2].trim());
								final int b = Integer.parseInt(parts[3].trim());
								final double finalLastThreshold = lastThreshold;
								if (threshold <= lastThreshold) {
									LOG.error(() -> "Threshold " + threshold + " must be greater than the previous stop's threshold ("
											+ finalLastThreshold + "), please try again");
									continue;
								}
								if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
									LOG.error(() -> "Color channel values must be between 0 and 255, please try again");
									continue;
								}
								ledSinkBuilder.colorStop(threshold, r, g, b);
								lastThreshold = threshold;
								stopCount++;
							} catch (final NumberFormatException e) {
								LOG.error(() -> "Could not parse color stop '" + stopStr + "', please try again");
							}
						}
					}

					if (cancelled) {
						LOG.info(() -> "LED indicator sink setup cancelled");
						return;
					}

					ledSinkBuilder.measurement(measurement);
					ledSinkBuilder.targetDeviceId(targetDeviceId);

					try {
						sink = ledSinkBuilder.build();
					} catch (final IllegalStateException e) {
						LOG.error(() -> "Error building LED indicator sink: " + e.getMessage());
						return;
					}
					((LedIndicatorSink) sink).setRegistry(server.getRegistry());
				} else {
					LOG.error(() -> "Unknown sink type " + type);
					return;
				}

				/* determine id and assign lowest unused value starting from 0 */
				final int nextId = Util.determineNextFreeId(device.getSinks());				
				sink.setId(nextId);
				
				device.addSink(sink);
				LOG.info(() -> "Added new sink " + sink);
				server.saveConfig();
			} finally {
				/* Send the refreshed device state before switching the CLI back to interactive mode,
				 * so the client's status box update can never race with the newly resumed prompt redraw. */
				send(new DeviceConfigResponse(deviceData(device)));
				send(SwitchToNonInteractiveRequest.INSTANCE);
			}
		}

		private void addSignal() throws IOException {
			try {
				final ISignal signal;
				final String type = ask("Signal type (Time|Threshold|Gate|Invert|Stale)");
				if ("time".equalsIgnoreCase(type)) {
					final String startStr = ask("Start time (HH:MM)");
					final String endStr = ask("End time (HH:MM)");
					final Integer startMinute = parseHhMm(startStr);
					final Integer endMinute = parseHhMm(endStr);
					if (startMinute == null || endMinute == null) {
						LOG.error(() -> "Start/end time must be in HH:MM format");
						return;
					}
					final String daysStr = ask("Days of week, comma-separated (blank = every day, e.g. MONDAY,TUESDAY)");
					final java.util.Set<java.time.DayOfWeek> days = new java.util.HashSet<>();
					if (daysStr != null && !daysStr.isEmpty()) {
						for (final String d : daysStr.split(",")) {
							try {
								days.add(java.time.DayOfWeek.valueOf(d.trim().toUpperCase()));
							} catch (final IllegalArgumentException e) {
								LOG.error(() -> "Unknown day of week '" + d.trim() + "'", e);
								return;
							}
						}
					}
					signal = new TimeWindowSignal(startMinute, endMinute, days);
				} else if ("threshold".equalsIgnoreCase(type)) {
					final String targetDeviceIdStr = ask("Target device ID (measurement source)");
					final int targetDeviceId;
					try {
						targetDeviceId = Integer.parseInt(targetDeviceIdStr.trim());
					} catch (final NumberFormatException e) {
						LOG.error(() -> "Invalid target device ID '" + targetDeviceIdStr + "'", e);
						return;
					}
					if (server.getRegistry().getDevice(targetDeviceId).isEmpty()) {
						LOG.error(() -> "Target device " + targetDeviceId + " not found in registry");
						return;
					}
					final String measurementStr = ask("Measurement (MRP values like TEMPERATURE)", MEASUREMENT_SUGGESTIONS);
					final MRP measurement;
					try {
						measurement = MRP.valueOf(measurementStr.trim());
					} catch (final IllegalArgumentException e) {
						LOG.error(() -> "Unknown MRP type " + measurementStr, e);
						return;
					}
					final String comparatorStr = ask("Comparator (>|>=|<|<=)");
					final ThresholdSignal.Comparator comparator = switch (comparatorStr == null ? "" : comparatorStr.trim()) {
						case ">" -> ThresholdSignal.Comparator.GT;
						case ">=" -> ThresholdSignal.Comparator.GTE;
						case "<" -> ThresholdSignal.Comparator.LT;
						case "<=" -> ThresholdSignal.Comparator.LTE;
						default -> null;
					};
					if (comparator == null) {
						LOG.error(() -> "Comparator must be one of >, >=, <, <=");
						return;
					}
					final String thresholdStr = ask("Threshold value");
					final double threshold;
					try {
						threshold = Double.parseDouble(thresholdStr.trim());
					} catch (final NumberFormatException e) {
						LOG.error(() -> "Invalid threshold value '" + thresholdStr + "'", e);
						return;
					}
					final String offThresholdStr = ask("Off-threshold for hysteresis (blank = deactivate immediately)");
					Double offThreshold = null;
					if (offThresholdStr != null && !offThresholdStr.trim().isEmpty()) {
						try {
							offThreshold = Double.parseDouble(offThresholdStr.trim());
						} catch (final NumberFormatException e) {
							LOG.error(() -> "Invalid off-threshold value '" + offThresholdStr + "'", e);
							return;
						}
					}
					final String stableStr = ask("Stability window in minutes before state change (blank = immediate)");
					Integer stableForMinutes = null;
					if (stableStr != null && !stableStr.trim().isEmpty()) {
						try {
							stableForMinutes = Integer.parseInt(stableStr.trim());
							if (stableForMinutes <= 0) {
								LOG.error(() -> "Stability window must be positive");
								return;
							}
						} catch (final NumberFormatException e) {
							LOG.error(() -> "Invalid stability window '" + stableStr + "'", e);
							return;
						}
					}
					final ThresholdSignal thresholdSignal = new ThresholdSignal(targetDeviceId, measurement, comparator, threshold);
					thresholdSignal.setOffThreshold(offThreshold);
					thresholdSignal.setStableForMinutes(stableForMinutes);
					signal = thresholdSignal;
				} else if ("gate".equalsIgnoreCase(type)) {
					final String operatorStr = ask("Operator (AND|OR)");
					final LogicGateSignal.Operator operator;
					try {
						operator = LogicGateSignal.Operator.valueOf(operatorStr.trim().toUpperCase());
					} catch (final IllegalArgumentException | NullPointerException e) {
						LOG.error(() -> "Operator must be AND or OR");
						return;
					}
					final String childIdsStr = ask("Child signal IDs, comma-separated");
					final List<Integer> childIds = new ArrayList<>();
					if (childIdsStr != null) {
						for (final String idStr : childIdsStr.split(",")) {
							try {
								final int childId = Integer.parseInt(idStr.trim());
								if (server.getSignalRegistry().getSignal(childId).isEmpty()) {
									LOG.error(() -> "Signal " + childId + " not found");
									return;
								}
								childIds.add(childId);
							} catch (final NumberFormatException e) {
								LOG.error(() -> "Invalid signal ID '" + idStr.trim() + "'", e);
								return;
							}
						}
					}
					if (childIds.isEmpty()) {
						LOG.error(() -> "At least one child signal ID is required");
						return;
					}
					signal = new LogicGateSignal(operator, childIds);
				} else if ("invert".equalsIgnoreCase(type) || "inverter".equalsIgnoreCase(type)) {
					final String childIdStr = ask("Child signal ID (the signal to invert)");
					try {
						final int childId = Integer.parseInt(childIdStr.trim());
						if (server.getSignalRegistry().getSignal(childId).isEmpty()) {
							LOG.error(() -> "Signal " + childId + " not found");
							return;
						}
						signal = new InverterSignal(childId);
					} catch (final NumberFormatException e) {
						LOG.error(() -> "Invalid signal ID '" + childIdStr + "'", e);
						return;
					}
				} else if ("stale".equalsIgnoreCase(type) || "staleness".equalsIgnoreCase(type)) {
					final String targetDeviceIdStr = ask("Target device ID (data source)");
					final int targetDeviceId;
					try {
						targetDeviceId = Integer.parseInt(targetDeviceIdStr.trim());
					} catch (final NumberFormatException e) {
						LOG.error(() -> "Invalid target device ID '" + targetDeviceIdStr + "'", e);
						return;
					}
					if (server.getRegistry().getDevice(targetDeviceId).isEmpty()) {
						LOG.error(() -> "Target device " + targetDeviceId + " not found in registry");
						return;
					}
					final String measurementStr = ask("Measurement to observe, blank = any data unit incl. heartbeats (MRP values like TEMPERATURE)", MEASUREMENT_SUGGESTIONS);
					MRP measurement = null;
					if (measurementStr != null && !measurementStr.trim().isEmpty()) {
						try {
							measurement = MRP.valueOf(measurementStr.trim());
						} catch (final IllegalArgumentException e) {
							LOG.error(() -> "Unknown MRP type " + measurementStr, e);
							return;
						}
					}
					final String timeoutStr = ask("Timeout in minutes");
					double timeoutMinutes;
					try {
						timeoutMinutes = Double.parseDouble(timeoutStr.trim());
						if (timeoutMinutes <= 0) {
							LOG.error(() -> "Timeout must be positive");
							return;
						}
					} catch (final NumberFormatException e) {
						LOG.error(() -> "Invalid timeout '" + timeoutStr + "'", e);
						return;
					}
					signal = new StalenessSignal(targetDeviceId, measurement, timeoutMinutes);
				} else {
					LOG.error(() -> "Unknown signal type " + type);
					return;
				}

				final SignalRegistry signalRegistry = server.getSignalRegistry();
				try {
					signalRegistry.addSignal(signal);
				} catch (final IllegalArgumentException e) {
					LOG.error(() -> "Rejected new signal: " + e.getMessage());
					return;
				}
				if (signal instanceof ThresholdSignal thresholdSignal) {
					thresholdSignal.setRegistry(server.getRegistry());
					server.getRegistry().getDevice(thresholdSignal.getTargetDeviceId())
						.ifPresent(targetDevice -> targetDevice.addSink(thresholdSignal));
				} else if (signal instanceof StalenessSignal stalenessSignal) {
					stalenessSignal.setRegistry(server.getRegistry());
					server.getRegistry().getDevice(stalenessSignal.getTargetDeviceId())
						.ifPresent(targetDevice -> targetDevice.addSink(stalenessSignal));
				} else if (signal instanceof LogicGateSignal logicGateSignal) {
					logicGateSignal.setRegistry(signalRegistry);
				} else if (signal instanceof InverterSignal inverterSignal) {
					inverterSignal.setRegistry(signalRegistry);
				}
				LOG.info(() -> "Added new signal " + signal);
				server.saveSignalConfig();
			} finally {
				send(SwitchToNonInteractiveRequest.INSTANCE);
			}
		}

		/** Parses an "HH:MM" string into a minute-of-day value, or {@code null} if invalid. */
		private Integer parseHhMm(final String hhmm) {
			if (hhmm == null) {
				return null;
			}
			final String[] parts = hhmm.trim().split(":");
			if (parts.length != 2) {
				return null;
			}
			try {
				final int h = Integer.parseInt(parts[0]);
				final int m = Integer.parseInt(parts[1]);
				if (h < 0 || h > 23 || m < 0 || m > 59) {
					return null;
				}
				return h * 60 + m;
			} catch (final NumberFormatException e) {
				return null;
			}
		}

		/**
		 * Finds every current consumer of the given signal ID, producing human-readable
		 * labels for devices/sinks/tasks gated by it and any logic gate signals that
		 * combine it as a child. Used both to render the signal graph and to guard
		 * against removing a still-referenced signal.
		 *
		 * @param signalId the signal ID to search for
		 * @return human-readable usage labels, empty if the signal is unused
		 */
		private List<String> findSignalUsages(final int signalId) {
			final SignalRegistry signalRegistry = server.getSignalRegistry();
			final List<String> usages = new ArrayList<>();
			for (final Device device : server.getRegistry().getDevices()) {
				final String deviceLabel = device.getDescription() != null && !device.getDescription().isEmpty()
						? "Device " + device.getId() + " (" + device.getDescription() + ")"
						: "Device " + device.getId();
				for (final IDataSink sink : device.getSinks()) {
					if (signalId == (sink.getSignalId() == null ? Integer.MIN_VALUE : sink.getSignalId())) {
						usages.add(deviceLabel + " -> Sink #" + sink.getId() + " (" + sink.getSinkType() + ")");
					}
				}
				for (final MeasurementTask task : device.getTasks()) {
					if (signalId == (task.getSignalId() == null ? Integer.MIN_VALUE : task.getSignalId())) {
						usages.add(deviceLabel + " -> Task #" + task.getId() + " (" + task.getMeasurementName() + ")");
					}
				}
			}
			for (final ISignal other : signalRegistry.getSignals()) {
				if (other.getId() != signalId && other.getChildSignalIds() != null && other.getChildSignalIds().contains(signalId)) {
					usages.add("Signal #" + other.getId() + " (" + other.getSignalType() + ")");
				}
			}
			return usages;
		}

		private void removeSignal(final int signalId) {
			final SignalRegistry signalRegistry = server.getSignalRegistry();
			final List<String> usages = findSignalUsages(signalId);
			if (!usages.isEmpty()) {
				LOG.error(() -> "Cannot remove signal " + signalId + ", still in use by: " + String.join(", ", usages));
				return;
			}
			signalRegistry.getSignal(signalId).ifPresent(signal -> {
				if (signal instanceof ThresholdSignal thresholdSignal) {
					thresholdSignal.resolveTargetDevice().ifPresent(device -> device.removeSink(thresholdSignal));
				} else if (signal instanceof StalenessSignal stalenessSignal) {
					server.getRegistry().getDevice(stalenessSignal.getTargetDeviceId())
						.ifPresent(device -> device.removeSink(stalenessSignal));
				}
			});
			if (signalRegistry.removeSignal(signalId)) {
				LOG.info(() -> "Removed signal " + signalId);
				server.saveSignalConfig();
			} else {
				LOG.error(() -> "Signal " + signalId + " not found");
			}
		}

		private String ask(final String request, final String... suggestions) throws IOException {
			final ConsoleRequest consoleRequest = new ConsoleRequest();
			consoleRequest.setRequest(request);
			if (suggestions != null && suggestions.length > 0) {
				consoleRequest.setSuggestions(suggestions);
			}
			send(consoleRequest);
			final ICliBean response = MechRainFory.receiveAndDeserialize(dis, cliMetrics::recordReceived);
			if (response instanceof ConsoleResponse consoleResponse) {
				return consoleResponse.getResponse().trim();
			} else {
				LOG.error(() -> "Expected console response but got " + response.getClass().getSimpleName());
				return null;
			}
		}

		private static void fillMetricsData(final DeviceMetricsData d, final DeviceMetrics m) {
			final MetricSnapshot hour  = m.snapshot(DeviceMetrics.WINDOW_HOUR);
			final MetricSnapshot day   = m.snapshot(DeviceMetrics.WINDOW_DAY);
			final MetricSnapshot week  = m.snapshot(DeviceMetrics.WINDOW_WEEK);
			final MetricSnapshot month = m.snapshot(DeviceMetrics.WINDOW_MONTH);
			d.setMsgSentHour(hour.msgSent());         d.setMsgReceivedHour(hour.msgReceived());
			d.setBytesSentHour(hour.bytesSent());     d.setBytesReceivedHour(hour.bytesReceived());
			d.setMsgSentDay(day.msgSent());           d.setMsgReceivedDay(day.msgReceived());
			d.setBytesSentDay(day.bytesSent());       d.setBytesReceivedDay(day.bytesReceived());
			d.setMsgSentWeek(week.msgSent());         d.setMsgReceivedWeek(week.msgReceived());
			d.setBytesSentWeek(week.bytesSent());     d.setBytesReceivedWeek(week.bytesReceived());
			d.setMsgSentMonth(month.msgSent());       d.setMsgReceivedMonth(month.msgReceived());
			d.setBytesSentMonth(month.bytesSent());   d.setBytesReceivedMonth(month.bytesReceived());
		}

		private static void accumulateMetrics(final DeviceMetricsData target, final DeviceMetricsData src) {
			target.setMsgSentHour(target.getMsgSentHour()           + src.getMsgSentHour());
			target.setMsgReceivedHour(target.getMsgReceivedHour()   + src.getMsgReceivedHour());
			target.setBytesSentHour(target.getBytesSentHour()       + src.getBytesSentHour());
			target.setBytesReceivedHour(target.getBytesReceivedHour() + src.getBytesReceivedHour());
			target.setMsgSentDay(target.getMsgSentDay()             + src.getMsgSentDay());
			target.setMsgReceivedDay(target.getMsgReceivedDay()     + src.getMsgReceivedDay());
			target.setBytesSentDay(target.getBytesSentDay()         + src.getBytesSentDay());
			target.setBytesReceivedDay(target.getBytesReceivedDay() + src.getBytesReceivedDay());
			target.setMsgSentWeek(target.getMsgSentWeek()           + src.getMsgSentWeek());
			target.setMsgReceivedWeek(target.getMsgReceivedWeek()   + src.getMsgReceivedWeek());
			target.setBytesSentWeek(target.getBytesSentWeek()       + src.getBytesSentWeek());
			target.setBytesReceivedWeek(target.getBytesReceivedWeek() + src.getBytesReceivedWeek());
			target.setMsgSentMonth(target.getMsgSentMonth()         + src.getMsgSentMonth());
			target.setMsgReceivedMonth(target.getMsgReceivedMonth() + src.getMsgReceivedMonth());
			target.setBytesSentMonth(target.getBytesSentMonth()     + src.getBytesSentMonth());
			target.setBytesReceivedMonth(target.getBytesReceivedMonth() + src.getBytesReceivedMonth());
		}
	}
}
