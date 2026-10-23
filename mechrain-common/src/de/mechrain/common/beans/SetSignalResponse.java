package de.mechrain.common.beans;

/**
 * Explicit response to {@link SetDeviceSinkSignalRequest},
 * {@link SetDeviceTaskSignalRequest} and {@link SetSignalStateRequest}. Carries
 * either success or a human-readable error message (e.g. device, sink, task or
 * signal not found).
 */
public class SetSignalResponse implements ICliBean {

	private static final long serialVersionUID = 1L;

	private boolean success;
	private String message;

	public SetSignalResponse() {}

	public SetSignalResponse(final boolean success, final String message) {
		this.success = success;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(final boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(final String message) {
		this.message = message;
	}
}
