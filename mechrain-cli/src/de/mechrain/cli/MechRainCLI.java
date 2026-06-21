package de.mechrain.cli;

import static de.mechrain.cli.MechRainTerminal.CLEAR;
import static de.mechrain.cli.MechRainTerminal.CONFIG;
import static de.mechrain.cli.MechRainTerminal.DUMP;
import static de.mechrain.cli.MechRainTerminal.FILTER;
import static de.mechrain.cli.MechRainTerminal.RECONNECT;
import static de.mechrain.cli.MechRainTerminal.SET;
import static de.mechrain.cli.MechRainTerminal.SHOW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.spi.StandardLevel;
import org.jline.reader.UserInterruptException;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import de.mechrain.cli.LogConfig.FilterBy;
import org.apache.fory.logging.LoggerFactory;

public class MechRainCLI implements Callable<Integer> {

	int port = 5000;
	boolean reconnect = true;

	static long start;

	private final MechRainTerminal terminal;
	
	private final boolean testMode;

	public MechRainCLI(final MechRainTerminal terminal, final boolean testMode) {
		this.terminal = terminal;
		this.testMode = testMode;
	}

	@Override
	public Integer call() throws Exception {
		final LogConfig config = new LogConfig();
		do {
			final Socket socket = connect(port);
			if (socket == null) {
				if (reconnect) {
					System.out.println("Re-attempting");
					continue;
				} else {
					System.err.println("Could not get connection");
					return 7;
				}
			} else {
				terminal.clear();
				terminal.printHeader();
				terminal.printInfo("Connection established (took " + (System.currentTimeMillis() - start) + "ms) ");
			}
			
			try (final InputStream inputStream = socket.getInputStream();
					final OutputStream outputStream = socket.getOutputStream()) {
				final ConsoleOutputRunner outputRunner = new ConsoleOutputRunner(inputStream, outputStream, terminal, config);
				final Thread cliThread = new Thread(outputRunner);
				cliThread.start();
				
				boolean running = true;
				while (running) {
					terminal.maybeWaitForNonInteractive();
					String line = null;
					try {
						line = terminal.readLine("MechRain> ");
					} catch (final UserInterruptException e) {
						if (outputRunner.isDisconnected()) {
							running = false;
							continue;
						} else if (terminal.getMode() == MechRainTerminal.Mode.DEVICE) {
							terminal.printInfo("Switched to general mode");
							outputRunner.endConfigDevice();
							continue;
						} else {
							terminal.printInfo("Press Ctrl+C again to exit");
							try {
								line = terminal.readLine("MechRain> ");
							} catch (final UserInterruptException e2) {
								terminal.printInfo("Exiting CLI");
								running = false;
								reconnect = false;
								continue;
							}
						}
					}
					if (line == null) continue;
					
					final String[] splits = line.split(" ");
					
					switch (terminal.getMode()) {
					case GENERAL:
						running = handleGeneral(splits, outputRunner, config, socket);
						break;
					case DEVICE:
						handleDevice(splits, outputRunner);
						break;
					}
				}
			}
		}
		while (reconnect);
		return 1;
	}

	private boolean handleGeneral(final String[] splits, final ConsoleOutputRunner outputRunner, final LogConfig config, final Socket socket) throws IOException {
		boolean redraw = false;
		boolean running = true;
		switch (splits[0].toLowerCase()) {
		case CLEAR:
			if (splits.length == 2 && splits[1].equalsIgnoreCase("buffer")) {
				outputRunner.clearBuffer();
			} else {
				terminal.clear();
			}
			break;
		case CONFIG:
			if (splits.length != 3) {
				terminal.printError("expected 'device' and number");
				return true;
			}
			outputRunner.configDevice(splits[2]);
			break;
		case DUMP:
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return true;
			}
			outputRunner.dumpToFile(splits[1]);
			break;
		case FILTER:
			if (splits.length < 2) {
				terminal.printError("expected at least 2 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "logname":
				if (splits.length < 3) {
					terminal.printError("expected at least 3 arguments but got " + splits.length);
					return true;
				}
				config.setFilterBy(FilterBy.LOG_NAME);
				config.setFilterString(splits[2]);
				redraw = true;
				break;
			case "text":
				if (splits.length < 3) {
					terminal.printError("expected at least 3 arguments but got " + splits.length);
					return true;
				}
				config.setFilterBy(FilterBy.TEXT);
				config.setFilterString(splits[2]);
				redraw = true;
				break;
			case "off":
				config.setFilterBy(FilterBy.DONT);
				redraw = true;
				break;
			default:
				terminal.printError("Unknown filter option " + splits[1]);
				break;
			}
			break;
		case SHOW:
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "buffer":
				outputRunner.showBuffer();
				break;
			case "devices":
				outputRunner.showDevices();
				break;
			case "metrics":
				outputRunner.showMetrics();
				break;
			case "diagram":
				showDiagram();
				break;
			default:
				terminal.printError("Unknown show option " + splits[1]);
				break;
			}
			break;
		case SET:
			if (splits.length != 3) {
				terminal.printError("expected 3 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "level":
				switch (splits[2].toLowerCase()) {
				case "off":
					config.setFilterLevel(StandardLevel.OFF);
					redraw = true;
					break;
				case "err":
					config.setFilterLevel(StandardLevel.ERROR);
					redraw = true;
					break;
				case "warn":
					config.setFilterLevel(StandardLevel.WARN);
					redraw = true;
					break;
				case "info":
					config.setFilterLevel(StandardLevel.INFO);
					redraw = true;
					break;
				case "debug":
					config.setFilterLevel(StandardLevel.DEBUG);
					redraw = true;
					break;
				case "trace":
					config.setFilterLevel(StandardLevel.TRACE);
					redraw = true;
					break;
				default:
					terminal.printError("Unknown level '" + splits[2] + "'");
					redraw = false;
					break;
				}
				break;
			case "date":
				switch (splits[2].toLowerCase()) {
				case "on":
					config.setShowDate(true);
					break;
				case "off":
					config.setShowDate(false);
					break;
				default:
					terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
					return true;
				}
				redraw = true;
				break;
			case "time":
				switch (splits[2].toLowerCase()) {
				case "on":
					config.setShowTime(true);
					break;
				case "off":
					config.setShowTime(false);
					break;
				default:
					terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
					return true;
				}
				redraw = true;
				break;
			case "logname":
				switch (splits[2].toLowerCase()) {
				case "on":
					config.setShowLoggerName(true);
					break;
				case "off":
					config.setShowLoggerName(false);
					break;
				default:
					terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
					return true;
				}
				redraw = true;
				break;
			}
			break;
		case RECONNECT:
			socket.close();
			running = false;
			reconnect = true;
			start = System.currentTimeMillis();
			break;
		case "switch":
			terminal.switchReader();
			break;
		default:
			terminal.printError("Unknown option " + splits[0]);
			break;
		}

		if (redraw) {
			try {
				outputRunner.setUpdateConsole(false);
				outputRunner.redraw();
			} finally {
				outputRunner.setUpdateConsole(true);
			}
		}

		return running;
	}

	private void handleDevice(final String[] splits, final ConsoleOutputRunner outputRunner) {
		switch (splits[0].toLowerCase()) {
		case "add":
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return;
			}
			switch (splits[1].toLowerCase()) {
			case "sink":
				outputRunner.addSink();
				break;
			case "task":
				outputRunner.addTask();
				break;
			default:
				terminal.printError("Unknown add option '" + splits[1] + "'");
				return;
			} 
			break;
		case "exit":
			outputRunner.endConfigDevice();
			break;
		case "remove":
			if (splits.length != 3) {
				terminal.printError("expected 3 arguments but got " + splits.length);
				return;
			}
			switch (splits[1].toLowerCase()) {
			case "sink":
				try {
					final int id = Integer.parseInt(splits[2]);
					outputRunner.removeSink(id);
				} catch (final NumberFormatException e) {
					terminal.printError("Not a valid id:" + splits[2]);
					return;
				}
				break;
			case "task":
				try {
					final int id = Integer.parseInt(splits[2]);
					outputRunner.removeTask(id);
				} catch (final NumberFormatException e) {
					terminal.printError("Not a valid id:" + splits[2]);
					return;
				}
				break;
			case "device":
				outputRunner.removeDevice();
				break;
			default:
				terminal.printError("Unknown remove option '" + splits[1] + "'");
				return;
			} 
			break;
		case "reset":
			outputRunner.resetDevice();
			break;
		case "replace":
			if (splits.length != 2) {
				terminal.printError("expected 'replace <device_id>'");
				return;
			}
			try {
				final int targetId = Integer.parseInt(splits[1]);
				outputRunner.replaceDevice(targetId);
			} catch (final NumberFormatException e) {
				terminal.printError("Not a valid device id: " + splits[1]);
			}
			break;
		case "set":
			if (splits.length < 3) {
				terminal.printError("expected 3 arguments but got " + splits.length);
				return;
			}
			switch(splits[1].toLowerCase()) {
			case "id":
				try {
					final int id = Integer.parseInt(splits[2]);
					outputRunner.setDeviceId(id);
				} catch (final NumberFormatException e) {
					terminal.printError("Not a valid id:" + splits[2]);
				}
				break;
			case "description":
				final String description = StringUtils.join(splits, ' ', 2, splits.length);
				outputRunner.setDeviceDescription(description);
				break;
			case "led":
				if (splits.length < 4) {
					terminal.printError("expected at least 4 arguments but got " + splits.length);
					return;
				}
				switch (splits[2].toLowerCase()) {
				case "count":
					try {
						final int numPixels = Integer.parseInt(splits[3]);
						outputRunner.setDeviceNumPixels(numPixels);
					} catch (final NumberFormatException e) {
						terminal.printError("Not a valid number of pixels:" + splits[3]);
					}
					break;
				case "rgb":
					if (splits.length != 6) {
						terminal.printError("expected 5 arguments (rgb 0-255 0-255 0-255) but got" + splits.length);
					} else {
						try {
							final int r = Integer.parseInt(splits[3]);
							final int g = Integer.parseInt(splits[4]);
							final int b = Integer.parseInt(splits[5]);
							if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) {
								outputRunner.setDeviceLedRGB(r, g, b);
							} else {
								terminal.printError("RGB values must be between 0 and 255");
							}
						} catch (final NumberFormatException e) {
							terminal.printError("Not a valid RGB value:" + splits[3] + ", " + splits[4] + ", " + splits[5]);
						}
					}
					break;
				case "mode":
					try {
						final int mode = Integer.parseInt(splits[3]);
						outputRunner.setDeviceLedMode(mode);
					} catch (final NumberFormatException e) {
						terminal.printError("Not a valid LED mode:" + splits[3]);
					}
					break;
				}
				break;
			default:
				terminal.printError("Unknown set option '" + splits[1] + "'");
				return;
			}
			break;
			
		default:
			terminal.printError("Unknown option " + splits[0]);
			break;
		}
	}

	private void showDiagram() {
		final AttributedStringBuilder asb = new AttributedStringBuilder();
		asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN));
		asb.append("          *+----------------------+           \n");
		asb.append("           |[3V3]            [GND]|           \n");
		asb.append("           |[EN ]            [G23]|           \n");
		asb.append("     [IN0 ]|[G36]  +------+  [G22]|[SHT-SCL]  \n");
		asb.append("     [IN1 ]|[G39]  |ESP-32|  [G1 ]|           \n");
		asb.append("     [IN2 ]|[G34]  +------+  [G3 ]|           \n");
		asb.append("     [IN3 ]|[G35]            [G21]|[SHT-SDA]  \n");
		asb.append("     [IN4 ]|[G32]            [GND]|           \n");
		asb.append("     [IN5 ]|[G33]            [G19]|           \n");
		asb.append("     [IN6 ]|[G25]            [G18]|           \n");
		asb.append("     [IN7 ]|[G26]            [G5 ]|           \n");
		asb.append("     [OUT0]|[G27]            [G17]|[PWM (CO2)]\n");
		asb.append("     [OUT1]|[G14]            [G16]|           \n");
		asb.append("     [OUT2]|[G12]            [G4 ]|           \n");
		asb.append("           |[GND]            [G0 ]|           \n");
		asb.append("     [OUT3]|[G13]            [G2 ]|           \n");
		asb.append("           |[G10]            [G15]|           \n");
		asb.append("           |[G11]            [G8 ]|           \n");
		asb.append("           |[CMD]            [G7 ]|           \n");
		asb.append("           |[5V ]            [G6 ]|           \n");
		asb.append("           +----------------------+           \n");
		asb.style(AttributedStyle.DEFAULT);
		terminal.printAbove(asb);
	}

	private Socket connect(final int udpPort) throws IOException {
		try (final DatagramSocket socket = new DatagramSocket(9999)) {
			socket.setBroadcast(true);
			socket.setSoTimeout(5_000);
			
			terminal.write("Waiting for connection" + (testMode ? " to test server" : ""));

			while (true) {
				try {
					final byte[] payload = testMode ? "CLI-TEST".getBytes(StandardCharsets.UTF_8) : "CLI-HELLO".getBytes(StandardCharsets.UTF_8);
					final DatagramPacket broadcast = new DatagramPacket(payload, payload.length, InetAddress.getByName("255.255.255.255"), udpPort);
					socket.send(broadcast);

					final byte[] buf = new byte[256];
					final DatagramPacket response = new DatagramPacket(buf, buf.length);
					socket.receive(response);

					final String responseString = new String(response.getData(), 0, response.getLength());
					final String port = responseString.substring(responseString.lastIndexOf("PORT=") + 5);
					final Socket result = new Socket(response.getAddress(), Integer.valueOf(port));

					return result;
				} catch (final SocketTimeoutException e) {
					terminal.write(".");
				}
			}
		}
	}


	public static void main(final String[] args) throws Exception {
		LoggerFactory.disableLogging();
		start = System.currentTimeMillis();
		
		boolean testMode = false;
		if (args.length > 0 && args[0].equalsIgnoreCase("--test")) {
			System.setProperty("mechrain.testmode", "true");
			testMode = true;
		}
		
		final MechRainTerminal terminal = new MechRainTerminal();
		MechRainCLI cli = new MechRainCLI(terminal, testMode);
		cli.call();
		System.exit(1);
	}
}
