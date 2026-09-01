package de.mechrain.signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
			if (createsCycle(nextId, signal.getChildSignalIds())) {
				throw new IllegalArgumentException("Adding signal with children " + signal.getChildSignalIds()
						+ " would create a reference cycle");
			}
			signal.setId(nextId);
			signalList.add(signal);
			LOG.info(() -> "Added signal " + signal);
			return nextId;
		}
	}

	/**
	 * Returns {@code true} if a signal with the given ID and child signals would create
	 * a reference cycle in the signal graph, i.e. if any of the children transitively
	 * references {@code signalId} again. Keeping the graph acyclic guarantees that
	 * {@link ISignal#isActive()} resolution cannot recurse indefinitely. Note that IDs
	 * are reused after removal (next free ID = max + 1), so a newly added signal can
	 * re-enter an existing reference chain.
	 *
	 * @param signalId the ID the new signal would receive
	 * @param childIds the child signal IDs of the new signal, may be {@code null}
	 * @return {@code true} if adding the signal would create a cycle
	 */
	public boolean createsCycle(final int signalId, final List<Integer> childIds) {
		if (childIds == null || childIds.isEmpty()) {
			return false;
		}
		synchronized (signalList) {
			final Deque<Integer> stack = new ArrayDeque<>(childIds);
			final Set<Integer> visited = new HashSet<>();
			while ( ! stack.isEmpty()) {
				final int current = stack.pop();
				if (current == signalId) {
					return true;
				}
				if ( ! visited.add(current)) {
					continue;
				}
				signalList.stream().filter(s -> s.getId() == current).findFirst()
					.map(ISignal::getChildSignalIds)
					.ifPresent(children -> children.forEach(stack::push));
			}
			return false;
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

