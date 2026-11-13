package de.mechrain.common.beans;

import de.mechrain.common.beans.DeviceListResponse.DeviceData;

public class DeviceConfigResponse implements ICliBean {

	private static final long serialVersionUID = 1234567890123456789L;

	/** Request id of responses that answer no request (updates sent during a config session). */
	public static final int NO_REQUEST = 0;

	/** Id of the {@link DeviceConfigRequest} this response answers, or {@link #NO_REQUEST}. */
	public final int requestId;

	/** The configured device, or {@code null} if the requested device was not found. */
	public final DeviceData deviceData;

	/** Creates an update sent during a config session. */
	public DeviceConfigResponse(final DeviceData deviceData) {
		this(NO_REQUEST, deviceData);
	}

	/** Creates the answer to the {@link DeviceConfigRequest} with the given id. */
	public DeviceConfigResponse(final int requestId, final DeviceData deviceData) {
		this.requestId = requestId;
		this.deviceData = deviceData;
	}

	/** Creates the answer to a {@link DeviceConfigRequest} for a device that does not exist. */
	public static DeviceConfigResponse notFound(final int requestId) {
		return new DeviceConfigResponse(requestId, null);
	}
}
