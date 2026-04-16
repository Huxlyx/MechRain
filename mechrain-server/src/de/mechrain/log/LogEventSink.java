package de.mechrain.log;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Interface for handling log events.
 */
public interface LogEventSink {
	
	/**
	 * Handles a log event. Implementations that retain the event past the call
	 * (e.g. by queuing it for async processing) must call {@link LogEvent#toImmutable()}
	 * before storing the reference, because Log4j2 may recycle the underlying
	 * {@code MutableLogEvent} as soon as the appender's {@code append()} method returns.
	 *
	 * @param logEvent the log event to handle
	 */
	void handleLogEvent(LogEvent logEvent);
	
}
