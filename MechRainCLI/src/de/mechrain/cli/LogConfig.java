package de.mechrain.cli;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.apache.logging.log4j.spi.StandardLevel;

public class LogConfig {
	
	public enum FilterBy {
		LOG_NAME,
		TEXT,
		DONT
	}
	
	private static final String PROPERTIES_FILE_NAME = "logconf.properties";
	
	private static final String SHOW_TIME = "showTime";
	private static final String SHOW_LOGGER_NAME = "showLoggerName";
	private static final String FILTER_LEVEL = "filterLevel";
	private static final String FILTER_BY = "filterBy";
	private static final String FILTER_STRING = "filterString";
	
	private boolean showTime = true;
	private boolean showLoggerName = true;
	private StandardLevel filterLevel = StandardLevel.TRACE;
	private FilterBy filterBy = FilterBy.DONT;
	private String filterString;
	
	private final String timeColonPattern = "HH:mm:ss.SSS";
	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(timeColonPattern);
	private final ZoneId zoneId;
	
	final Properties properties;
	
	public LogConfig() {
      	this.zoneId = ZonedDateTime.now().getZone();
      	this.properties = new Properties();
      	loadPropertiesOrDefault(properties);
	}
	
	private void loadPropertiesOrDefault(final Properties properties) {
      	final Path path = Paths.get(PROPERTIES_FILE_NAME);
      	boolean didLoad = false;
      	if (path.toFile().exists()) {
      		try (final FileInputStream fis = new FileInputStream(path.toFile())) {
      			properties.load(fis);
      			didLoad = true;
      		} catch (final FileNotFoundException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			}
      	}
      	
      	if (didLoad) {
      		setFilterLevel(StandardLevel.valueOf(properties.getProperty(FILTER_LEVEL, StandardLevel.TRACE.name())));
      		setShowLoggerName(Boolean.valueOf(properties.getProperty(SHOW_LOGGER_NAME, Boolean.TRUE.toString())));
      		setShowTime(Boolean.valueOf(properties.getProperty(SHOW_TIME, Boolean.TRUE.toString())));
      		setFilterBy(FilterBy.valueOf(properties.getProperty(FILTER_BY, FilterBy.DONT.name())));
      		setFilterString(properties.getProperty(FILTER_STRING, ""));
      	} else {
      		setFilterLevel(StandardLevel.TRACE);
      		setShowLoggerName(true);
      		setShowTime(true);
      		setFilterBy(FilterBy.DONT);
      		setFilterString("");
      		persist();
      	}
	}
	
	public void persist() {
		persist(properties);
	}
	
	private void persist(final Properties properties) {
		try (final FileOutputStream fos = new FileOutputStream(Paths.get(PROPERTIES_FILE_NAME).toFile())) {
			properties.store(fos, null);
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public void setFilterLevel(final StandardLevel filterLevel) {
		properties.put(FILTER_LEVEL, filterLevel.name());
  		persist();
		this.filterLevel = filterLevel;
	}

	public StandardLevel getFilterLevel() {
		return filterLevel;
	}

	public void setShowLoggerName(final boolean showLoggerName) {
		properties.put(SHOW_LOGGER_NAME, String.valueOf(showLoggerName));
  		persist();
		this.showLoggerName = showLoggerName;
	}

	public boolean isShowLoggerName() {
		return showLoggerName;
	}

	public void setShowTime(final boolean showTime) {
		properties.put(SHOW_TIME, String.valueOf(showTime));
  		persist();
		this.showTime = showTime;
	}

	public boolean isShowTime() {
		return showTime;
	}

	public void setFilterBy(final FilterBy filterBy) {
		properties.put(FILTER_BY, filterBy.name());
  		persist();
		this.filterBy = filterBy;
	}
	
	public FilterBy getFilterBy() {
		return filterBy;
	}

	public void setFilterString(final String filterString) {
		properties.put(FILTER_STRING, filterString);
  		persist();
		this.filterString = filterString;
	}

	public String getFilterString() {
		return filterString;
	}

	public ZoneId getZoneId() {
		return zoneId;
	}

	public DateTimeFormatter getTimeFormatter() {
		return timeFormatter;
	}
}
