package de.mechrain.log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

import org.apache.logging.log4j.core.LogEvent;

public class CliConnector implements LogEventSink {

	private final Socket socket;
	private final CliAppender appender;
	private final ObjectOutputStream oos;
	private boolean removed = false;

	public CliConnector(final Socket socket, final CliAppender appender) throws IOException {
		this.socket = socket;
		this.appender = appender;
		this.oos = new ObjectOutputStream(socket.getOutputStream());
	}

	@Override
	public void handleLogEvent(@SuppressWarnings("exports") final LogEvent logEvent) {
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
			}
		}
	}

}
