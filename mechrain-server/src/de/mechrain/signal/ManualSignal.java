package de.mechrain.signal;

import java.util.List;

/**
 * A signal whose state is set manually (e.g. as a maintenance switch), controlled
 * via the CLI ({@code config signals set <id> on|off}) or the web UI. The state is
 * not persisted to the signal registry file: after a server restart the signal is
 * inactive (fail-safe default, consistent with other signals whose live state starts
 * fresh).
 */
public class ManualSignal extends AbstractSignal {

	private static final long serialVersionUID = 1L;

	/** Current state, {@code false} (inactive) by default. Volatile: written by the CLI thread, read by gate checks from task/sink threads. */
	private volatile boolean active;

	/** Default constructor for de-serialization purposes. */
	public ManualSignal() {
		/* empty constructor for de-serialization */
	}

	@Override
	public boolean isActive() {
		return active;
	}

	/**
	 * Sets the state of the signal.
	 *
	 * @param active the new state
	 */
	public void setActive(final boolean active) {
		this.active = active;
	}

	@Override
	public String getSignalType() {
		return "Manual";
	}

	@Override
	public String getSignalDescription() {
		return active ? "manually on" : "manually off";
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return null;
	}

	@Override
	public String toString() {
		return "ManualSignal id:" + getId() + (active ? " [ON]" : " [OFF]");
	}
}
