package de.mechrain.cli;

import java.io.DataInputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.lang3.StringUtils;
import org.apache.fory.exception.DeserializationException;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import de.mechrain.common.MechRainFory;
import de.mechrain.common.beans.AddSinkRequest;
import de.mechrain.common.beans.AddTaskRequest;
import de.mechrain.common.beans.ConsoleRequest;
import de.mechrain.common.beans.ConsoleResponse;
import de.mechrain.common.beans.DeviceConfigRequest;
import de.mechrain.common.beans.DeviceConfigResponse;
import de.mechrain.common.beans.DeviceListRequest;
import de.mechrain.common.beans.DeviceListResponse;
import de.mechrain.common.beans.DeviceResetRequest;
import de.mechrain.common.beans.EndConfigureDeviceRequest;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.LogEvent;
import de.mechrain.common.beans.MetricsRequest;
import de.mechrain.common.beans.MetricsResponse;
import de.mechrain.common.beans.ServerInfoResponse;
import de.mechrain.common.beans.DeviceMetricsData;
import de.mechrain.common.beans.RemoveDeviceRequest;
import de.mechrain.common.beans.RemoveSinkRequest;
import de.mechrain.common.beans.RemoveTaskRequest;
import de.mechrain.common.beans.SetDescriptionRequest;
import de.mechrain.common.beans.SetIdRequest;
import de.mechrain.common.beans.SetLedAllRgbRequest;
import de.mechrain.common.beans.SetLedMode1Request;
import de.mechrain.common.beans.SetNumPixelsRequest;
import de.mechrain.common.beans.SwitchToNonInteractiveRequest;
import de.mechrain.common.beans.DeviceListResponse.DeviceData;
import de.mechrain.common.beans.DeviceListResponse.DeviceData.SinkData;
import de.mechrain.common.beans.DeviceListResponse.DeviceData.TaskData;

public class ConsoleOutputRunner implements Runnable {
	
	private static final int MAX_MESSAGES = 100_000;
	
	private final InputStream is;
	private final DataOutputStream dos;
	private final MechRainTerminal terminal;
	private final LogConfig logConfig;

	private final Deque<LogMessage> logMessages = new ConcurrentLinkedDeque<>();
	
	private boolean updateConsole = true;

	private volatile boolean disconnected = false;

	/**
	 * Returns {@code true} if the connection to the server has been lost.
	 *
	 * @return {@code true} once the run loop exits due to a connection error
	 */
	public boolean isDisconnected() {
		return disconnected;
	}
	
	/**
	 * Creates a new {@code ConsoleOutputRunner} connected to the given server streams.
	 *
	 * @param is        the input stream from the server
	 * @param os        the output stream to the server
	 * @param terminal  the terminal used for user interaction and display
	 * @param logConfig the log configuration (filter level, timezone, etc.)
	 * @throws IOException if wrapping the output stream fails
	 */
	public ConsoleOutputRunner(final InputStream is, final OutputStream os, final MechRainTerminal terminal, final LogConfig logConfig) throws IOException {
		this.is = is;
		this.dos = new DataOutputStream(os);
		this.terminal = terminal;
		this.logConfig = logConfig;
	}

	/**
	 * Controls whether incoming log events are immediately rendered to the terminal.
	 *
	 * @param updateConsole {@code true} to print new events live; {@code false} to buffer silently
	 */
	public void setUpdateConsole(boolean updateConsole) {
		this.updateConsole = updateConsole;
	}
	
	/**
	 * Prints the current log buffer fill level (entries and percentage of the maximum) to the terminal.
	 */
	public void showBuffer() {
		final int logMsgCount = logMessages.size();
		terminal.printInfo(logMsgCount + "/" + MAX_MESSAGES + ' ' + (((float)logMsgCount / MAX_MESSAGES) * 100) + "%");
	}
	
	/**
	 * Requests a device list from the server; the response is rendered asynchronously
	 * by {@link #run} when it arrives.
	 */
	public void showDevices() {
		try {
			MechRainFory.serializeAndSend(DeviceListRequest.INSTANCE, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send device list request. " + e.getMessage());
		}
	}

	/**
	 * Requests server metrics from the server; the response is rendered asynchronously
	 * by {@link #run} when it arrives.
	 */
	public void showMetrics() {
		try {
			MechRainFory.serializeAndSend(MetricsRequest.INSTANCE, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send metrics request. " + e.getMessage());
		}
	}

	/**
	 * Enters device configuration mode for the device identified by the given string ID.
	 * Switches the terminal to the device reader on success.
	 *
	 * @param id the device ID as a decimal string
	 */
	public void configDevice(final String id) {
		try {
			final int deviceId = Integer.parseInt(id);
			final DeviceConfigRequest request = new DeviceConfigRequest();
			request.setDeviceId(deviceId);
			MechRainFory.serializeAndSend(request, dos);
			terminal.switchReader();
		} catch (final NumberFormatException e) {
			terminal.printError("Invalid device id " + id + " expected a number. " + e.getMessage());
		} catch (final IOException e) {
			terminal.printError("Could not send config device request. " + e.getMessage());
		}
	}
	
	/**
	 * Exits device configuration mode, clears the device config status bar,
	 * and switches the terminal back to the main reader.
	 */
	public void endConfigDevice() {
		try {
			final EndConfigureDeviceRequest request = new EndConfigureDeviceRequest();
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send end config device request. " + e.getMessage());
		} finally {
			terminal.clearDeviceConfigStatus();
			terminal.switchReader();
		}
	}
	
	/**
	 * Sends an add-sink request for the currently configured device.
	 * Switches the terminal to interactive mode so the server can prompt for sink details.
	 */
	public void addSink() {
		try {
			final AddSinkRequest request = new AddSinkRequest();
			MechRainFory.serializeAndSend(request, dos);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
	/**
	 * Sends a remove-sink request for the given sink ID on the currently configured device.
	 *
	 * @param id the ID of the sink to remove
	 */
	public void removeSink(final int id) {
		try {
			final RemoveSinkRequest request = new RemoveSinkRequest(id);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send remove sink request. " + e.getMessage());
		}
	}
	/**
	 * Sends an add-task request for the currently configured device.
	 * Switches the terminal to interactive mode so the server can prompt for task details.
	 */
	public void addTask() {
		try {
			final AddTaskRequest request = AddTaskRequest.INSTANCE;
			MechRainFory.serializeAndSend(request, dos);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add task request. " + e.getMessage());
		}
	}
	
	/**
	 * Sends a remove-task request for the given task ID on the currently configured device.
	 *
	 * @param id the ID of the task to remove
	 */
	public void removeTask(final int id) {
		try {
			final RemoveTaskRequest request = new RemoveTaskRequest(id);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
	/**
	 * Sends a remove-device request for the currently configured device
	 * and switches the terminal back to the main reader.
	 */
	public void removeDevice() {
		try {
			MechRainFory.serializeAndSend(RemoveDeviceRequest.INSTANCE, dos);
			terminal.switchReader();
		} catch (final IOException e) {
			terminal.printError("Could not send remove device request. " + e.getMessage());
		}
	}

	/**
	 * Sends a set device id request for the connected device.
	 * 
	 * @param id the new device id
	 */
	public void setDeviceId(int id) {
		try {
			final SetIdRequest request = new SetIdRequest(id);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}
	
	/**
	 * Sends a set device description request for the connected device.
	 * 
	 * @param description the new device description
	 */
	public void setDeviceDescription(final String description) {
		try {
			final SetDescriptionRequest request = new SetDescriptionRequest(description);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}

	/**
	 * Sends a set-num-pixels request for the currently configured device.
	 *
	 * @param numPixels the new number of LED pixels
	 */
	public void setDeviceNumPixels(int numPixels) {
		try {
			final SetNumPixelsRequest request = new SetNumPixelsRequest(numPixels);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}

	/**
	 * Sends a set-all-LED-RGB request for the currently configured device.
	 *
	 * @param r red channel value (0-255)
	 * @param g green channel value (0-255)
	 * @param b blue channel value (0-255)
	 */
	public void setDeviceLedRGB(final int r, final int g, final int b) {
		try {
			final SetLedAllRgbRequest request = new SetLedAllRgbRequest(r, g, b);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}

	/**
	 * Sends a set-LED-mode request for the currently configured device.
	 *
	 * @param mode the LED mode identifier
	 */
	public void setDeviceLedMode(final int mode) {
		try {
			final SetLedMode1Request request = SetLedMode1Request.INSTANCE;
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}
	
	/**
	 * Sends a device reset request to the connected device.
	 */
	public void resetDevice() {
		try {
			final DeviceResetRequest request = new DeviceResetRequest();
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not reset device. " + e.getMessage());
		}
	}
	
	/**
	 * Clears the in-memory log message buffer.
	 */
	public void clearBuffer() {
		logMessages.clear();
	}
	
	/**
	 * Determines whether a log message should be output based on the current filter settings.
	 * 
	 * @param msg the log message to evaluate
	 * @return true if the message should be output, false otherwise
	 */
	private boolean shouldOutput(final LogMessage msg) {
		if (msg.getLevel().intLevel() > logConfig.getFilterLevel().intLevel()) {
			return false;
		}

		switch (logConfig.getFilterBy()) {
		case DONT:
			return true;
		case LOG_NAME:
			return msg.getLoggerName().contains(logConfig.getFilterString());
		case TEXT:
			return msg.getText().contains(logConfig.getFilterString());
		default:
			return true;
		}
	}
	
	/**
	 * Redraws the console output based on the current log buffer and filter settings.
	 */
	public void redraw() {
		for (final Iterator<LogMessage> iterator = logMessages.iterator(); iterator.hasNext();) {
			final LogMessage msg = iterator.next();
			if (shouldOutput(msg)) {
				msg.toConsoleOutput(terminal, logConfig);
			}
		}
	}
	
	/**
	 * Dumps the current log buffer to a file.
	 * 
	 * @param fileName the file name to dump the log to
	 */
	public void dumpToFile(final String fileName) {
		final Path path = Paths.get(fileName);
		
		if (path.toFile().exists()) {
			final String line = terminal.readLine("Override? (yes/no)> ");
			if ( ! line.equalsIgnoreCase("yes")) {
				return;
			}
		}
		
		int entries = 0;
		final long start = System.currentTimeMillis();
		try (final FileOutputStream fos = new FileOutputStream(path.toFile())) {
			for (final Iterator<LogMessage> iterator = logMessages.iterator(); iterator.hasNext();) {
				final LogMessage msg = iterator.next();
				if (shouldOutput(msg)) {
					msg.toLogOutput(fos, logConfig);
					++entries;
				}
			}
			terminal.printInfo("wrote " + entries + " log entries in " + (System.currentTimeMillis() - start) + "ms");
		} catch (final IOException e) {
			terminal.printError("Could not dump log " + e.getMessage());
		}
	}

	/**
	 * Main receive loop. Reads {@link ICliBean} objects from the server stream
	 * and dispatches them to the appropriate handler methods.
	 * Sets {@link #disconnected} to {@code true} when the connection is lost
	 * and interrupts the terminal to trigger a reconnect.
	 */
	@Override
	public void run() {
		try (final DataInputStream dis = new DataInputStream(is)) {
			boolean connected = true;
			while (connected) {
				try {
					final ICliBean object = MechRainFory.receiveAndDeserialize(dis);
					if (object instanceof ServerInfoResponse serverInfo) {
						terminal.printInfo("Connected to MechRain Server v" + serverInfo.getVersion());
					} else if (object instanceof LogEvent event) {
						final LogMessage msg = new LogMessage(event);
						if (logMessages.size() > MAX_MESSAGES) {
							logMessages.removeFirst();
						}
						logMessages.add(msg);
						if (updateConsole && shouldOutput(msg)) {
							msg.toConsoleOutput(terminal, logConfig);
						}
					} else if (object instanceof DeviceListResponse devListResponse) {
						handleDeviceListResponse(devListResponse);
					} else if (object instanceof MetricsResponse metricsResponse) {
						handleMetricsResponse(metricsResponse);
					} else if (object instanceof DeviceConfigResponse deviceConfigResponse) {
						handleDeviceConfigResponse(deviceConfigResponse);
					} else if (object instanceof ConsoleRequest consoleRequest) {
						final String[] suggestions = consoleRequest.getSuggestions();
						final String response = (suggestions != null && suggestions.length > 0)
								? terminal.readLine(consoleRequest.getRequest() + '>', suggestions)
								: terminal.readLine(consoleRequest.getRequest() + '>');
						final ConsoleResponse consoleResponse = new ConsoleResponse();
						consoleResponse.setResponse(response);
						MechRainFory.serializeAndSend(consoleResponse, dos);
					} else if (object instanceof SwitchToNonInteractiveRequest) {
						terminal.setInteractive(false);
					} else {
						terminal.printError("Unhandled object " + object.getClass().getName());
					}
				} catch (final IOException e) {
					terminal.printError("Connection lost: " + e.getMessage());
					connected = false;
				} catch (final DeserializationException e) {
					terminal.printError("Deserialization error: " + e.getMessage());
					connected = false;
				} catch (final RuntimeException e) {
					terminal.printError("Receive error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
					connected = false;
				}
			}
		} catch (IOException e1) {
			terminal.printError("Failed to open connection stream: " + e1.getMessage());
		}
		terminal.setInteractive(false);
		disconnected = true;
		terminal.printWarning("Reconnecting...");
		terminal.interrupt();
	}
	
	/**
	 * Handles the device list response by formatting and displaying the device information in a table.
	 * 
	 * @param devListResponse the device list response to handle
	 */
	private void handleDeviceListResponse(final DeviceListResponse devListResponse) {
		final List<DeviceData> devices = new ArrayList<>(devListResponse.getDeviceList());
		devices.sort(new DeviceDataComparator());
		final DateTimeFormatter contactFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
				.withZone(logConfig.getZoneId());
		final AttributedStringBuilder deviceTable = new AttributedStringBuilder();
		deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
			.append(StringUtils.center("Device", 10)).append('|')
			.append(StringUtils.center("Description", 40)).append('|')
			.append(StringUtils.center("BuildId", 20)).append('|')
			.append(StringUtils.center("Status", 15)).append('|')
			.append(StringUtils.center("Last contact", 20)).append('\n')
			.append(StringUtils.repeat('-', 108)).append('\n');
		for (final DeviceData device : devices) {
			final String description = device.getDescription();
			final String buildId = device.getBuildId();
			if (device.isConnected()) {
				deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
				.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|')
				.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|')
				.append(StringUtils.rightPad(buildId != null ? buildId : " ", 20)).append('|')
				.append(StringUtils.center("connected", 15)).append('|')
				.append(StringUtils.repeat(' ', 20)).append('\n');
			} else {
				final long lc = device.getLastContactAt();
				final String lastContact = lc > 0 ? contactFmt.format(Instant.ofEpochMilli(lc)) : "never";
				deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
				.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|')
				.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|')
				.append(StringUtils.rightPad(buildId != null ? buildId : " ", 20)).append('|')
				.append(StringUtils.center("disconnected", 15)).append('|')
				.append(StringUtils.center(lastContact, 20)).append('\n');
			}
		}
		terminal.printAbove(deviceTable);
	}
	
	/**
	 * Handles the device configuration response by displaying the task-to-sink flow diagram.
	 * Each task lists the sinks it feeds into, matched by the sink's measurement filter.
	 * Tasks in green, sink arrows in yellow, unmatched tasks in red.
	 *
	 * @param deviceConfigResponse the device configuration response to handle
	 */
	private void handleDeviceConfigResponse(final DeviceConfigResponse deviceConfigResponse) {
		final DeviceData d = deviceConfigResponse.deviceData;
		final AttributedStringBuilder sb = new AttributedStringBuilder();

		sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
		sb.append("Device ").append(String.valueOf(d.getId()));
		if (d.getName() != null && !d.getName().isEmpty()) {
			sb.append(" \u2013 ").append(d.getName());
		}
		sb.append("  [").append(d.isConnected() ? "\u25cf connected" : "\u25cb disconnected").append("]\n");
		sb.append("\u2501".repeat(60)).append("\n\n");

		final List<TaskData> tasks = d.getTasks().values().stream()
				.sorted(Comparator.comparingInt(TaskData::getId))
				.toList();
		final List<SinkData> sinks = d.getSinks().values().stream()
				.sorted(Comparator.comparingInt(SinkData::getId))
				.toList();

		if (tasks.isEmpty()) {
			sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
			sb.append("  (no tasks configured)\n");
		} else {
			for (final TaskData task : tasks) {
				sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
				.append("  Task #").append(String.valueOf(task.getId()))
				.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
					.append("  ").append(task.getMeasurement())
					.append("  ").append(String.valueOf(task.getInterval())).append(' ').append(task.getTimeUnit());
				if (task.getChannelId() != null) {
					sb.append("  [ch:").append(String.valueOf(task.getChannelId())).append(']');
				}
				if (task.isAdaptive()) {
					sb.append("  [adaptive]");
				}
				sb.append('\n');

				final List<SinkData> matching = sinks.stream()
						.filter(s -> s.getFilterNames() == null || s.getFilterNames().contains(task.getMeasurement()))
						.toList();

				if (matching.isEmpty()) {
					sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
					sb.append("      (no matching sinks)\n");
				} else {
					for (int i = 0; i < matching.size(); i++) {
						final SinkData sink = matching.get(i);
						sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
						sb.append(i == matching.size() - 1 ? "      \u2514\u2500\u2500\u25ba " : "      \u251c\u2500\u2500\u25ba ");
						sb.append('#').append(String.valueOf(sink.getId()))
							.append("  ").append(sink.getType());
						if (sink.getDescription() != null && !sink.getDescription().isEmpty()) {
							sb.append("  ").append(sink.getDescription());
						}
						sb.append('\n');
					}
				}
				sb.style(AttributedStyle.DEFAULT);
				sb.append('\n');
			}
		}

		terminal.showDeviceConfigStatus(toStatusLines(sb));
	}

	private List<AttributedString> toStatusLines(final AttributedStringBuilder sb) {
		final AttributedString full = sb.toAttributedString();
		final List<AttributedString> result = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < full.length(); i++) {
			if (full.charAt(i) == '\n') {
				result.add(full.subSequence(start, i));
				start = i + 1;
			}
		}
		if (start < full.length()) {
			result.add(full.subSequence(start, full.length()));
		}
		return result;
	}
	
	static class DeviceDataComparator implements Comparator<DeviceData> {
		@Override
		public int compare(final DeviceData device1, final DeviceData device2) {
			return Integer.compare(device1.getId(), device2.getId());
		}
	}

	/**
	 * Handles the metrics response by displaying per-device message and byte statistics
	 * for the last hour, day, week, and month in a table.
	 *
	 * @param metricsResponse the metrics response to handle
	 */
	private void handleMetricsResponse(final MetricsResponse metricsResponse) {
		final List<DeviceMetricsData> list = metricsResponse.getDeviceMetricsList();
		final DeviceMetricsData total = metricsResponse.getTotalMetrics();
		final DeviceMetricsData cli = metricsResponse.getCliMetrics();

		if ((list == null || list.isEmpty()) && total == null && cli == null) {
			terminal.printInfo("No metrics available.");
			return;
		}

		final int COL_W = 12;
		final String SEP = StringUtils.repeat('-', 6 + COL_W * 4 + 3);
		final String header = StringUtils.rightPad(" Window", 8) + '|'
				+ StringUtils.center("Msgs Sent", COL_W) + '|'
				+ StringUtils.center("Msgs Recv", COL_W) + '|'
				+ StringUtils.center("Bytes Sent", COL_W) + '|'
				+ StringUtils.center("Bytes Recv", COL_W);

		if (list != null) {
			for (final DeviceMetricsData d : list) {
				final AttributedStringBuilder sb = new AttributedStringBuilder();
				sb.style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.CYAN));
				sb.append("Device ").append(String.valueOf(d.getDeviceId()))
				  .append(" (").append(d.getDeviceName()).append(")\n");
				sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
				sb.append(header).append('\n');
				sb.append(SEP).append('\n');
				appendMetricsRow(sb, "Hour",  d.getMsgSentHour(),  d.getMsgReceivedHour(),  d.getBytesSentHour(),  d.getBytesReceivedHour(),  COL_W);
				appendMetricsRow(sb, "Day",   d.getMsgSentDay(),   d.getMsgReceivedDay(),   d.getBytesSentDay(),   d.getBytesReceivedDay(),   COL_W);
				appendMetricsRow(sb, "Week",  d.getMsgSentWeek(),  d.getMsgReceivedWeek(),  d.getBytesSentWeek(),  d.getBytesReceivedWeek(),  COL_W);
				appendMetricsRow(sb, "Month", d.getMsgSentMonth(), d.getMsgReceivedMonth(), d.getBytesSentMonth(), d.getBytesReceivedMonth(), COL_W);
				sb.append('\n');
				terminal.printAbove(sb);
			}
		}

		if (total != null) {
			final AttributedStringBuilder sb = new AttributedStringBuilder();
			sb.style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.WHITE));
			sb.append("\u2014 ").append(total.getDeviceName()).append(" \u2014\n");
			sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
			sb.append(header).append('\n');
			sb.append(SEP).append('\n');
			appendMetricsRow(sb, "Hour",  total.getMsgSentHour(),  total.getMsgReceivedHour(),  total.getBytesSentHour(),  total.getBytesReceivedHour(),  COL_W);
			appendMetricsRow(sb, "Day",   total.getMsgSentDay(),   total.getMsgReceivedDay(),   total.getBytesSentDay(),   total.getBytesReceivedDay(),   COL_W);
			appendMetricsRow(sb, "Week",  total.getMsgSentWeek(),  total.getMsgReceivedWeek(),  total.getBytesSentWeek(),  total.getBytesReceivedWeek(),  COL_W);
			appendMetricsRow(sb, "Month", total.getMsgSentMonth(), total.getMsgReceivedMonth(), total.getBytesSentMonth(), total.getBytesReceivedMonth(), COL_W);
			sb.append('\n');
			terminal.printAbove(sb);
		}

		if (cli != null) {
			final AttributedStringBuilder sb = new AttributedStringBuilder();
			sb.style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.MAGENTA));
			sb.append("\u2014 ").append(cli.getDeviceName()).append(" \u2014\n");
			sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
			sb.append(header).append('\n');
			sb.append(SEP).append('\n');
			appendMetricsRow(sb, "Hour",  cli.getMsgSentHour(),  cli.getMsgReceivedHour(),  cli.getBytesSentHour(),  cli.getBytesReceivedHour(),  COL_W);
			appendMetricsRow(sb, "Day",   cli.getMsgSentDay(),   cli.getMsgReceivedDay(),   cli.getBytesSentDay(),   cli.getBytesReceivedDay(),   COL_W);
			appendMetricsRow(sb, "Week",  cli.getMsgSentWeek(),  cli.getMsgReceivedWeek(),  cli.getBytesSentWeek(),  cli.getBytesReceivedWeek(),  COL_W);
			appendMetricsRow(sb, "Month", cli.getMsgSentMonth(), cli.getMsgReceivedMonth(), cli.getBytesSentMonth(), cli.getBytesReceivedMonth(), COL_W);
			sb.append('\n');
			terminal.printAbove(sb);
		}
	}

	private void appendMetricsRow(final AttributedStringBuilder sb, final String label,
			final long msgSent, final long msgRecv, final long bytesSent, final long bytesRecv, final int colW) {
		sb.append(StringUtils.rightPad(' ' + label, 8)).append('|')
		  .append(StringUtils.leftPad(String.valueOf(msgSent), colW - 1)).append(' ').append('|')
		  .append(StringUtils.leftPad(String.valueOf(msgRecv), colW - 1)).append(' ').append('|')
		  .append(StringUtils.leftPad(formatBytes(bytesSent), colW - 1)).append(' ').append('|')
		  .append(StringUtils.leftPad(formatBytes(bytesRecv), colW - 1)).append(' ').append('\n');
	}

	private static String formatBytes(final long bytes) {
		if (bytes < 1024L)               return bytes + " B";
		if (bytes < 1024L * 1024L)       return String.format("%.1f KB", bytes / 1024.0);
		if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
		return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
	}
}
