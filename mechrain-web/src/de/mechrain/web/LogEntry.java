package de.mechrain.web;

/**
 * A single log line for the web UI. Mirrors the shape of the prototype's dummy logs
 * ({@code id}, {@code ts}, {@code level}, {@code message}).
 */
public class LogEntry {

	private final long id;
	private final long ts;
	private final String level;
	private final String message;
	private final String logger;

	public LogEntry(final long id, final long ts, final String level, final String message, final String logger) {
		this.id = id;
		this.ts = ts;
		this.level = level;
		this.message = message;
		this.logger = logger;
	}

	public long getId() {
		return id;
	}

	public long getTs() {
		return ts;
	}

	public String getLevel() {
		return level;
	}

	public String getMessage() {
		return message;
	}

	public String getLogger() {
		return logger;
	}
}
