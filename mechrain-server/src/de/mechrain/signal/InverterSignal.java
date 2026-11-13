package de.mechrain.signal;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

/**
 * A signal that is the logical negation of a single child signal: active while
 * the child is inactive and vice versa. Completes the boolean algebra together
 * with {@link LogicGateSignal} (AND + OR + NOT is functionally complete).
 *
 * <p>Fail-safe behaviour, consistent with {@link LogicGateSignal}: if no registry
 * is wired or the child signal has been removed, this signal reports inactive
 * rather than blindly inverting an unknown state.
 */
public class InverterSignal extends AbstractSignal {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LogManager.getLogger(Logging.SIGNAL);

	private int childSignalId;

	private transient SignalRegistry registry;

	/** Default constructor for de-serialization purposes. */
	public InverterSignal() {
		/* empty constructor for de-serialization */
	}

	public InverterSignal(final int childSignalId) {
		this.childSignalId = childSignalId;
	}

	/**
	 * Wires this signal with the live {@link SignalRegistry}, needed to resolve
	 * its child signal. Not persisted; must be called again after
	 * deserialization/restore.
	 *
	 * @param registry the signal registry to resolve the child from
	 */
	public void setRegistry(final SignalRegistry registry) {
		this.registry = registry;
	}

	public int getChildSignalId() {
		return childSignalId;
	}

	public void setChildSignalId(final int childSignalId) {
		this.childSignalId = childSignalId;
	}

	@Override
	public boolean isActive() {
		if (registry == null) {
			LOG.warn(() -> "InverterSignal " + getId() + " has no SignalRegistry wired, treating as inactive");
			return false;
		}
		final ISignal child = registry.getSignal(childSignalId).orElse(null);
		if (child == null) {
			LOG.warn(() -> "InverterSignal " + getId() + " references missing signal " + childSignalId + ", treating as inactive");
			return false;
		}
		return !child.isActive();
	}

	@Override
	public String getSignalType() {
		return "Inverter";
	}

	@Override
	public String getSignalDescription() {
		return "NOT(" + childSignalId + ")";
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return Collections.singletonList(childSignalId);
	}

	@Override
	public String toString() {
		return "InverterSignal id:" + getId() + " " + getSignalDescription();
	}
}
