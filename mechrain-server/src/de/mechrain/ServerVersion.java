package de.mechrain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerVersion {

	public static final String VERSION;

	static {
		final Properties props = new Properties();
		try (final InputStream is = ServerVersion.class.getClassLoader()
				.getResourceAsStream("mechrain-server.properties")) {
			if (is != null) {
				props.load(is);
			}
		} catch (final IOException e) {
			// fall through to default
		}
		VERSION = props.getProperty("version", "unknown");
	}

	private ServerVersion() {}
}
