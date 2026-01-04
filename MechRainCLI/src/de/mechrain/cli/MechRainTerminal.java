package de.mechrain.cli;

import static org.jline.builtins.Completers.TreeCompleter.node;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

public class MechRainTerminal {
	
	enum Mode {
		GENERAL,
		DEVICE;
	}
	
	public static final String CLEAR = "clear";
	public static final String CONFIG = "config";
	public static final String DUMP = "dump";
	public static final String FILTER = "filter";
	public static final String RECONNECT = "reconnect";
	public static final String SHOW = "show";
	public static final String SET = "set";
	
	private boolean interactive = false;
	private Lock lock = new ReentrantLock();
	private Condition interactiveMode = lock.newCondition();

	private final Completer generalCompleter = new TreeCompleter(
			node(CLEAR,
					node("buffer")),
			node(CONFIG,
					node("device")),
			node(DUMP),
			node(FILTER,
					node("logName"),
					node("text"),
					node("off")),
			node(RECONNECT),
			node(SHOW,
					node("buffer"),
					node("devices"),
					node("diagram")),
			node(SET,
					node("level", 
							node("off", "err", "warn", "info", "debug", "trace")),
					node("time", 
							node("off", "on")),
					node("logName", 
							node("off", "on"))),
			node("switch")
		);

	private final Completer deviceCompleter = new TreeCompleter(
			node("add",
					node("sink"),
					node("task")),
			node("exit"),
			node("reset"),
			node("remove",
					node("sink"),
					node("task"),
					node("device")),
			node("set",
					node("id"),
					node("description"),
					node("led",
							node("count"),
							node("rgb"),
							node("mode")))		
			);
	
	private final Terminal terminal;
	private final LineReader generalReader;
	private final LineReader deviceReader;
	
	private LineReader activeReader;
	private Mode mode = Mode.GENERAL;
	
	public MechRainTerminal() throws IOException {
		AnsiConsole.systemInstall();
		this.terminal = TerminalBuilder.builder()
				.system(true).provider("jni")
				.build();
		this.generalReader = LineReaderBuilder.builder()
				.terminal(terminal)
				.completer(generalCompleter)
				.build();
		this.generalReader.setVariable(LineReader.HISTORY_FILE, Paths.get("general.hist"));
		this.generalReader.setVariable(LineReader.HISTORY_FILE_SIZE, 1000);
		this.deviceReader = LineReaderBuilder.builder()
				.terminal(terminal)
				.completer(deviceCompleter)
				.build();
		this.deviceReader.setVariable(LineReader.HISTORY_FILE, Paths.get("device.hist"));
		this.deviceReader.setVariable(LineReader.HISTORY_FILE_SIZE, 1000);
		
		this.activeReader = generalReader;
	}

	public void printHeader() {
		terminal.writer().println();
		terminal.writer().println();
		terminal.puts(Capability.set_a_foreground, 4);
		terminal.writer().println(" ░███     ░███                       ░██        ░█████████             ░██           ");
		terminal.writer().println(" ░████   ░████                       ░██        ░██     ░██                          ");
		terminal.writer().println(" ░██░██ ░██░██  ░███████   ░███████  ░████████  ░██     ░██  ░██████   ░██░████████  ");
		terminal.writer().println(" ░██ ░████ ░██ ░██    ░██ ░██    ░██ ░██    ░██ ░█████████        ░██  ░██░██    ░██ ");
		terminal.writer().println(" ░██  ░██  ░██ ░█████████ ░██        ░██    ░██ ░██   ░██    ░███████  ░██░██    ░██ ");
		terminal.writer().println(" ░██       ░██ ░██        ░██    ░██ ░██    ░██ ░██    ░██  ░██   ░██  ░██░██    ░██ ");
		terminal.writer().println(" ░██       ░██  ░███████   ░███████  ░██    ░██ ░██     ░██  ░█████░██ ░██░██    ░██ ");
		terminal.writer().println();
		terminal.writer().println();
	    terminal.puts(Capability.orig_pair);
	}

	public void clear() {
		terminal.puts(Capability.clear_screen);
		terminal.puts(Capability.clr_eol);
		terminal.puts(Capability.cursor_home);
		
		terminal.writer().println("cleared");
		terminal.flush();
	}
	
	public void switchReader() {
		if (activeReader == generalReader) {
			activeReader = deviceReader;
			mode = Mode.DEVICE;
		} else {
			activeReader = generalReader;
			mode = Mode.GENERAL;
		}
	}
	
	public void setInteractive(final boolean interactive) {
		if (this.interactive && ! interactive) {
			printInfo("Switched to non-interactive");
			try {
				lock.lock();
				interactiveMode.signal();
			} catch (final Exception e) {
				e.printStackTrace();
			} finally {
				lock.unlock();
			}
		}
		this.interactive = interactive;
	}
	
	public void maybeWaitForNonInteractive() throws InterruptedException {
		try {
			lock.lock();
			while (interactive) {
				interactiveMode.await();
			}
		} finally {
			lock.unlock();
		}
	}
	
	public String readLine(final String prompt) {
		return activeReader.readLine(prompt);
	}
	
	public void printError(final String error) {
		printAbove(AttributedStyle.BOLD, AttributedStyle.RED, error);
	}
	
	public void printWarning(final String warning) {
		printAbove(AttributedStyle.BOLD, AttributedStyle.YELLOW, warning);
	}
	
	public void printInfo(final String info) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.GREEN, info);
	}
	
	public void printDebug(final String debug) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.CYAN, debug);
	}
	
	public void printTrace(final String trace) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.BLUE, trace);
	}
	
	private void printAbove(final AttributedStyle style, final int color, final String text) {
		final AttributedStringBuilder asb = new AttributedStringBuilder();
		asb.style(style.foreground(color));
		asb.append(text);
		asb.style(AttributedStyle.DEFAULT);
		activeReader.printAbove(asb.toAnsi(terminal));
	}
	
	public void printAbove(final AttributedStringBuilder asb) {
		activeReader.printAbove(asb.toAnsi(terminal));
	}
	
	public void write(final String msg) {
		terminal.writer().write(msg);
	}

	public Mode getMode() {
		return mode;
	}
}
