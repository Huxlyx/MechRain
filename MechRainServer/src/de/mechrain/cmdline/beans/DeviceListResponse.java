package de.mechrain.cmdline.beans;

import java.util.List;
import java.util.Map;

import de.mechrain.device.Device;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.task.MeasurementTask;

public class DeviceListResponse implements ICliBean {

	private static final long serialVersionUID = 5184032790285646478L;
	
	private List<DeviceData> deviceList;

	public List<DeviceData> getDeviceList() {
		return deviceList;
	}

	public void setDeviceList(final List<Device> deviceList) {
		this.deviceList = deviceList.stream()
			.map(d -> new DeviceData(d.getId(), d.getName(), d.getDescription(), d.getBuildId(), d.isConnected(), d.getTasks(), d.getSinks()))
			.toList();
	}
	
	public static class DeviceData implements ICliBean {
		
		private static final long serialVersionUID = 1L;
		
		private final int id;
		private final String name;
		private final String description;
		private final String buildId;
		private final boolean connected;
		private final Map<Integer, String> tasks;
		private final Map<Integer, String> sinks;
		
		public DeviceData(final Device device) {
			this(device.getId(), device.getName(), device.getDescription(), device.getBuildId(), device.isConnected(), device.getTasks(), device.getSinks());
		}
		
		public DeviceData(final int id, final String name, final String description, final String buildId, final boolean connected, final List<MeasurementTask> tasks, final List<IDataSink> sinks) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.connected = connected;
			this.buildId = buildId;
			this.tasks = tasks.stream().collect(java.util.stream.Collectors.toMap(MeasurementTask::getId, t -> t.toString()));
			this.sinks = sinks.stream().collect(java.util.stream.Collectors.toMap(IDataSink::getId, s -> s.toString()));
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
		
		public Map<Integer, String> getSinks() {
			return sinks;
		}
		
		public Map<Integer, String> getTasks() {
			return tasks;
		}
	}
}
