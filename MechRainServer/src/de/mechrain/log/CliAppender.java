package de.mechrain.log;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "CliAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class CliAppender extends AbstractAppender {
	
	private static final int MAX_SAVED_EVENTS = 5_000;
	
	private final List<LogEventSink> sinks;
	private final List<LogEventSink> sinksToRemove;
	
	private final Deque<LogEvent> logEvents = new ArrayDeque<>();

	protected CliAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout, final boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
		this.sinks = Collections.synchronizedList(new ArrayList<LogEventSink>());
		this.sinksToRemove = Collections.synchronizedList(new ArrayList<LogEventSink>());
	}
	
	public void addSink(final LogEventSink sink) {
		for (final Iterator<LogEvent> iterator = logEvents.iterator(); iterator.hasNext();) {
			final LogEvent logEvent = iterator.next();
			sink.handleLogEvent(logEvent);
		}
		sinks.add(sink);
	}
	
	public void removeSink(final LogEventSink sink) {
		sinksToRemove.add(sink);
	}

	@Override
	public void append(final LogEvent event) {
		if (event.getLevel().intLevel() <= Level.INFO.intLevel()) {
			/* save INFO, WARN, LOG messages for any sink that connects */
			if (logEvents.size() > MAX_SAVED_EVENTS) {
				logEvents.removeFirst();
			}
			logEvents.add(Log4jLogEvent.createMemento(event));
		}
		if (sinks.isEmpty()) {
			return;
		}
		synchronized(sinks) {
			final Iterator<LogEventSink> iterator = sinks.iterator();
			while (iterator.hasNext()) {
				final LogEventSink sink = iterator.next();
				sink.handleLogEvent(event);
			}
			if ( ! sinksToRemove.isEmpty()) {
				sinks.removeAll(sinksToRemove);
				sinksToRemove.clear();
			}
		}
	}

	@PluginFactory
    public static CliAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter) {
        if (name == null) {
            LOGGER.error("No name provided for TestLoggerAppender");
            return null;
        }

        if (layout == null) layout = PatternLayout.createDefaultLayout();

        return new CliAppender(name, filter, layout, true);
    }
	
}
