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
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.DummySink;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.sink.InfluxSink;
import de.mechrain.device.sink.VictoriaMetricsSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;

/**
 * Manages server configuration by saving and restoring configuration objects to and from JSON files.
 */
public class ServerConfig {

	private static final Logger LOG = LogManager.getLogger(Logging.CONFIG);

	private final static Path CONFIG_PATH = Paths.get("conf");

	public enum CONFIG_TYPE {

		DEVICE_REGISTRY("device_registry.json", DeviceRegistry.class);

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
		gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(IDataSink.class, new SinkAdapter()).create();
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
			} else {
				throw new IllegalArgumentException("Unsupported sink " + value.getClass().getSimpleName());
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
					return new DummySink();
				} else if (text.equals("influx")) {
					final InfluxSink.Builder influxSinkBuilder = new InfluxSink.Builder();
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
						default:
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							break;
						}
					}
					return influxSinkBuilder.build();
				} else if (text.equals("victoriametrics")) {
					final VictoriaMetricsSink.Builder vmSinkBuilder = new VictoriaMetricsSink.Builder();
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
						default:
							final String name = nextName;
							LOG.error(() -> "Unknown property name " + name);
							break;
						}
					}
					return vmSinkBuilder.build();
				} else {
					throw new IllegalArgumentException("Unsupported sink " + text);
				}
			} finally {
				in.endObject();
			}
		}
	}
}
