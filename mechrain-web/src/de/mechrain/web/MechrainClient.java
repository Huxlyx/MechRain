package de.mechrain.web;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.mechrain.common.MechRainFory;
import de.mechrain.common.ProtocolVersion;
import de.mechrain.common.beans.HandshakeRequest;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.LogEvent;
import de.mechrain.common.beans.ServerInfoResponse;

/**
 * Client for the Mechrain server protocol.
 *
 * <p>Connection lifecycle: UDP discovery (broadcast {@code CLI-HELLO}/{@code CLI-TEST} on port 5000),
 * TCP connect to the dynamic CLI port from the discovery reply, read until
 * {@link ServerInfoResponse} (discarding the replayed {@link LogEvent} burst), reject servers with
 * a protocol version below the configured minimum, then send {@link HandshakeRequest}.
 *
 * <p>All request/response operations are serialized through a single-flight lock: the server's CLI
 * main loop processes exactly one request at a time, so the client must not have more than one
 * unanswered request in flight. Interactive flows (signal creation) hold the lock for their entire
 * duration via {@link #runExclusive(ExclusiveOp)}.
 *
 * <p>On connection loss the client automatically re-discovers and reconnects with exponential
 * backoff; pending operations fail with {@link ConnectionLostException}.
 */
public final class MechrainClient implements AutoCloseable {

	/** Client connection state. */
	public enum State {
		/** First connection attempt in progress. */
		CONNECTING,
		/** Connected and handshake completed. */
		CONNECTED,
		/** Connection lost, automatic reconnect in progress. */
		RECONNECTING,
		/** Not connected and no reconnect scheduled (fatal error or closed). */
		DISCONNECTED
	}

	/** Thrown when the server connection is lost or not established. */
	public static final class ConnectionLostException extends RuntimeException {
		public ConnectionLostException(final String message) {
			super(message);
		}

		public ConnectionLostException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/** Thrown when the server does not answer within the given timeout. */
	public static final class RequestTimeoutException extends RuntimeException {
		public RequestTimeoutException(final String message) {
			super(message);
		}
	}

	/** Thrown when the server protocol version is too old (no retry). */
	public static final class ProtocolVersionException extends RuntimeException {
		public ProtocolVersionException(final String message) {
			super(message);
		}
	}

	/** Thrown when the server sends an unexpected message type. */
	public static final class ProtocolException extends RuntimeException {
		public ProtocolException(final String message) {
			super(message);
		}
	}

	/** I/O operations available inside an exclusive (single-flight) block. */
	public interface Io {
		/** Sends a bean over the connection. */
		void send(ICliBean bean) throws IOException;

		/** Waits for the next protocol message (LogEvents are filtered into the log buffer). */
		ICliBean receive(long timeoutMs) throws Exception;
	}

	/** An operation executed with exclusive access to the connection. */
	@FunctionalInterface
	public interface ExclusiveOp {
		void execute(Io io) throws Exception;
	}

	private static final int MAX_LOGS = 200;
	private static final long SOCKET_READ_TIMEOUT_MS = 15_000;
	private static final long DISCOVERY_TIMEOUT_MS = 5_000;
	private static final long INITIAL_BACKOFF_MS = 2_000;
	private static final long MAX_BACKOFF_MS = 30_000;

	private final int discoveryPort;
	private final boolean testMode;
	private final int minProtocolVersion;

	private final ReentrantLock ioLock = new ReentrantLock();
	private final AtomicReference<CompletableFuture<ICliBean>> pending = new AtomicReference<>();
	private final Object stateLock = new Object();
	private final Deque<LogEntry> logBuffer = new ArrayDeque<>();
	private final Object logLock = new Object();
	private final AtomicLong logSeq = new AtomicLong();

	private volatile State state = State.DISCONNECTED;
	private volatile Socket socket;
	private volatile DataOutputStream dos;
	private volatile String serverVersion;
	private volatile int protocolVersion;
	private volatile boolean closed;

	/**
	 * @param discoveryPort      UDP port the server's discovery service listens on (default 5000)
	 * @param testMode           use test-mode discovery payloads ({@code CLI-TEST} instead of {@code CLI-HELLO})
	 * @param minProtocolVersion lowest server protocol version accepted (4 for full
	 *                           functionality incl. gate-assignment shortcuts and device
	 *                           naming; 2 for read-only operation against older servers)
	 */
	public MechrainClient(final int discoveryPort, final boolean testMode, final int minProtocolVersion) {
		this.discoveryPort = discoveryPort;
		this.testMode = testMode;
		this.minProtocolVersion = minProtocolVersion;
	}

	/** Starts the connection loop (non-blocking). */
	public void start() {
		final Thread t = new Thread(this::connectLoop, "MechrainClient-Connect");
		t.setDaemon(true);
		t.start();
	}

	public State getState() {
		return state;
	}

	public String getServerVersion() {
		return serverVersion;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	/** Snapshot of the recent log events (oldest first, max {@value #MAX_LOGS}). */
	public List<LogEntry> getLogs() {
		synchronized (logLock) {
			return new ArrayList<>(logBuffer);
		}
	}

	/** Adds a client-side log entry (connection state changes etc.). */
	public void log(final String level, final String message) {
		log(level, message, null);
	}

	/** Adds a client-side log entry with explicit logger name. */
	public void log(final String level, final String message, final String logger) {
		final LogEntry entry = new LogEntry(logSeq.incrementAndGet(), System.currentTimeMillis(), level, message, logger);
		synchronized (logLock) {
			logBuffer.addLast(entry);
			while (logBuffer.size() > MAX_LOGS) {
				logBuffer.removeFirst();
			}
		}
	}

	private void addLogEvent(final LogEvent le) {
		log(levelName(le.getLevel()), le.getFormattedMessage(), le.getLoggerName());
	}

	/**
	 * Sends a request and waits for the expected response type. Blocks until the single-flight
	 * lock is free, then sends and waits for the response.
	 *
	 * @param request     the request bean
	 * @param expected    expected response type
	 * @param timeoutMs   maximum time to wait for the response
	 * @return the response
	 * @throws ConnectionLostException if the connection is lost
	 * @throws RequestTimeoutException if no response arrives in time
	 * @throws ProtocolException       if the response type does not match
	 */
	public <T extends ICliBean> T sendAndAwait(final ICliBean request, final Class<T> expected, final long timeoutMs) {
		final AtomicReference<T> result = new AtomicReference<>();
		try {
			runExclusive(io -> {
				io.send(request);
				final ICliBean b = io.receive(timeoutMs);
				if (!expected.isInstance(b)) {
					throw new ProtocolException("Unerwartete Antwort: " + (b == null ? "null" : b.getClass().getSimpleName()));
				}
				result.set(expected.cast(b));
			});
		} catch (final Exception e) {
			throw unwrap(e);
		}
		return result.get();
	}

	/**
	 * Sends a request that the server does not answer (e.g. {@code RemoveSignalRequest}).
	 * The server processes it synchronously in its main loop, so a subsequent request
	 * observes the effect.
	 */
	public void sendOnly(final ICliBean request) {
		try {
			runExclusive(io -> io.send(request));
		} catch (final Exception e) {
			throw unwrap(e);
		}
	}

	/**
	 * Executes an operation with exclusive access to the connection, i.e. no other client
	 * operation can interleave. Used for interactive flows (signal creation) where the server
	 * expects a strict request/response sequence.
	 */
	public void runExclusive(final ExclusiveOp op) throws Exception {
		ioLock.lock();
		try {
			if (state != State.CONNECTED) {
				throw new ConnectionLostException(state == State.DISCONNECTED
						? "Keine Server-Verbindung"
						: "Server-Verbindung wird hergestellt, bitte warten");
			}
			op.execute(new Io() {
				@Override
				public void send(final ICliBean bean) throws IOException {
					sendLocked(bean);
				}

				@Override
				public ICliBean receive(final long timeoutMs) throws Exception {
					return receiveLocked(timeoutMs);
				}
			});
		} finally {
			ioLock.unlock();
		}
	}

	@Override
	public void close() {
		closed = true;
		final Socket s = socket;
		if (s != null) {
			try {
				s.close();
			} catch (final IOException ignored) {
				// ignore
			}
		}
		synchronized (stateLock) {
			stateLock.notifyAll();
		}
	}

	/* ---------------- Connection loop ---------------- */

	private void connectLoop() {
		long backoff = INITIAL_BACKOFF_MS;
		while (!closed) {
			try {
				setState(state == State.RECONNECTING ? State.RECONNECTING : State.CONNECTING);
				connectOnce();
				backoff = INITIAL_BACKOFF_MS;
				// Wait until the connection drops again.
				synchronized (stateLock) {
					while (!closed && state == State.CONNECTED) {
						stateLock.wait(500);
					}
				}
				if (closed || state == State.DISCONNECTED) {
					return;
				}
			} catch (final ProtocolVersionException e) {
				log("ERROR", e.getMessage(), "MechrainClient");
				setState(State.DISCONNECTED);
				return;
			} catch (final Exception e) {
				log("WARN", "Verbindung fehlgeschlagen: " + e.getMessage(), "MechrainClient");
				setState(State.RECONNECTING);
			}
			if (sleepInterruptible(backoff)) {
				return;
			}
			backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
		}
	}

	private boolean sleepInterruptible(final long ms) {
		try {
			Thread.sleep(ms);
			return false;
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return true;
		}
	}

	private void setState(final State s) {
		synchronized (stateLock) {
			state = s;
			stateLock.notifyAll();
		}
	}

	private void connectOnce() throws IOException {
		final InetSocketAddress server = discover();
		log("INFO", "Server gefunden: " + server.getAddress().getHostAddress() + ":" + server.getPort(), "MechrainClient");

		final Socket s = new Socket();
		s.connect(server, 5_000);
		s.setTcpNoDelay(true);
		s.setKeepAlive(true);
		s.setSoTimeout((int) SOCKET_READ_TIMEOUT_MS);
		final DataInputStream dis = new DataInputStream(s.getInputStream());
		final DataOutputStream dosOut = new DataOutputStream(s.getOutputStream());

		// Read until ServerInfoResponse; the server replays stored LogEvents first.
		ServerInfoResponse info = null;
		while (true) {
			final ICliBean b;
			try {
				b = MechRainFory.receiveAndDeserialize(dis);
			} catch (final RuntimeException e) {
				s.close();
				throw new IOException("Ungültige Server-Antwort: " + e.getMessage(), e);
			}
			if (b instanceof ServerInfoResponse sir) {
				info = sir;
				break;
			}
			// LogEvent replay burst -> discard
		}
		if (info.getProtocolVersion() < minProtocolVersion) {
			s.close();
			throw new ProtocolVersionException("Server-Protokollversion " + info.getProtocolVersion()
					+ " ist zu alt, benötigt wird >= " + minProtocolVersion + " – bitte Mechrain-Server aktualisieren");
		}
		MechRainFory.serializeAndSend(new HandshakeRequest(ProtocolVersion.PROTOCOL_VERSION), dosOut);

		socket = s;
		dos = dosOut;
		serverVersion = info.getVersion();
		protocolVersion = info.getProtocolVersion();

		final Thread receiveThread = new Thread(() -> receiveLoop(dis), "MechrainClient-Receive");
		receiveThread.setDaemon(true);
		receiveThread.start();

		setState(State.CONNECTED);
		log("INFO", "Verbunden mit Mechrain-Server " + info.getVersion() + " (Protokoll v" + info.getProtocolVersion() + ")", "MechrainClient");
	}

	private void receiveLoop(final DataInputStream dis) {
		while (!closed) {
			final ICliBean b;
			try {
				b = MechRainFory.receiveAndDeserialize(dis);
			} catch (final SocketTimeoutException e) {
				continue; // idle, no data
			} catch (final RuntimeException e) {
				log("WARN", "Deserialisierungsfehler, Nachricht verworfen: " + e.getMessage(), "MechrainClient");
				continue;
			} catch (final IOException e) {
				onDisconnected();
				return;
			}
			if (b instanceof LogEvent le) {
				addLogEvent(le);
				continue;
			}
			final CompletableFuture<ICliBean> f = pending.getAndSet(null);
			if (f != null) {
				f.complete(b);
			}
			// else: unexpected message without waiting request -> ignore
		}
	}

	private void onDisconnected() {
		final CompletableFuture<ICliBean> f = pending.getAndSet(null);
		if (f != null) {
			f.completeExceptionally(new ConnectionLostException("Verbindung zum Server verloren"));
		}
		log("WARN", "Verbindung zum Server verloren, versuche neu zu verbinden …", "MechrainClient");
		setState(State.RECONNECTING);
	}

	/* ---------------- Single-flight I/O ---------------- */

	private void sendLocked(final ICliBean bean) throws IOException {
		final DataOutputStream out = dos;
		if (out == null) {
			throw new IOException("Keine Verbindung");
		}
		synchronized (out) {
			MechRainFory.serializeAndSend(bean, out);
		}
	}

	private ICliBean receiveLocked(final long timeoutMs) throws Exception {
		final CompletableFuture<ICliBean> f = new CompletableFuture<>();
		pending.set(f);
		try {
			return f.get(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (final TimeoutException e) {
			pending.set(null);
			throw new RequestTimeoutException("Keine Antwort vom Server innerhalb von " + timeoutMs + " ms");
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			pending.set(null);
			throw new ConnectionLostException("Warten auf Antwort unterbrochen");
		} catch (final ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof RuntimeException re) {
				throw re;
			}
			throw new ConnectionLostException("Verbindung zum Server verloren", cause);
		}
	}

	private static RuntimeException unwrap(final Exception e) {
		if (e instanceof RuntimeException re) {
			return re;
		}
		return new ConnectionLostException(e.getMessage(), e);
	}

	/* ---------------- UDP discovery ---------------- */

	private InetSocketAddress discover() throws IOException {
		final byte[] payload = (testMode ? "CLI-TEST" : "CLI-HELLO").getBytes(StandardCharsets.UTF_8);
		try (DatagramSocket udp = new DatagramSocket()) {
			udp.setBroadcast(true);
			udp.setSoTimeout((int) DISCOVERY_TIMEOUT_MS);
			while (!closed) {
				try {
					udp.send(new DatagramPacket(payload, payload.length, InetAddress.getByName("255.255.255.255"), discoveryPort));
					final byte[] buf = new byte[256];
					final DatagramPacket resp = new DatagramPacket(buf, buf.length);
					udp.receive(resp);
					final String str = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
					final int idx = str.lastIndexOf("PORT=");
					if (idx < 0) {
						throw new IOException("Ungültige Discovery-Antwort: " + str);
					}
					final int port = Integer.parseInt(str.substring(idx + 5).trim());
					return new InetSocketAddress(resp.getAddress(), port);
				} catch (final SocketTimeoutException e) {
					// no reply yet, retry
				}
			}
			throw new IOException("Abgebrochen");
		}
	}

	/* ---------------- Logging helpers ---------------- */

	private static String levelName(final int level) {
		// Log4j2 intLevels: OFF=0, FATAL=100, ERROR=200, WARN=300, INFO=400, DEBUG=500, TRACE=600
		switch (level) {
			case 100: return "FATAL";
			case 200: return "ERROR";
			case 300: return "WARN";
			case 400: return "INFO";
			case 500: return "DEBUG";
			case 600: return "TRACE";
			default: return "LEVEL" + level;
		}
	}
}
