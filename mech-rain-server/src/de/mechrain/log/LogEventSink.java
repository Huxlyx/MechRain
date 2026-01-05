package de.mechrain.log;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Interface for handling log events.
 */
public interface LogEventSink {
	
	/**
	 * Handles a log event.
	 *
	 * @param logEvent the log event to handle
	 */
	void handleLogEvent(LogEvent logEvent);
	
}
