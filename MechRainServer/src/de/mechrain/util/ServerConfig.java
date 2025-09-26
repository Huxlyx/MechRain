package de.mechrain.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.mechrain.device.DeviceRegistry;
import de.mechrain.log.Logging;

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
		gson = new GsonBuilder().setPrettyPrinting().create();
	}
	
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
}
