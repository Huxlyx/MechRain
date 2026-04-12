package de.mechrain.common.beans;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.mechrain.common.IDeviceDescriptor;
import de.mechrain.common.ISinkDescriptor;
import de.mechrain.common.ITaskDescriptor;

public class DeviceListResponse implements ICliBean {

	private static final long serialVersionUID = 5184032790285646478L;
	
	private List<DeviceData> deviceList;

	public List<DeviceData> getDeviceList() {
		return deviceList;
	}

	public void setDeviceList(final List<IDeviceDescriptor> deviceList) {
		this.deviceList = deviceList.stream()
			.map(DeviceData::new)
			.toList();
	}
	
	public static class DeviceData implements ICliBean {
		
		private static final long serialVersionUID = 2L;
		
		private final int id;
		private final String name;
		private final String description;
		private final String buildId;
		private final boolean connected;
		private final Map<Integer, TaskData> tasks;
		private final Map<Integer, SinkData> sinks;
		
		public DeviceData(final IDeviceDescriptor device) {
			this.id = device.getId();
			this.name = device.getName();
			this.description = device.getDescription();
			this.buildId = device.getBuildId();
			this.connected = device.isConnected();
			this.tasks = device.getTaskDescriptors().stream()
					.collect(Collectors.toMap(ITaskDescriptor::getId, TaskData::new));
			this.sinks = device.getSinkDescriptors().stream()
					.collect(Collectors.toMap(ISinkDescriptor::getId, SinkData::new));
		}

		public int getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}
		
		public String getBuildId() {
			return buildId;
		}

		public boolean isConnected() {
			return connected;
		}
		
		public Map<Integer, SinkData> getSinks() {
			return sinks;
		}
		
		public Map<Integer, TaskData> getTasks() {
			return tasks;
		}

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

			public TaskData(final ITaskDescriptor task) {
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
			}

			public int getId() { return id; }
			public String getMeasurement() { return measurement; }
			public int getInterval() { return interval; }
			public String getTimeUnit() { return timeUnit; }
			public Integer getChannelId() { return channelId; }
			public boolean isAdaptive() { return adaptive; }
			public long getMinIntervalMs() { return minIntervalMs; }
			public double getChangeThreshold() { return changeThreshold; }
			public double getSpeedupFactor() { return speedupFactor; }
			public double getSlowdownFactor() { return slowdownFactor; }
		}

		public static class SinkData implements ICliBean {

			private static final long serialVersionUID = 1L;

			private final int id;
			private final String type;
			private final List<String> filterNames;
			private final String description;

			public SinkData(final ISinkDescriptor sink) {
				this.id = sink.getId();
				this.type = sink.getSinkType();
				this.filterNames = sink.getFilterNames();
				this.description = sink.getSinkDescription();
			}

			public int getId() { return id; }
			public String getType() { return type; }
			/** Measurement names this sink accepts, or {@code null} if it accepts all. */
			public List<String> getFilterNames() { return filterNames; }
			public String getDescription() { return description; }
		}
	}
}

