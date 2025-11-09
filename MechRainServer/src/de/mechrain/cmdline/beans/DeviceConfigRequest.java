package de.mechrain.cmdline.beans;

public class DeviceConfigRequest implements ICliBean {

	private static final long serialVersionUID = -8176135389086361441L;
	
	private int deviceId;

	public int getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}
}
