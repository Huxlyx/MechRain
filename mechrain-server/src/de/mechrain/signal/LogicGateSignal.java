package de.mechrain.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

/**
 * A signal that combines other signals (by ID, resolved via the shared
 * {@link SignalRegistry}) using an {@code AND} or {@code OR} logic operator.
 */
public class LogicGateSignal extends AbstractSignal {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LogManager.getLogger(Logging.SIGNAL);

	public enum Operator {
		AND, OR;
	}

	private Operator operator;
	private List<Integer> childSignalIds;

	private transient SignalRegistry registry;

	/** Default constructor for de-serialization purposes. */
	public LogicGateSignal() {
		/* empty constructor for de-serialization */
	}

	public LogicGateSignal(final Operator operator, final List<Integer> childSignalIds) {
		this.operator = operator;
		this.childSignalIds = new ArrayList<>(childSignalIds);
	}

	/**
	 * Wires this signal with the live {@link SignalRegistry}, needed to resolve
	 * its child signals. Not persisted; must be called again after
	 * deserialization/restore.
	 *
	 * @param registry the signal registry to resolve child signals from
	 */
	public void setRegistry(final SignalRegistry registry) {
		this.registry = registry;
	}

	public Operator getOperator() {
		return operator;
	}

	public void setOperator(final Operator operator) {
		this.operator = operator;
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return Collections.unmodifiableList(childSignalIds);
	}

	public void setChildSignalIds(final List<Integer> childSignalIds) {
		this.childSignalIds = new ArrayList<>(childSignalIds);
	}

	@Override
	public boolean isActive() {
		if (registry == null) {
			LOG.warn(() -> "LogicGateSignal " + getId() + " has no SignalRegistry wired, treating as inactive");
			return false;
		}
		if (childSignalIds == null || childSignalIds.isEmpty()) {
			return false;
		}
		final boolean and = operator == Operator.AND;
		for (final Integer childId : childSignalIds) {
			final boolean childActive = registry.getSignal(childId).map(ISignal::isActive).orElse(false);
			if (and && !childActive) {
				return false;
			}
			if (!and && childActive) {
				return true;
			}
		}
		return and;
	}

	@Override
	public String getSignalType() {
		return "Logic Gate";
	}

	@Override
	public String getSignalDescription() {
		return operator + "(" + String.join(", ", childSignalIds.stream().map(String::valueOf).toList()) + ")";
	}

	@Override
	public String toString() {
		return "LogicGateSignal id:" + getId() + " " + getSignalDescription();
	}
}
