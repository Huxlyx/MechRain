package de.mechrain.cmdline.beans;

import de.mechrain.cmdline.beans.DeviceListResponse.DeviceData;

public class DeviceConfigResponse implements ICliBean {

	private static final long serialVersionUID = 1234567890123456789L;
	
	public final DeviceData deviceData;
	
	public DeviceConfigResponse(final DeviceData deviceData) {
		this.deviceData = deviceData;
	}
}
