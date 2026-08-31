package de.mechrain.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

public class SignalRegistry {
	
	private static final Logger LOG = LogManager.getLogger(Logging.SIGNAL_REGISTRY);
	
	final List<ISignal> signalList = Collections.synchronizedList(new ArrayList<>());

	public SignalRegistry() {
		/* empty constructor for de-serialization */
	}

	/**
	 * Adds the given signal to the registry, assigning it the next free ID.
	 *
	 * @param signal the signal to add
	 * @return the assigned ID
	 */
	public int addSignal(final ISignal signal) {
		synchronized (signalList) {
			final int nextId = signalList.stream().mapToInt(ISignal::getId).max().orElse(0) + 1;
			signal.setId(nextId);
			signalList.add(signal);
			LOG.info(() -> "Added signal " + signal);
			return nextId;
		}
	}

	/**
	 * Removes the signal with the given ID from the registry.
	 *
	 * @param id the ID of the signal to remove
	 * @return {@code true} if a signal was removed
	 */
	public boolean removeSignal(final int id) {
		synchronized (signalList) {
			final boolean removed = signalList.removeIf(s -> s.getId() == id);
			if (removed) {
				LOG.info(() -> "Removed signal " + id);
			}
			return removed;
		}
	}

	/**
	 * Returns the signal with the given ID, if any.
	 *
	 * @param id the ID to look up, may be {@code null}
	 * @return the signal, or empty if not found or {@code id} is {@code null}
	 */
	public Optional<ISignal> getSignal(final Integer id) {
		if (id == null) {
			return Optional.empty();
		}
		synchronized (signalList) {
			return signalList.stream().filter(s -> s.getId() == id).findFirst();
		}
	}

	/**
	 * Returns an unmodifiable snapshot of all registered signals.
	 *
	 * @return the signal list
	 */
	public List<ISignal> getSignals() {
		synchronized (signalList) {
			return Collections.unmodifiableList(new ArrayList<>(signalList));
		}
	}
}

