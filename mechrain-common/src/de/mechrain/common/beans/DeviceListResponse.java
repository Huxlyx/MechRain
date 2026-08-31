package de.mechrain.common.beans;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.mechrain.common.IDeviceDescriptor;
import de.mechrain.common.ISignalDescriptor;
import de.mechrain.common.ISinkDescriptor;
import de.mechrain.common.ITaskDescriptor;

/**
 * Server response carrying a snapshot of all registered devices,
 * sent to CLI clients in response to a {@link DeviceListRequest}.
 */
public class DeviceListResponse implements ICliBean {

	private static final long serialVersionUID = 5184032790285646478L;
	
	private List<DeviceData> deviceList;

	/** Returns the list of device snapshots. */
	public List<DeviceData> getDeviceList() {
		return deviceList;
	}

	/**
	 * Populates the device list from a list of {@link IDeviceDescriptor}s,
	 * creating an immutable {@link DeviceData} snapshot for each.
	 *
	 * @param deviceList the live device descriptors to snapshot
	 */
	public void setDeviceList(final List<IDeviceDescriptor> deviceList) {
		setDeviceList(deviceList, id -> null);
	}

	/**
	 * Populates the device list from a list of {@link IDeviceDescriptor}s,
	 * creating an immutable {@link DeviceData} snapshot for each and resolving
	 * gating signals via the given lookup so the CLI can render them without a
	 * second round trip.
	 *
	 * @param deviceList     the live device descriptors to snapshot
	 * @param signalResolver resolves a signal ID to its descriptor, or {@code null} if unknown
	 */
	public void setDeviceList(final List<IDeviceDescriptor> deviceList, final Function<Integer, ISignalDescriptor> signalResolver) {
		this.deviceList = deviceList.stream()
			.map(d -> new DeviceData(d, signalResolver))
			.toList();
	}
	
	/**
	 * Immutable snapshot of a single device, safe to transfer over the wire.
	 */
	public static class DeviceData implements ICliBean {
		
		private static final long serialVersionUID = 3L;
		
		private final int id;
		private final String name;
		private final String description;
		private final String buildId;
		private final boolean connected;
		private final long lastContactAt;
		private final Map<Integer, TaskData> tasks;
		private final Map<Integer, SinkData> sinks;
		
		/**
		 * Creates a snapshot of the given device at the current point in time.
		 *
		 * @param device the live device descriptor to snapshot
		 */
		public DeviceData(final IDeviceDescriptor device) {
			this(device, id -> null);
		}

		/**
		 * Creates a snapshot of the given device, resolving gating signals via the
		 * given lookup so the CLI can render them without a second round trip.
		 *
		 * @param device         the live device descriptor to snapshot
		 * @param signalResolver resolves a signal ID to its descriptor, or {@code null} if unknown
		 */
		public DeviceData(final IDeviceDescriptor device, final Function<Integer, ISignalDescriptor> signalResolver) {
			this.id = device.getId();
			this.name = device.getName();
			this.description = device.getDescription();
			this.buildId = device.getBuildId();
			this.connected = device.isConnected();
			this.lastContactAt = device.getLastContactAt();
			this.tasks = device.getTaskDescriptors().stream()
					.collect(Collectors.toMap(ITaskDescriptor::getId, t -> new TaskData(t, signalResolver)));
			this.sinks = device.getSinkDescriptors().stream()
					.collect(Collectors.toMap(ISinkDescriptor::getId, s -> new SinkData(s, signalResolver)));
		}

		/** Returns the device ID. */
		public int getId() {
			return id;
		}

		/** Returns the device name, or {@code null} if not set. */
		public String getName() {
			return name;
		}

		/** Returns the device description, or {@code null} if not set. */
		public String getDescription() {
			return description;
		}
		
		/** Returns the firmware build ID, or {@code null} if not yet received. */
		public String getBuildId() {
			return buildId;
		}

		/** Returns {@code true} if the device was connected at snapshot time. */
		public boolean isConnected() {
			return connected;
		}

		/**
		 * Returns the epoch milliseconds of the most recent disconnect,
		 * or {@code 0} if the device has never connected.
		 */
		public long getLastContactAt() {
			return lastContactAt;
		}
		
		/** Returns a map of sink ID to {@link SinkData} for this device. */
		public Map<Integer, SinkData> getSinks() {
			return sinks;
		}
		
		/** Returns a map of task ID to {@link TaskData} for this device. */
		public Map<Integer, TaskData> getTasks() {
			return tasks;
		}

		/**
		 * Immutable snapshot of a measurement task.
		 */
		public static class TaskData implements ICliBean {

			private static final long serialVersionUID = 1L;

			private final int id;
			private final String measurement;
			private final int interval;
			private final String timeUnit;
			private final Integer channelId;
			private final boolean adaptive;
			private final long minIntervalMs;
			private final double changeThreshold;
			private final double speedupFactor;
			private final double slowdownFactor;
			private final Integer signalId;
			private final String signalDescription;
			private final Boolean signalActive;

			/**
			 * Creates a snapshot of the given task with no signal resolution.
			 *
			 * @param task the live task descriptor to snapshot
			 */
			public TaskData(final ITaskDescriptor task) {
				this(task, id -> null);
			}

			/**
			 * Creates a snapshot of the given task, resolving its gating signal (if any)
			 * against the given lookup so the CLI can render it without a second round trip.
			 *
			 * @param task           the live task descriptor to snapshot
			 * @param signalResolver resolves a signal ID to its descriptor, or {@code null} if unknown
			 */
			public TaskData(final ITaskDescriptor task, final Function<Integer, ISignalDescriptor> signalResolver) {
				this.id = task.getId();
				this.measurement = task.getMeasurementName();
				this.interval = task.getInterval();
				this.timeUnit = task.getTimeUnitName();
				this.channelId = task.getChannelId();
				this.adaptive = task.isAdaptive();
				this.minIntervalMs = task.getMinIntervalMs();
				this.changeThreshold = task.getChangeThreshold();
				this.speedupFactor = task.getSpeedupFactor();
				this.slowdownFactor = task.getSlowdownFactor();
				this.signalId = task.getSignalId();
				final ISignalDescriptor signal = signalId == null ? null : signalResolver.apply(signalId);
				this.signalDescription = signal == null ? null : signal.getSignalType() + ": " + signal.getSignalDescription();
				this.signalActive = signal == null ? null : signal.isActive();
			}

			/** Returns the task ID. */
			public int getId() { return id; }
			/** Returns the measurement name (e.g. {@code "TEMPERATURE"}). */
			public String getMeasurement() { return measurement; }
			/** Returns the configured polling interval. */
			public int getInterval() { return interval; }
			/** Returns the time unit name for the polling interval (e.g. {@code "SECONDS"}). */
			public String getTimeUnit() { return timeUnit; }
			/** Returns the ADC channel ID, or {@code null} if not applicable. */
			public Integer getChannelId() { return channelId; }
			/** Returns {@code true} if adaptive polling is enabled for this task. */
			public boolean isAdaptive() { return adaptive; }
			/** Returns the minimum polling interval in milliseconds when adaptive polling is active. */
			public long getMinIntervalMs() { return minIntervalMs; }
			/** Returns the value-change threshold that triggers a polling speed-up. */
			public double getChangeThreshold() { return changeThreshold; }
			/** Returns the factor by which the interval is multiplied on a speed-up event. */
			public double getSpeedupFactor() { return speedupFactor; }
			/** Returns the factor by which the interval is multiplied on a slow-down event. */
			public double getSlowdownFactor() { return slowdownFactor; }
			/** Returns the ID of the signal gating this task, or {@code null} if not gated. */
			public Integer getSignalId() { return signalId; }
			/** Returns a human-readable description of the gating signal, or {@code null} if not gated. */
			public String getSignalDescription() { return signalDescription; }
			/** Returns {@code true}/{@code false} if the gating signal is currently active/inactive, or {@code null} if not gated. */
			public Boolean getSignalActive() { return signalActive; }
		}

		/**
		 * Immutable snapshot of a data sink.
		 */
		public static class SinkData implements ICliBean {

			private static final long serialVersionUID = 1L;

			private final int id;
			private final String type;
			private final List<String> filterNames;
			private final String description;
			private final Integer signalId;
			private final String signalDescription;
			private final Boolean signalActive;

			/**
			 * Creates a snapshot of the given sink with no signal resolution.
			 *
			 * @param sink the live sink descriptor to snapshot
			 */
			public SinkData(final ISinkDescriptor sink) {
				this(sink, id -> null);
			}

			/**
			 * Creates a snapshot of the given sink, resolving its gating signal (if any)
			 * against the given lookup so the CLI can render it without a second round trip.
			 *
			 * @param sink           the live sink descriptor to snapshot
			 * @param signalResolver resolves a signal ID to its descriptor, or {@code null} if unknown
			 */
			public SinkData(final ISinkDescriptor sink, final Function<Integer, ISignalDescriptor> signalResolver) {
				this.id = sink.getId();
				this.type = sink.getSinkType();
				this.filterNames = sink.getFilterNames();
				this.description = sink.getSinkDescription();
				this.signalId = sink.getSignalId();
				final ISignalDescriptor signal = signalId == null ? null : signalResolver.apply(signalId);
				this.signalDescription = signal == null ? null : signal.getSignalType() + ": " + signal.getSignalDescription();
				this.signalActive = signal == null ? null : signal.isActive();
			}

			/** Returns the sink ID. */
			public int getId() { return id; }
			/** Returns the sink type name (e.g. {@code "VictoriaMetrics"}, {@code "Dummy"}). */
			public String getType() { return type; }
			/** Returns the measurement names this sink accepts, or {@code null} if it accepts all. */
			public List<String> getFilterNames() { return filterNames; }
			/** Returns the human-readable sink description. */
			public String getDescription() { return description; }
			/** Returns the ID of the signal gating this sink, or {@code null} if not gated. */
			public Integer getSignalId() { return signalId; }
			/** Returns a human-readable description of the gating signal, or {@code null} if not gated. */
			public String getSignalDescription() { return signalDescription; }
			/** Returns {@code true}/{@code false} if the gating signal is currently active/inactive, or {@code null} if not gated. */
			public Boolean getSignalActive() { return signalActive; }
		}
	}
}
