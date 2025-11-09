package de.mechrain.cmdline.beans;

public class LogEvent implements ICliBean {
	
	private static final long serialVersionUID = 12345L;
	
	private final String loggerName;
	private final long timestamp;
	private final int level;
	private final String message;
	
	private LogEvent(final String loggerName, final long timestamp, final int level, final String message) {
		this.loggerName = loggerName;
		this.timestamp = timestamp;
		this.level = level;
		this.message = message;
	}
	
	public String getLoggerName() {
		return loggerName;
	}
	
	public long getTimeMillis() {
		return timestamp;
	}
	
	public int getLevel() {
		return level;
	}
	
	public String getFormattedMessage() {
		return message;
	}

	public static LogEvent fromLog4jEvent(final org.apache.logging.log4j.core.LogEvent log4jEvent) {
		return new LogEvent(
			log4jEvent.getLoggerName(),
			log4jEvent.getTimeMillis(),
			log4jEvent.getLevel().intLevel(),
			log4jEvent.getMessage().getFormattedMessage()
		);
	}
}
