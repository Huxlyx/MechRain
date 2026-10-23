package de.mechrain.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.mechrain.common.beans.DeviceConfigRequest;
import de.mechrain.common.beans.DeviceConfigResponse;
import de.mechrain.common.beans.DeviceListRequest;
import de.mechrain.common.beans.DeviceListResponse;
import de.mechrain.common.beans.DeviceListResponse.DeviceData;
import de.mechrain.common.beans.EndConfigureDeviceRequest;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.RemoveSignalRequest;
import de.mechrain.common.beans.SetNameRequest;
import de.mechrain.common.beans.SetDeviceSinkSignalRequest;
import de.mechrain.common.beans.SetDeviceTaskSignalRequest;
import de.mechrain.common.beans.SetSignalResponse;
import de.mechrain.common.beans.SetSignalStateRequest;
import de.mechrain.common.beans.SignalListRequest;
import de.mechrain.common.beans.SignalListResponse;
import de.mechrain.common.beans.SignalListResponse.SignalData;
import de.mechrain.common.beans.SwitchToNonInteractiveRequest;

/**
 * HTTP API of the web module (JDK built-in HttpServer, no extra dependencies):
 *
 * <ul>
 *   <li>{@code GET /api/state} – connection status + device/signal snapshot</li>
 *   <li>{@code GET /api/logs} – recent log entries</li>
 *   <li>{@code POST /api/signals} – create a signal (drives the interactive server flow)</li>
 *   <li>{@code DELETE /api/signals/{id}} – remove a signal (re-polls for confirmation)</li>
 *   <li>{@code PATCH /api/signals/{id}/state} – set the state of a manual signal</li>
 *   <li>{@code PATCH /api/sinks/{deviceId}/{sinkId}/signal} – set/clear a sink gate</li>
 *   <li>{@code PATCH /api/tasks/{deviceId}/{taskId}/signal} – set/clear a task gate</li>
 *   <li>{@code PATCH /api/devices/{deviceId}/name} – set/clear the device name</li>
 *   <li>static files from the classpath ({@code /web/...})</li>
 * </ul>
 */
public final class HttpApi {

	private static final int REQUEST_TIMEOUT_MS = 15_000;

	private final int port;
	private final WebState state;
	private final MechrainClient client;
	private final SignalFlowDriver driver;
	private final Gson gson;
	private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
		final Thread t = new Thread(r, "HttpApi");
		t.setDaemon(true);
		return t;
	});
	private HttpServer server;

	public HttpApi(final int port, final WebState state, final MechrainClient client, final SignalFlowDriver driver) {
		this.port = port;
		this.state = state;
		this.client = client;
		this.driver = driver;
		this.gson = new Gson();
	}

	public void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", this::handle);
		server.setExecutor(executor);
		server.start();
		System.out.println("[mechrain-web] HTTP-Server laeuft auf http://localhost:" + port);
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
		}
		executor.shutdownNow();
	}

	/* ---------------- Dispatch ---------------- */

	private void handle(final HttpExchange exchange) {
		try {
			final String method = exchange.getRequestMethod();
			final String path = exchange.getRequestURI().getPath();
			if (path.startsWith("/api/")) {
				handleApi(method, path, exchange);
			} else {
				handleStatic(path, exchange);
			}
		} catch (final Exception e) {
			// last-resort error (should not happen, handlers catch their own errors)
			try {
				sendJson(exchange, 500, error("Unerwarteter Fehler: " + e.getMessage()));
			} catch (final IOException ignored) {
				// client went away
			}
		}
		exchange.close();
	}

	private void handleApi(final String method, final String path, final HttpExchange exchange) throws Exception {
		if ("GET".equals(method) && path.equals("/api/state")) {
			sendJson(exchange, 200, stateJson());
			return;
		}
		if ("GET".equals(method) && path.equals("/api/logs")) {
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("logs", client.getLogs());
			sendJson(exchange, 200, body);
			return;
		}
		if (state.isReadOnly() && !"GET".equals(method)) {
			sendJson(exchange, 501, error("Nur-Lese-Modus: Änderungen sind deaktiviert (--read-only)"));
			return;
		}
		if ("POST".equals(method) && path.equals("/api/signals")) {
			handleCreateSignal(exchange);
			return;
		}
		if ("DELETE".equals(method) && path.startsWith("/api/signals/")) {
			handleDeleteSignal(path.substring("/api/signals/".length()), exchange);
			return;
		}
		if ("PATCH".equals(method) && path.matches("/api/signals/\\d+/state")) {
			final int id = Integer.parseInt(path.split("/")[3]);
			final Boolean active = bodyActive(exchange);
			if (active == null) {
				sendJson(exchange, 400, error("Feld \"active\" fehlt oder ist kein Boolescher Wert"));
			} else {
				handleSetSignalState(id, active, exchange);
			}
			return;
		}
		if ("PATCH".equals(method) && path.matches("/api/sinks/\\d+/\\d+/signal")) {
			final String[] parts = path.split("/");
			final int deviceId = Integer.parseInt(parts[3]);
			final int sinkId = Integer.parseInt(parts[4]);
			final Integer signalId = bodySignalId(exchange);
			if (precheckSetSignal(deviceId, sinkId, signalId, true, exchange)) {
				handleSetSignal(new SetDeviceSinkSignalRequest(deviceId, sinkId, signalId), exchange);
			}
			return;
		}
		if ("PATCH".equals(method) && path.matches("/api/tasks/\\d+/\\d+/signal")) {
			final String[] parts = path.split("/");
			final int deviceId = Integer.parseInt(parts[3]);
			final int taskId = Integer.parseInt(parts[4]);
			final Integer signalId = bodySignalId(exchange);
			if (precheckSetSignal(deviceId, taskId, signalId, false, exchange)) {
				handleSetSignal(new SetDeviceTaskSignalRequest(deviceId, taskId, signalId), exchange);
			}
			return;
		}
		if ("PATCH".equals(method) && path.matches("/api/devices/\\d+/name")) {
			final int deviceId = Integer.parseInt(path.split("/")[3]);
			final String name = bodyName(exchange);
			if (name == null) {
				sendJson(exchange, 400, error("Feld \"name\" fehlt"));
			} else if (findDevice(deviceId) == null) {
				sendJson(exchange, 404, error("Gerät " + deviceId + " nicht gefunden"));
			} else {
				handleSetName(deviceId, name, exchange);
			}
			return;
		}
		sendJson(exchange, 404, error("Unbekannter Endpunkt: " + method + " " + path));
	}

	/* ---------------- Handlers ---------------- */

	private void handleCreateSignal(final HttpExchange exchange) throws Exception {
		final SignalSpec spec;
		try {
			spec = gson.fromJson(readBody(exchange), SignalSpec.class);
		} catch (final JsonSyntaxException e) {
			sendJson(exchange, 400, error("Ungültiges JSON: " + e.getMessage()));
			return;
		}
		if (spec == null) {
			sendJson(exchange, 400, error("Leerer Body"));
			return;
		}
		try {
			final SignalData sig = driver.createSignal(spec);
			sendJson(exchange, 201, sig);
		} catch (final SignalFlowDriver.SignalFlowException e) {
			sendJson(exchange, 400, error(e.getMessage()));
		} catch (final MechrainClient.ConnectionLostException e) {
			sendJson(exchange, 503, error(e.getMessage()));
		} catch (final MechrainClient.RequestTimeoutException e) {
			sendJson(exchange, 504, error(e.getMessage()));
		}
	}

	private void handleDeleteSignal(final String idStr, final HttpExchange exchange) throws Exception {
		final int id;
		try {
			id = Integer.parseInt(idStr);
		} catch (final NumberFormatException e) {
			sendJson(exchange, 400, error("Ungültige Signal-ID: " + idStr));
			return;
		}
		final SignalData sig = findSignal(id);
		if (sig == null) {
			sendJson(exchange, 404, error("Signal " + id + " nicht gefunden"));
			return;
		}
		if (sig.getUsedBy() != null && !sig.getUsedBy().isEmpty()) {
			sendJson(exchange, 409, error("Signal wird verwendet von: " + String.join(", ", sig.getUsedBy())));
			return;
		}
		try {
			// The server processes RemoveSignalRequest synchronously in its main loop;
			// a re-poll immediately after observes the effect.
			client.sendOnly(new RemoveSignalRequest(id));
			final SignalListResponse slr = client.sendAndAwait(SignalListRequest.INSTANCE, SignalListResponse.class, 5_000);
			for (final SignalData s : slr.getSignalList()) {
				if (s.getId() == id) {
					final String usages = s.getUsedBy() != null && !s.getUsedBy().isEmpty()
							? "Signal wird verwendet von: " + String.join(", ", s.getUsedBy())
							: "Löschen fehlgeschlagen, Server-Log prüfen";
					sendJson(exchange, 409, error(usages));
					return;
				}
			}
			state.setSignals(slr.getSignalList());
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("deleted", id);
			sendJson(exchange, 200, body);
		} catch (final MechrainClient.ConnectionLostException e) {
			sendJson(exchange, 503, error(e.getMessage()));
		} catch (final MechrainClient.RequestTimeoutException e) {
			sendJson(exchange, 504, error(e.getMessage()));
		}
	}

	/**
	 * Returns true if the referenced device, sink/task and signal all exist in the current
	 * state; otherwise sends a 404 response and returns false.
	 */
	private boolean precheckSetSignal(final int deviceId, final int elementId, final Integer signalId,
			final boolean isSink, final HttpExchange exchange) throws IOException {
		final DeviceData device = findDevice(deviceId);
		if (device == null) {
			sendJson(exchange, 404, error("Gerät " + deviceId + " nicht gefunden"));
			return false;
		}
		final Map<Integer, ?> elements = isSink ? device.getSinks() : device.getTasks();
		if (!elements.containsKey(elementId)) {
			sendJson(exchange, 404, error((isSink ? "Sink" : "Task") + " " + elementId
					+ " nicht gefunden auf Gerät " + deviceId));
			return false;
		}
		if (signalId != null && findSignal(signalId) == null) {
			sendJson(exchange, 404, error("Signal " + signalId + " nicht gefunden"));
			return false;
		}
		return true;
	}

	private void handleSetSignal(final ICliBean request, final HttpExchange exchange) throws Exception {
		try {
			final SetSignalResponse r = client.sendAndAwait(request, SetSignalResponse.class, REQUEST_TIMEOUT_MS);
			if (r.isSuccess()) {
				// Re-poll so the change is visible without waiting for the next state poll cycle
				final DeviceListResponse dlr = client.sendAndAwait(DeviceListRequest.INSTANCE, DeviceListResponse.class, 5_000);
				state.setDevices(dlr.getDeviceList());
			}
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("success", r.isSuccess());
			body.put("message", r.getMessage());
			sendJson(exchange, 200, body);
		} catch (final MechrainClient.ConnectionLostException e) {
			sendJson(exchange, 503, error(e.getMessage()));
		} catch (final MechrainClient.RequestTimeoutException e) {
			sendJson(exchange, 504, error(e.getMessage()));
		}
	}

	/**
	 * Sets the state of a manually controlled signal. The server only accepts this for manual
	 * signals; after a successful response the signal list is re-polled so the new state is
	 * visible without waiting for the next state poll cycle.
	 */
	private void handleSetSignalState(final int id, final boolean active, final HttpExchange exchange) throws Exception {
		final SignalData sig = findSignal(id);
		if (sig == null) {
			sendJson(exchange, 404, error("Signal " + id + " nicht gefunden"));
			return;
		}
		if (!"Manual".equals(sig.getType())) {
			sendJson(exchange, 400, error("Nur manuelle Signale können gesetzt werden (Typ: " + sig.getType() + ")"));
			return;
		}
		try {
			final SetSignalResponse r = client.sendAndAwait(
					new SetSignalStateRequest(id, active), SetSignalResponse.class, REQUEST_TIMEOUT_MS);
			if (!r.isSuccess()) {
				sendJson(exchange, 409, error(r.getMessage()));
				return;
			}
			final SignalListResponse slr = client.sendAndAwait(SignalListRequest.INSTANCE, SignalListResponse.class, 5_000);
			state.setSignals(slr.getSignalList());
			boolean newState = active;
			for (final SignalData s : slr.getSignalList()) {
				if (s.getId() == id) {
					newState = s.isActive();
					break;
				}
			}
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("success", true);
			body.put("active", newState);
			sendJson(exchange, 200, body);
		} catch (final MechrainClient.ConnectionLostException e) {
			sendJson(exchange, 503, error(e.getMessage()));
		} catch (final MechrainClient.RequestTimeoutException e) {
			sendJson(exchange, 504, error(e.getMessage()));
		}
	}

	/**
	 * Renames (or clears the name of) a device. The server only processes
	 * {@link SetNameRequest} inside the device-config subflow (same as the CLI), so this
	 * driver enters the subflow, sends the request, and leaves the subflow again - all
	 * under the client's single-flight lock so no other request can interleave.
	 */
	private void handleSetName(final int deviceId, final String name, final HttpExchange exchange) throws Exception {
		final String effective = name.isEmpty() ? null : name;
		try {
			client.runExclusive(io -> {
				boolean subflowOpen = false;
				try {
					final DeviceConfigRequest dcr = new DeviceConfigRequest();
					dcr.setDeviceId(deviceId);
					io.send(dcr);
					final ICliBean resp = io.receive(5_000);
					if (!(resp instanceof DeviceConfigResponse)) {
						throw new MechrainClient.ProtocolException("Unerwartete Antwort: " + (resp == null ? "null" : resp.getClass().getSimpleName()));
					}
					subflowOpen = true;
					io.send(new SetNameRequest(effective));
					io.send(EndConfigureDeviceRequest.INSTANCE);
					final ICliBean end = io.receive(5_000);
					if (!(end instanceof SwitchToNonInteractiveRequest)) {
						throw new MechrainClient.ProtocolException("Unerwartete Antwort: " + (end == null ? "null" : end.getClass().getSimpleName()));
					}
					subflowOpen = false;
				} finally {
					if (subflowOpen) {
						// Best effort: make sure the server leaves the subflow even if something failed
						try {
							io.send(EndConfigureDeviceRequest.INSTANCE);
							io.receive(2_000);
						} catch (final Exception ignored) {
							// connection is probably gone; the server will clean up on disconnect
						}
					}
				}
			});
			final DeviceListResponse dlr = client.sendAndAwait(DeviceListRequest.INSTANCE, DeviceListResponse.class, 5_000);
			state.setDevices(dlr.getDeviceList());
			for (final DeviceData d : dlr.getDeviceList()) {
				if (d.getId() == deviceId) {
					final boolean ok = effective == null ? d.getName() == null : effective.equals(d.getName());
					final Map<String, Object> body = new LinkedHashMap<>();
					body.put("success", ok);
					body.put("name", d.getName());
					sendJson(exchange, ok ? 200 : 409, body);
					return;
				}
			}
			sendJson(exchange, 409, error("Gerät " + deviceId + " nach Änderung nicht gefunden"));
		} catch (final MechrainClient.ConnectionLostException e) {
			sendJson(exchange, 503, error(e.getMessage()));
		} catch (final MechrainClient.RequestTimeoutException e) {
			sendJson(exchange, 504, error(e.getMessage()));
		} catch (final MechrainClient.ProtocolException e) {
			sendJson(exchange, 500, error(e.getMessage()));
		}
	}

	/* ---------------- Static files ---------------- */

	private void handleStatic(final String path, final HttpExchange exchange) throws IOException {
		final String rel = path.equals("/") ? "web/index.html" : "web" + path;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(rel)) {
			if (in == null) {
				sendJson(exchange, 404, error("Nicht gefunden: " + path));
				return;
			}
			final ByteArrayOutputStream buf = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			int n;
			while ((n = in.read(chunk)) > 0) {
				buf.write(chunk, 0, n);
			}
			final byte[] data = buf.toByteArray();
			exchange.getResponseHeaders().set("Content-Type", contentType(rel));
			exchange.sendResponseHeaders(200, data.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(data);
			}
		}
	}

	private static String contentType(final String path) {
		if (path.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (path.endsWith(".js")) {
			return "application/javascript; charset=utf-8";
		}
		if (path.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		return "application/octet-stream";
	}

	/* ---------------- Helpers ---------------- */

	private Map<String, Object> stateJson() {
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("connected", state.isConnected());
		body.put("reconnecting", state.isReconnecting());
		body.put("readOnly", state.isReadOnly());
		body.put("serverVersion", state.getServerVersion());
		body.put("protocolVersion", state.getProtocolVersion());
		body.put("lastUpdated", state.getLastUpdated());
		body.put("devices", state.getDevices());
		body.put("signals", state.getSignals());
		return body;
	}

	private SignalData findSignal(final int id) {
		for (final SignalData s : state.getSignals()) {
			if (s.getId() == id) {
				return s;
			}
		}
		return null;
	}

	private DeviceData findDevice(final int id) {
		for (final DeviceData d : state.getDevices()) {
			if (d.getId() == id) {
				return d;
			}
		}
		return null;
	}

	private String bodyName(final HttpExchange exchange) throws IOException {
		final String body = readBody(exchange);
		final Map<String, Object> map = gson.fromJson(body, Map.class);
		if (map == null || map.get("name") == null) {
			return null;
		}
		return String.valueOf(map.get("name"));
	}

	private Boolean bodyActive(final HttpExchange exchange) throws IOException {
		final String body = readBody(exchange);
		final Map<String, Object> map = gson.fromJson(body, Map.class);
		if (map == null || !(map.get("active") instanceof Boolean b)) {
			return null;
		}
		return b;
	}

	private Integer bodySignalId(final HttpExchange exchange) throws IOException {
		final String body = readBody(exchange);
		final Map<String, Object> map = gson.fromJson(body, Map.class);
		if (map == null || map.get("signalId") == null) {
			return null;
		}
		final Object v = map.get("signalId");
		if (v instanceof Number n) {
			return n.intValue();
		}
		return Integer.parseInt(String.valueOf(v));
	}

	private static String readBody(final HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			final ByteArrayOutputStream buf = new ByteArrayOutputStream();
			final byte[] chunk = new byte[4096];
			int n;
			while ((n = in.read(chunk)) > 0) {
				buf.write(chunk, 0, n);
			}
			return new String(buf.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, String> error(final String message) {
		final Map<String, String> m = new LinkedHashMap<>();
		m.put("error", message);
		return m;
	}

	private void sendJson(final HttpExchange exchange, final int status, final Object body) throws IOException {
		final byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, data.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(data);
		}
	}
}
