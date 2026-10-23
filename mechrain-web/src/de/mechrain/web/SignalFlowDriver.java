package de.mechrain.web;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mechrain.common.beans.AddSignalRequest;
import de.mechrain.common.beans.ConsoleRequest;
import de.mechrain.common.beans.ConsoleResponse;
import de.mechrain.common.beans.DeviceListResponse.DeviceData;
import de.mechrain.common.beans.DeviceListResponse.DeviceData.TaskData;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.SignalListRequest;
import de.mechrain.common.beans.SignalListResponse;
import de.mechrain.common.beans.SignalListResponse.SignalData;
import de.mechrain.common.beans.SwitchToNonInteractiveRequest;

/**
 * Drives the server's interactive signal-creation flow ({@code AddSignalRequest} +
 * {@code ConsoleRequest}/{@code ConsoleResponse} exchange) automatically.
 *
 * <p>The whole flow runs in one exclusive (single-flight) block: the server's main loop is
 * inside {@code addSignal()} and only understands {@code ConsoleResponse} messages until it
 * sends {@code SwitchToNonInteractiveRequest}. If an unexpected prompt appears, the driver
 * answers with "ABORT" so the server aborts cleanly, then reports the error.
 *
 * <p>After the flow the driver re-polls the signal list to find the newly created signal
 * (the server does not send a dedicated success response).
 */
public final class SignalFlowDriver {

	private static final long STEP_TIMEOUT_MS = 10_000;
	private static final long REPOLL_TIMEOUT_MS = 5_000;

	private final MechrainClient client;
	private final WebState state;

	public SignalFlowDriver(final MechrainClient client, final WebState state) {
		this.client = client;
		this.state = state;
	}

	/** Thrown when signal creation fails (validation, unexpected prompt, or no new signal found). */
	public static final class SignalFlowException extends RuntimeException {
		public SignalFlowException(final String message) {
			super(message);
		}

		public SignalFlowException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Creates a signal by driving the interactive server flow.
	 *
	 * @param spec the signal specification from the UI
	 * @return the newly created signal
	 * @throws SignalFlowException on validation errors, unexpected prompts, or if the server did not create the signal
	 */
	public SignalData createSignal(final SignalSpec spec) {
		validate(spec);
		final Set<Integer> knownIds = new HashSet<>();
		for (final SignalData s : state.getSignals()) {
			knownIds.add(s.getId());
		}
		try {
			client.runExclusive(io -> {
				io.send(AddSignalRequest.INSTANCE);
				while (true) {
					final ICliBean m = io.receive(STEP_TIMEOUT_MS);
					if (m instanceof ConsoleRequest cr) {
						final String answer = answerFor(cr.getRequest(), spec);
						if (answer == null) {
							// Unknown prompt: make the server abort cleanly, then report.
							io.send(consoleResponse("ABORT"));
							io.receive(STEP_TIMEOUT_MS);
							throw new SignalFlowException("Unerwarteter Prompt vom Server: " + cr.getRequest());
						}
						io.send(consoleResponse(answer));
					} else if (m instanceof SwitchToNonInteractiveRequest) {
						break;
					}
					// other messages: ignore
				}
			});
		} catch (final SignalFlowException e) {
			throw e;
		} catch (final Exception e) {
			throw new SignalFlowException("Anlegen fehlgeschlagen: " + e.getMessage(), e);
		}
		final SignalListResponse slr = client.sendAndAwait(SignalListRequest.INSTANCE, SignalListResponse.class, REPOLL_TIMEOUT_MS);
		state.setSignals(slr.getSignalList());
		for (final SignalData s : slr.getSignalList()) {
			if (!knownIds.contains(s.getId())) {
				return s;
			}
		}
		throw new SignalFlowException("Anlegen fehlgeschlagen – neues Signal nicht gefunden, Server-Log prüfen");
	}

	/* ---------------- Validation ---------------- */

	private void validate(final SignalSpec spec) {
		if (spec.type == null) {
			throw new SignalFlowException("Signal-Typ fehlt");
		}
		switch (spec.type) {
			case "threshold": {
				final DeviceData d = requireDevice(spec.targetDeviceId);
				requireMeasurement(d, spec.measurement);
				if (spec.comparator == null) {
					throw new SignalFlowException("Vergleichsoperator fehlt");
				}
				if (spec.threshold == null) {
					throw new SignalFlowException("Schwellwert fehlt");
				}
				if (spec.offThreshold != null) {
					final boolean above = "GT".equals(spec.comparator) || "GTE".equals(spec.comparator);
					if (above && spec.offThreshold >= spec.threshold) {
						throw new SignalFlowException("Off-Schwellwert muss unter dem Schwellwert liegen");
					}
					if (!above && spec.offThreshold <= spec.threshold) {
						throw new SignalFlowException("Off-Schwellwert muss über dem Schwellwert liegen");
					}
				}
				if (spec.stableForMinutes != null && spec.stableForMinutes <= 0) {
					throw new SignalFlowException("Stabilitätsfenster muss positiv sein");
				}
				break;
			}
			case "gate": {
				if (!"AND".equals(spec.operator) && !"OR".equals(spec.operator)) {
					throw new SignalFlowException("Operator muss AND oder OR sein");
				}
				if (spec.childSignalIds == null || spec.childSignalIds.isEmpty()) {
					throw new SignalFlowException("Mindestens ein Kindsignal erforderlich");
				}
				for (final Integer id : spec.childSignalIds) {
					if (id == null || !signalExists(id)) {
						throw new SignalFlowException("Signal " + id + " nicht gefunden");
					}
				}
				break;
			}
			case "inverter": {
				if (spec.childSignalId == null) {
					throw new SignalFlowException("Signal fehlt");
				}
				if (!signalExists(spec.childSignalId)) {
					throw new SignalFlowException("Signal " + spec.childSignalId + " nicht gefunden");
				}
				break;
			}
			case "staleness": {
				final DeviceData d = requireDevice(spec.targetDeviceId);
				if (spec.measurement != null) {
					requireMeasurement(d, spec.measurement);
				}
				if (spec.timeoutMinutes == null || spec.timeoutMinutes <= 0) {
					throw new SignalFlowException("Timeout muss positiv sein");
				}
				break;
			}
			case "timewindow": {
				if (!isValidHhMm(spec.start) || !isValidHhMm(spec.end)) {
					throw new SignalFlowException("Ungültige Zeit (Format HH:MM)");
				}
				if (spec.start.equals(spec.end)) {
					throw new SignalFlowException("Start und Ende dürfen nicht gleich sein");
				}
				if (spec.days == null || spec.days.isEmpty()) {
					throw new SignalFlowException("Mindestens einen Wochentag wählen");
				}
				break;
			}
			case "manual":
				/* no parameters required */
				break;
			default:
				throw new SignalFlowException("Unbekannter Signal-Typ: " + spec.type);
		}
	}

	private DeviceData requireDevice(final Integer deviceId) {
		if (deviceId == null) {
			throw new SignalFlowException("Gerät fehlt");
		}
		for (final DeviceData d : state.getDevices()) {
			if (d.getId() == deviceId) {
				return d;
			}
		}
		throw new SignalFlowException("Gerät " + deviceId + " nicht gefunden");
	}

	private void requireMeasurement(final DeviceData d, final String measurement) {
		if (measurement == null) {
			throw new SignalFlowException("Messgröße fehlt");
		}
		for (final TaskData t : d.getTasks().values()) {
			if (measurement.equals(t.getMeasurement())) {
				return;
			}
		}
		throw new SignalFlowException("Gerät " + d.getId() + " bietet " + measurement + " nicht an");
	}

	private boolean signalExists(final int id) {
		for (final SignalData s : state.getSignals()) {
			if (s.getId() == id) {
				return true;
			}
		}
		return false;
	}

	private static boolean isValidHhMm(final String s) {
		if (s == null) {
			return false;
		}
		final String[] p = s.trim().split(":");
		if (p.length != 2) {
			return false;
		}
		try {
			final int h = Integer.parseInt(p[0]);
			final int m = Integer.parseInt(p[1]);
			return h >= 0 && h <= 23 && m >= 0 && m <= 59;
		} catch (final NumberFormatException e) {
			return false;
		}
	}

	private static ConsoleResponse consoleResponse(final String response) {
		final ConsoleResponse cr = new ConsoleResponse();
		cr.setResponse(response);
		return cr;
	}

	/* ---------------- Prompt answering ---------------- */

	private static String answerFor(final String prompt, final SignalSpec spec) {
		if (prompt == null) {
			return null;
		}
		if (prompt.startsWith("Signal type")) {
			switch (spec.type) {
				case "threshold": return "threshold";
				case "timewindow": return "time";
				case "gate": return "gate";
				case "inverter": return "invert";
				case "staleness": return "stale";
				case "manual": return "manual";
				default: return null;
			}
		}
		if (prompt.startsWith("Start time")) {
			return spec.start;
		}
		if (prompt.startsWith("End time")) {
			return spec.end;
		}
		if (prompt.startsWith("Days of week")) {
			return daysToString(spec.days);
		}
		if (prompt.startsWith("Target device ID (measurement source)") || prompt.startsWith("Target device ID (data source)")) {
			return spec.targetDeviceId == null ? null : String.valueOf(spec.targetDeviceId);
		}
		if (prompt.startsWith("Measurement to observe")) {
			return spec.measurement;
		}
		if (prompt.startsWith("Measurement")) {
			return spec.measurement;
		}
		if (prompt.startsWith("Comparator")) {
			switch (spec.comparator) {
				case "GT": return ">";
				case "GTE": return ">=";
				case "LT": return "<";
				case "LTE": return "<=";
				default: return null;
			}
		}
		if (prompt.startsWith("Threshold value")) {
			return spec.threshold == null ? null : String.valueOf(spec.threshold);
		}
		if (prompt.startsWith("Off-threshold")) {
			return spec.offThreshold == null ? "" : String.valueOf(spec.offThreshold);
		}
		if (prompt.startsWith("Stability window")) {
			return spec.stableForMinutes == null ? "" : String.valueOf(spec.stableForMinutes);
		}
		if (prompt.startsWith("Operator")) {
			return spec.operator;
		}
		if (prompt.startsWith("Child signal IDs")) {
			if (spec.childSignalIds == null || spec.childSignalIds.isEmpty()) {
				return null;
			}
			final List<String> ids = new ArrayList<>();
			for (final Integer id : spec.childSignalIds) {
				ids.add(String.valueOf(id));
			}
			return String.join(",", ids);
		}
		if (prompt.startsWith("Child signal ID")) {
			return spec.childSignalId == null ? null : String.valueOf(spec.childSignalId);
		}
		if (prompt.startsWith("Timeout in minutes")) {
			return spec.timeoutMinutes == null ? null : String.valueOf(spec.timeoutMinutes);
		}
		return null;
	}

	private static String daysToString(final List<Integer> days) {
		if (days == null || days.isEmpty()) {
			return "";
		}
		final List<String> names = new ArrayList<>();
		for (final Integer d : days) {
			if (d == null || d < 1 || d > 7) {
				continue;
			}
			names.add(DayOfWeek.of(d).name());
		}
		return String.join(",", names);
	}
}
