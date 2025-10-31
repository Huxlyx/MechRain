package de.mechrain.cmdline.beans;

import java.io.Serializable;
import java.util.List;

import de.mechrain.device.Device;

public class DeviceListResponse implements ICliBean {

	private static final long serialVersionUID = 5184032790285646478L;
	
	private List<DeviceData> deviceList;

	public List<DeviceData> getDeviceList() {
		return deviceList;
	}

	public void setDeviceList(final List<Device> deviceList) {
		this.deviceList = deviceList.stream()
			.map(d -> new DeviceData(d.getId(), d.getName(), d.getDescription(), d.isConnected()))
			.toList();
	}
	
	
	public static class DeviceData implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final int id;
		private final String name;
		private final String description;
		private final boolean connected;
		
		public DeviceData(final int id, final String name, final String description, final boolean connected) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.connected = connected;
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

		public boolean isConnected() {
			return connected;
		}
		
	}
}
