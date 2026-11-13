package de.mechrain.common.beans;

import java.util.List;

import de.mechrain.common.ISignalDescriptor;

/**
 * Server response carrying a snapshot of all registered signals,
 * sent to CLI clients in response to a {@link SignalListRequest}.
 */
public class SignalListResponse implements ICliBean {

	private static final long serialVersionUID = 1L;

	private List<SignalData> signalList;

	/** Returns the list of signal snapshots. */
	public List<SignalData> getSignalList() {
		return signalList;
	}

	/**
	 * Populates the signal list from a list of {@link ISignalDescriptor}s,
	 * creating an immutable {@link SignalData} snapshot for each, with no usage
	 * information (all signals report an empty {@code usedBy} list).
	 *
	 * @param signalList the live signal descriptors to snapshot
	 */
	public void setSignalList(final List<ISignalDescriptor> signalList) {
		setSignalList(signalList, id -> List.of());
	}

	/**
	 * Populates the signal list from a list of {@link ISignalDescriptor}s,
	 * creating an immutable {@link SignalData} snapshot for each, resolving the
	 * human-readable list of consumers (devices/sinks/tasks/other signals) that
	 * reference each signal so the CLI can render a graph of connections.
	 *
	 * @param signalList    the live signal descriptors to snapshot
	 * @param usageResolver resolves a signal ID to a list of human-readable usage labels
	 */
	public void setSignalList(final List<ISignalDescriptor> signalList,
			final java.util.function.Function<Integer, List<String>> usageResolver) {
		this.signalList = signalList.stream()
			.map(s -> new SignalData(s, usageResolver.apply(s.getId())))
			.toList();
	}

	/**
	 * Immutable snapshot of a single signal, safe to transfer over the wire.
	 */
	public static class SignalData implements ICliBean {

		private static final long serialVersionUID = 1L;

		private final int id;
		private final String type;
		private final String description;
		private final boolean active;
		private final List<Integer> childSignalIds;
		private final List<String> usedBy;

		/**
		 * Creates a snapshot of the given signal at the current point in time,
		 * with no usage information.
		 *
		 * @param signal the live signal descriptor to snapshot
		 */
		public SignalData(final ISignalDescriptor signal) {
			this(signal, List.of());
		}

		/**
		 * Creates a snapshot of the given signal at the current point in time,
		 * including a human-readable list of its consumers.
		 *
		 * @param signal the live signal descriptor to snapshot
		 * @param usedBy human-readable labels of everything currently referencing this signal
		 */
		public SignalData(final ISignalDescriptor signal, final List<String> usedBy) {
			this.id = signal.getId();
			this.type = signal.getSignalType();
			this.description = signal.getSignalDescription();
			this.active = signal.isActive();
			this.childSignalIds = signal.getChildSignalIds();
			this.usedBy = usedBy;
		}

		/** Returns the signal ID. */
		public int getId() { return id; }
		/** Returns the signal type name (e.g. {@code "Time Window"}, {@code "Threshold"}, {@code "Logic Gate"}). */
		public String getType() { return type; }
		/** Returns the human-readable signal definition, e.g. {@code "08:00-20:00"}. */
		public String getDescription() { return description; }
		/** Returns {@code true} if the signal was active at snapshot time. */
		public boolean isActive() { return active; }
		/** Returns the IDs of child signals combined by this signal, or {@code null} if not a logic gate. */
		public List<Integer> getChildSignalIds() { return childSignalIds; }
		/** Returns human-readable labels of everything currently referencing this signal (devices, sinks, tasks, other signals). */
		public List<String> getUsedBy() { return usedBy; }
	}
}
