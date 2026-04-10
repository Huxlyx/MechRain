package de.mechrain.cli;

import java.io.DataInputStream;
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
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.lang3.StringUtils;
import org.apache.fory.exception.DeserializationException;
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

public class ConsoleOutputRunner implements Runnable {
	
	private static final int MAX_MESSAGES = 10_000;
	
	private final InputStream is;
	private final DataOutputStream dos;
	private final MechRainTerminal terminal;
	private final LogConfig logConfig;

	private final Deque<LogMessage> logMessages = new ConcurrentLinkedDeque<>();
	
	private boolean updateConsole = true;

	private volatile boolean disconnected = false;

	public boolean isDisconnected() {
		return disconnected;
	}
	
	public ConsoleOutputRunner(final InputStream is, final OutputStream os, final MechRainTerminal terminal, final LogConfig logConfig) throws IOException {
		this.is = is;
		this.dos = new DataOutputStream(os);
		this.terminal = terminal;
		this.logConfig = logConfig;
	}

	public void setUpdateConsole(boolean updateConsole) {
		this.updateConsole = updateConsole;
	}
	
	public void showBuffer() {
		final int logMsgCount = logMessages.size();
		terminal.printInfo(logMsgCount + "/" + MAX_MESSAGES + ' ' + (((float)logMsgCount / MAX_MESSAGES) * 100) + "%");
	}
	
	public void showDevices() {
		try {
			MechRainFory.serializeAndSend(DeviceListRequest.INSTANCE, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send device list request. " + e.getMessage());
		}
	}

	public void showMetrics() {
		try {
			MechRainFory.serializeAndSend(MetricsRequest.INSTANCE, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send metrics request. " + e.getMessage());
		}
	}

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
	
	public void endConfigDevice() {
		try {
			final EndConfigureDeviceRequest request = new EndConfigureDeviceRequest();
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send end config device request. " + e.getMessage());
		} finally {
			terminal.switchReader();
		}
	}
	
	public void addSink() {
		try {
			final AddSinkRequest request = new AddSinkRequest();
			MechRainFory.serializeAndSend(request, dos);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
	public void removeSink(final int id) {
		try {
			final RemoveSinkRequest request = new RemoveSinkRequest(id);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send remove sink request. " + e.getMessage());
		}
	}
	public void addTask() {
		try {
			final AddTaskRequest request = AddTaskRequest.INSTANCE;
			MechRainFory.serializeAndSend(request, dos);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add task request. " + e.getMessage());
		}
	}
	
	public void removeTask(final int id) {
		try {
			final RemoveTaskRequest request = new RemoveTaskRequest(id);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
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

	public void setDeviceNumPixels(int numPixels) {
		try {
			final SetNumPixelsRequest request = new SetNumPixelsRequest(numPixels);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}

	public void setDeviceLedRGB(final int r, final int g, final int b) {
		try {
			final SetLedAllRgbRequest request = new SetLedAllRgbRequest(r, g, b);
			MechRainFory.serializeAndSend(request, dos);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}

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
		final AttributedStringBuilder deviceTable = new AttributedStringBuilder();
		deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
		deviceTable
			.append(StringUtils.center("Device", 10)).append('|')
			.append(StringUtils.center("Description", 40)).append('|')
			.append(StringUtils.center("BuildId", 20)).append('|')
			.append(StringUtils.center("Status", 15)).append('\n');
		deviceTable.append(StringUtils.repeat('-', 87)).append('\n');
		for (final DeviceData device : devices) {
			final String description = device.getDescription();
			final String buildId = device.getBuildId();
			if (device.isConnected()) {
				deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
				deviceTable.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|');
				deviceTable.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|');
				deviceTable.append(StringUtils.rightPad(buildId != null ? buildId : " ", 20)).append('|');
				deviceTable.append(StringUtils.center("connected", 15)).append('\n');
			} else {
				deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
				deviceTable.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|');
				deviceTable.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|');
				deviceTable.append(StringUtils.rightPad(buildId != null ? buildId : " ", 20)).append('|');
				deviceTable.append(StringUtils.center("disconnected", 15)).append('\n');
			}
		}
		terminal.printAbove(deviceTable);
	}
	
	/**
	 * Handles the device configuration response by displaying the configuration details.
	 * 
	 * @param deviceConfigResponse the device configuration response to handle
	 */
	private void handleDeviceConfigResponse(final DeviceConfigResponse deviceConfigResponse) {
		final AttributedStringBuilder deviceConfig = new AttributedStringBuilder();
		deviceConfig.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
		deviceConfig.append("Device ").append(String.valueOf(deviceConfigResponse.deviceData.getId())).append(" Configuration:\n");
		deviceConfig.append("Tasks:").append('\n');
		for (Entry<Integer, String> entry : deviceConfigResponse.deviceData.getTasks().entrySet()) {
			deviceConfig.append("  Task ").append(String.valueOf(entry.getKey())).append("): ").append(entry.getValue()).append('\n');
		}
		deviceConfig.append('\n');
		deviceConfig.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
		deviceConfig.append("Sinks:").append('\n');
		for (final Entry<Integer, String> entry : deviceConfigResponse.deviceData.getSinks().entrySet()) {
			deviceConfig.append("  Sink ").append(String.valueOf(entry.getKey())).append("): ").append(entry.getValue()).append('\n');
		}
		deviceConfig.append('\n');
		
		terminal.printAbove(deviceConfig);
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
