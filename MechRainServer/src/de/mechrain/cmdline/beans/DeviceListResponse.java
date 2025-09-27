package de.mechrain.cmdline.beans;

import java.util.List;

import de.mechrain.device.Device;

public class DeviceListResponse implements ICliBean {

	private static final long serialVersionUID = 5184032790285646478L;
	
	private List<Device> deviceList;

	public List<Device> getDeviceList() {
		return deviceList;
	}

	public void setDeviceList(List<Device> deviceList) {
		this.deviceList = deviceList;
	}
}
