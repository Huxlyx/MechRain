package de.mechrain.log;

import org.apache.logging.log4j.core.LogEvent;

public interface LogEventSink {
	
	void handleLogEvent(LogEvent logEvent);
}
