package de.mechrain.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.sink.LedIndicatorSink;
import de.mechrain.device.sink.LedIndicatorSink.ColorStop;
import de.mechrain.device.sink.VictoriaMetricsSink;
import de.mechrain.device.task.ChanneledMeasurementTask;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;
import de.mechrain.signal.ISignal;
import de.mechrain.signal.InverterSignal;
import de.mechrain.signal.LogicGateSignal;
import de.mechrain.signal.SignalRegistry;
import de.mechrain.signal.StalenessSignal;
import de.mechrain.signal.ThresholdSignal;
import de.mechrain.signal.TimeWindowSignal;

/**
 * Manages server configuration by saving and restoring configuration objects to and from JSON files.
 */
public class ServerConfig {

	private static final Logger LOG = LogManager.getLogger(Logging.CONFIG);

	private final static Path CONFIG_PATH = Paths.get("conf");

	public enum CONFIG_TYPE {

		DEVICE_REGISTRY("device_registry.json", DeviceRegistry.class),

		SIGNAL_REGISTRY("signal_registry.json", SignalRegistry.class);

		final Path path;
		final Class<?> configClass;
		CONFIG_TYPE(final String string, final Class<?> configClass) {
			this.path = Paths.get(string);
			this.configClass = configClass;
		}
	}

	private final Gson gson;

	public ServerConfig() {
		if ( ! CONFIG_PATH.toFile().exists()) {
			CONFIG_PATH.toFile().mkdirs();
		}
		final SinkAdapter sinkAdapter = new SinkAdapter();
		gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(IDataSink.class, sinkAdapter)
				.registerTypeAdapter(new TypeToken<List<IDataSink>>() {}.getType(), new SinkListAdapter(sinkAdapter))
				.registerTypeAdapter(MeasurementTask.class, new TaskAdapter())
				.registerTypeAdapter(ISignal.class, new SignalAdapter())
				.create();
	}

	/**
	 * Saves the given configuration object to a JSON file corresponding to the specified configuration type.
	 *
	 * @param configType the type of configuration to save
	 * @param o          the configuration object to save
	 */
	public void save(final CONFIG_TYPE configType, final Object o) {
		final Path targetPath = CONFIG_PATH.resolve(configType.path);
		LOG.info(() -> "Saving config " + configType + " to " + targetPath);
		final String json = gson.toJson(o);
		try (final FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
			fos.write(json.getBytes(StandardCharsets.ISO_8859_1));
		} catch (final IOException e) {
			LOG.error(() -> "Could not save " + o, e);
		}
	}

	/**
	 * Attempts to restore a configuration object of the specified type from a JSON file.
	 * If the file does not exist or cannot be read, a new configuration object is created using the provided supplier.
	 *
	 * @param <T>        the type of the configuration object
	 * @param configType the type of configuration to restore
	 * @param supplier   a supplier that provides a new configuration object if restoration fails
	 * @return the restored or newly created configuration object
	 */
	@SuppressWarnings("unchecked")
	public <T> T maybeRestore(final CONFIG_TYPE configType, final Supplier<T> supplier) {
		final Path targetPath = CONFIG_PATH.resolve(configType.path);

		if (targetPath.toFile().exists()) {
			LOG.debug(() -> "Found existing file " + targetPath + " restoring config");
			try (final FileReader fr = new FileReader(targetPath.toFile(), StandardCharsets.ISO_8859_1)) {
				return (T) gson.fromJson(fr, configType.configClass);
			} catch (final FileNotFoundException e) {
				LOG.error(() -> "Config file could not be found", e);
			} catch (final IOException e) {
				LOG.error(() -> "Error reading config", e);
			}
		} else {
			LOG.info(() -> "Config file " + targetPath + " not found");
		}

		LOG.info(() -> "Creating new config for " + configType);
		final T result = supplier.get();
		save(configType, result);
		return result;
	}

	private static class TaskAdapter extends TypeAdapter<MeasurementTask> {

		@Override
		public void write(final JsonWriter out, final MeasurementTask task) throws IOException {
			out.beginObject();
			out.name("type");
			if (task instanceof ChanneledMeasurementTask channeled) {
				out.value("channeled");
				out.name("channelId");
				out.value(channeled.getChannelId());
			} else {
				out.value("basic");
			}
			out.name("id");           out.value(task.getId());
			out.name("interval");     out.value(task.getInterval());
			out.name("timeUnit");     out.value(task.getTimeUnit().name());
			out.name("measurement");  out.value(task.getMeasurement().name());
			out.name("adaptive");     out.value(task.isAdaptive());
			if (task.isAdaptive()) {
				out.name("minIntervalMs");    out.value(task.getMinIntervalMs());
				out.name("changeThreshold");  out.value(task.getChangeThreshold());
				out.name("speedupFactor");    out.value(task.getSpeedupFactor());
				out.name("slowdownFactor");   out.value(task.getSlowdownFactor());
			}
			if (task.getSignalId() != null) {
				out.name("signalId"); out.value(task.getSignalId());
			}
			out.endObject();
		}

		@Override
		public MeasurementTask read(final JsonReader in) throws IOException {
			in.beginObject();
			String type = null;
			int id = 0, interval = 0, channelId = -1; // -1 = not present in JSON
			TimeUnit timeUnit = null;
			MRP measurement = null;
			boolean adaptive = false;
			long minIntervalMs = 5_000;
			double changeThreshold = 1.0, speedupFactor = 0.5, slowdownFactor = 1.5;
			Integer signalId = null;

			while (in.hasNext()) {
				switch (in.nextName()) {
					case "type"           -> type = in.nextString();
					case "id"             -> id = in.nextInt();
					case "interval"       -> interval = in.nextInt();
					case "timeUnit"       -> timeUnit = TimeUnit.valueOf(in.nextString());
					case "measurement"    -> measurement = MRP.valueOf(in.nextString());
					case "channelId"      -> channelId = in.nextInt();
					case "adaptive"       -> adaptive = in.nextBoolean();
					case "minIntervalMs"  -> minIntervalMs = in.nextLong();
					case "changeThreshold"-> changeThreshold = in.nextDouble();
					case "speedupFactor"  -> speedupFactor = in.nextDouble();
					case "slowdownFactor" -> slowdownFactor = in.nextDouble();
					case "signalId"       -> signalId = in.nextInt();
					default               -> in.skipValue();
				}
			}
			in.endObject();

			// "type" field is new — old JSON won't have it. Fall back to inferring from
			// the presence of "channelId" (Gson did serialize the final field, just couldn't restore it).
			final boolean isChanneled = "channeled".equals(type) || (type == null && channelId >= 0);
			final MeasurementTask task;
			if (isChanneled) {
				task = new ChanneledMeasurementTask(interval, timeUnit, measurement, channelId);
			} else {
				task = new MeasurementTask(interval, timeUnit, measurement);
			}
			task.setId(id);
			task.setAdaptive(adaptive);
			task.setMinIntervalMs(minIntervalMs);
			task.setChangeThreshold(changeThreshold);
			task.setSpeedupFactor(speedupFactor);
			task.setSlowdownFactor(slowdownFactor);
			task.setSignalId(signalId);
			return task;
		}
	}

	/**
	 * Adapter for the {@code Device.sinks} list. {@link ThresholdSignal} instances are attached
	 * to a device as a pseudo-sink at runtime (see {@code Server.wireSignals()}) but must not be
	 * persisted alongside real sinks: they already live in, and are restored from, the
	 * {@code SignalRegistry}. Skips them on write and never encounters them on read, since they
	 * are re-attached by {@code Server.wireSignals()} after the signal registry is restored.
	 */
	private static class SinkListAdapter extends TypeAdapter<List<IDataSink>> {

		private final SinkAdapter sinkAdapter;

		SinkListAdapter(final SinkAdapter sinkAdapter) {
			this.sinkAdapter = sinkAdapter;
		}

		@Override
		public void write(final JsonWriter out, final List<IDataSink> value) throws IOException {
			out.beginArray();
			if (value != null) {
				for (final IDataSink sink : value) {
					if (sink instanceof ISignal) {
						continue;
					}
					sinkAdapter.write(out, sink);
				}
			}
			out.endArray();
		}

		@Override
		public List<IDataSink> read(final JsonReader in) throws IOException {
			final List<IDataSink> result = new java.util.concurrent.CopyOnWriteArrayList<>();
			in.beginArray();
			while (in.hasNext()) {
				result.add(sinkAdapter.read(in));
			}
			in.endArray();
			return result;
		}
	}

	private static class SinkAdapter extends TypeAdapter<IDataSink> {

		@Override
		public void write(final JsonWriter out, final IDataSink value) throws IOException {
			out.beginObject();
			out.name("type");
			if (value instanceof DummySink) {
				out.value("dummy");
			} else if (value instanceof InfluxSink sink) {
				out.value("influx");
				out.name("id");
				out.value(value.getId());
				final List<MRP> filter = sink.getFilter();
				if (filter != null) {
					out.name("filter");
					out.beginArray();
					for (final MRP mrp : filter) {
						out.value(mrp.name());
					}
					out.endArray();
				}
				out.name("host");
				out.value(sink.getHost());
				out.name("port");
				out.value(sink.getPort());
				out.name("user");
				out.value(sink.getUser());
				out.name("password");
				out.value(sink.getPassword());
				out.name("dbName");
				out.value(sink.getDbName());
				out.name("measurementName");
				out.value(sink.getMeasurementName());
			} else if (value instanceof VictoriaMetricsSink sink) {
				out.value("victoriametrics");
				out.name("id");
				out.value(value.getId());
				final List<MRP> filter = sink.getFilter();
				if (filter != null) {
					out.name("filter");
					out.beginArray();
					for (final MRP mrp : filter) {
						out.value(mrp.name());
					}
					out.endArray();
				}
				out.name("host");
				out.value(sink.getHost());
				out.name("port");
				out.value(sink.getPort());
				out.name("measurementName");
				out.value(sink.getMeasurementName());
			} else if (value instanceof LedIndicatorSink sink) {
				out.value("ledIndicator");
				out.name("id");
				out.value(value.getId());
				out.name("measurement");
				out.value(sink.getMeasurement().name());
				out.name("targetDeviceId");
				out.value(sink.getTargetDeviceId());
				out.name("colorStops");
				out.beginArray();
				for (final ColorStop stop : sink.getColorStops()) {
					out.beginObject();
					out.name("threshold"); out.value(stop.threshold());
					out.name("r");         out.value(stop.r());
					out.name("g");         out.value(stop.g());
					out.name("b");         out.value(stop.b());
					out.endObject();
				}
				out.endArray();
			} else {
				throw new IllegalArgumentException("Unsupported sink " + value.getClass().getSimpleName());
			}
			if (value.getSignalId() != null) {
				out.name("signalId");
				out.value(value.getSignalId());
			}
			out.endObject();
		}

		@Override
		public IDataSink read(final JsonReader in) throws IOException {
			try {
				in.beginObject();
				String nextName = in.nextName();
				String text = in.nextString();
				if ( ! nextName.equals("type")) {
					throw new IllegalArgumentException("Expected type but got " + nextName);
				}
				if (text.equals("dummy")) {
					final DummySink dummySink = new DummySink();
					while (in.hasNext()) {
						nextName = in.nextName();
						if ("signalId".equals(nextName)) {
							dummySink.setSignalId(in.nextInt());
						} else {
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							in.skipValue();
						}
					}
					return dummySink;
				} else if (text.equals("influx")) {
					final InfluxSink.Builder influxSinkBuilder = new InfluxSink.Builder();
					Integer influxSinkSignalId = null;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id":
							final int id = in.nextInt();
							influxSinkBuilder.id(id);
							break;
						case "filter":
							final List<MRP> filters = new ArrayList<>();
							in.beginArray();
							while (in.hasNext()) {
								final MRP mrp = MRP.valueOf(in.nextString());
								filters.add(mrp);
							}
							in.endArray();
							influxSinkBuilder.filter(filters);
							break;
						case "host":
							final String host = in.nextString();
							influxSinkBuilder.host(host);
							break;
						case "port":
							final int port = in.nextInt();
							influxSinkBuilder.port(port);
							break;
						case "user":
							final String user = in.nextString();
							influxSinkBuilder.user(user);
							break;
						case "password":
							final String password = in.nextString();
							influxSinkBuilder.password(password);
							break;
						case "dbName":
							final String dbName = in.nextString();
							influxSinkBuilder.dbName(dbName);
							break;
						case "measurementName":
							final String measurementName = in.nextString();
							influxSinkBuilder.measurementName(measurementName);
							break;
						case "signalId":
							influxSinkSignalId = in.nextInt();
							break;
						default:
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							break;
						}
					}
					final InfluxSink influxSink = influxSinkBuilder.build();
					influxSink.setSignalId(influxSinkSignalId);
					return influxSink;
				} else if (text.equals("victoriametrics")) {
					final VictoriaMetricsSink.Builder vmSinkBuilder = new VictoriaMetricsSink.Builder();
					Integer vmSinkSignalId = null;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id":
							final int id = in.nextInt();
							vmSinkBuilder.id(id);
							break;
						case "filter":
							final List<MRP> filters = new ArrayList<>();
							in.beginArray();
							while (in.hasNext()) {
								final MRP mrp = MRP.valueOf(in.nextString());
								filters.add(mrp);
							}
							in.endArray();
							vmSinkBuilder.filter(filters);
							break;
						case "host":
							final String host = in.nextString();
							vmSinkBuilder.host(host);
							break;
						case "port":
							final int port = in.nextInt();
							vmSinkBuilder.port(port);
							break;
						case "measurementName":
							final String measurementName = in.nextString();
							vmSinkBuilder.measurementName(measurementName);
							break;
						case "signalId":
							vmSinkSignalId = in.nextInt();
							break;
						default:
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							break;
						}
					}
					final VictoriaMetricsSink vmSink = vmSinkBuilder.build();
					vmSink.setSignalId(vmSinkSignalId);
					return vmSink;
				} else if (text.equals("ledIndicator")) {
					final LedIndicatorSink.Builder ledSinkBuilder = new LedIndicatorSink.Builder();
					Integer ledSinkSignalId = null;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id":
							final int id = in.nextInt();
							ledSinkBuilder.id(id);
							break;
						case "measurement":
							final MRP measurement = MRP.valueOf(in.nextString());
							ledSinkBuilder.measurement(measurement);
							break;
						case "targetDeviceId":
							final int targetDeviceId = in.nextInt();
							ledSinkBuilder.targetDeviceId(targetDeviceId);
							break;
						case "colorStops":
							in.beginArray();
							while (in.hasNext()) {
								in.beginObject();
								double threshold = 0;
								int r = 0, g = 0, b = 0;
								while (in.hasNext()) {
									switch (in.nextName()) {
									case "threshold" -> threshold = in.nextDouble();
									case "r"         -> r = in.nextInt();
									case "g"         -> g = in.nextInt();
									case "b"         -> b = in.nextInt();
									default          -> in.skipValue();
									}
								}
								in.endObject();
								ledSinkBuilder.colorStop(threshold, r, g, b);
							}
							in.endArray();
							break;
						case "signalId":
							ledSinkSignalId = in.nextInt();
							break;
						default:
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							break;
						}
					}
					final LedIndicatorSink ledSink = ledSinkBuilder.build();
					ledSink.setSignalId(ledSinkSignalId);
					return ledSink;
				} else {
					throw new IllegalArgumentException("Unsupported sink " + text);
				}
			} finally {
				in.endObject();
			}
		}
	}

	private static class SignalAdapter extends TypeAdapter<ISignal> {

		@Override
		public void write(final JsonWriter out, final ISignal value) throws IOException {
			out.beginObject();
			out.name("type");
			if (value instanceof TimeWindowSignal signal) {
				out.value("timeWindow");
				out.name("id"); out.value(signal.getId());
				out.name("startMinuteOfDay"); out.value(signal.getStartMinuteOfDay());
				out.name("endMinuteOfDay");   out.value(signal.getEndMinuteOfDay());
				if (signal.getDays() != null) {
					out.name("days");
					out.beginArray();
					for (final java.time.DayOfWeek day : signal.getDays()) {
						out.value(day.name());
					}
					out.endArray();
				}
			} else if (value instanceof ThresholdSignal signal) {
				out.value("threshold");
				out.name("id"); out.value(signal.getId());
				out.name("targetDeviceId"); out.value(signal.getTargetDeviceId());
				out.name("measurement");    out.value(signal.getMeasurement().name());
				out.name("comparator");     out.value(signal.getComparator().name());
				out.name("threshold");      out.value(signal.getThreshold());
				if (signal.getOffThreshold() != null) {
					out.name("offThreshold"); out.value(signal.getOffThreshold());
				}
				if (signal.getStableForMinutes() != null) {
					out.name("stableForMinutes"); out.value(signal.getStableForMinutes());
				}
			} else if (value instanceof LogicGateSignal signal) {
				out.value("logicGate");
				out.name("id"); out.value(signal.getId());
				out.name("operator"); out.value(signal.getOperator().name());
				out.name("childSignalIds");
				out.beginArray();
				for (final Integer childId : signal.getChildSignalIds()) {
					out.value(childId);
				}
				out.endArray();
			} else if (value instanceof InverterSignal signal) {
				out.value("inverter");
				out.name("id"); out.value(signal.getId());
				out.name("childSignalId"); out.value(signal.getChildSignalId());
			} else if (value instanceof StalenessSignal signal) {
				out.value("staleness");
				out.name("id"); out.value(signal.getId());
				out.name("targetDeviceId"); out.value(signal.getTargetDeviceId());
				if (signal.getMeasurement() != null) {
					out.name("measurement"); out.value(signal.getMeasurement().name());
				}
				out.name("timeoutMinutes"); out.value(signal.getTimeoutMinutes());
			} else {
				throw new IllegalArgumentException("Unsupported signal " + value.getClass().getSimpleName());
			}
			out.endObject();
		}

		@Override
		public ISignal read(final JsonReader in) throws IOException {
			try {
				in.beginObject();
				String nextName = in.nextName();
				String text = in.nextString();
				if ( ! nextName.equals("type")) {
					throw new IllegalArgumentException("Expected type but got " + nextName);
				}
				if (text.equals("timeWindow")) {
					int id = 0, startMinuteOfDay = 0, endMinuteOfDay = 0;
					final java.util.Set<java.time.DayOfWeek> days = new java.util.HashSet<>();
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id"               -> id = in.nextInt();
						case "startMinuteOfDay" -> startMinuteOfDay = in.nextInt();
						case "endMinuteOfDay"   -> endMinuteOfDay = in.nextInt();
						case "days" -> {
							in.beginArray();
							while (in.hasNext()) {
								days.add(java.time.DayOfWeek.valueOf(in.nextString()));
							}
							in.endArray();
						}
						default -> in.skipValue();
						}
					}
					final TimeWindowSignal signal = new TimeWindowSignal(startMinuteOfDay, endMinuteOfDay, days);
					signal.setId(id);
					return signal;
				} else if (text.equals("threshold")) {
					int id = 0, targetDeviceId = 0;
					MRP measurement = null;
					ThresholdSignal.Comparator comparator = null;
					double threshold = 0;
					Double offThreshold = null;
					Integer stableForMinutes = null;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id"             -> id = in.nextInt();
						case "targetDeviceId" -> targetDeviceId = in.nextInt();
						case "measurement"    -> measurement = MRP.valueOf(in.nextString());
						case "comparator"     -> comparator = ThresholdSignal.Comparator.valueOf(in.nextString());
						case "threshold"      -> threshold = in.nextDouble();
						case "offThreshold" -> offThreshold = in.nextDouble();
						case "stableForMinutes" -> stableForMinutes = in.nextInt();
						default -> in.skipValue();
						}
					}
					final ThresholdSignal signal = new ThresholdSignal(targetDeviceId, measurement, comparator, threshold);
					signal.setOffThreshold(offThreshold);
					signal.setStableForMinutes(stableForMinutes);
					signal.setId(id);
					return signal;
				} else if (text.equals("logicGate")) {
					int id = 0;
					LogicGateSignal.Operator operator = null;
					final List<Integer> childIds = new ArrayList<>();
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id"       -> id = in.nextInt();
						case "operator" -> operator = LogicGateSignal.Operator.valueOf(in.nextString());
						case "childSignalIds" -> {
							in.beginArray();
							while (in.hasNext()) {
								childIds.add(in.nextInt());
							}
							in.endArray();
						}
						default -> in.skipValue();
						}
					}
					final LogicGateSignal signal = new LogicGateSignal(operator, childIds);
					signal.setId(id);
					return signal;
				} else if (text.equals("inverter")) {
					int id = 0, childSignalId = 0;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id" -> id = in.nextInt();
						case "childSignalId" -> childSignalId = in.nextInt();
						default -> in.skipValue();
						}
					}
					final InverterSignal signal = new InverterSignal(childSignalId);
					signal.setId(id);
					return signal;
				} else if (text.equals("staleness")) {
					int id = 0, targetDeviceId = 0;
					MRP measurement = null;
					double timeoutMinutes = 0;
					while (in.hasNext()) {
						nextName = in.nextName();
						switch (nextName) {
						case "id" -> id = in.nextInt();
						case "targetDeviceId" -> targetDeviceId = in.nextInt();
						case "measurement" -> measurement = MRP.valueOf(in.nextString());
						case "timeoutMinutes" -> timeoutMinutes = in.nextDouble();
						default -> in.skipValue();
						}
					}
					final StalenessSignal signal = new StalenessSignal(targetDeviceId, measurement, timeoutMinutes);
					signal.setId(id);
					return signal;
				} else {
					throw new IllegalArgumentException("Unsupported signal " + text);
				}
			} finally {
				in.endObject();
			}
		}
	}
}
