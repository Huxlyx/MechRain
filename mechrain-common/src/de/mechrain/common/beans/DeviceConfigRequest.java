package de.mechrain.common.beans;

public class DeviceConfigRequest implements ICliBean {

	private static final long serialVersionUID = -8176135389086361441L;
	
	private int deviceId;
	/* echoed in the response that answers this request, so late answers can be told apart; never 0 */
	private int requestId;

	public int getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}

	public int getRequestId() {
		return requestId;
	}

	public void setRequestId(final int requestId) {
		this.requestId = requestId;
	}
}
