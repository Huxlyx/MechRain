package de.mechrain.common.beans;

/**
 * Sent by the CLI to transfer sinks, tasks, and description from a target device
 * (which must be disconnected) to the device currently being configured.
 * The replacing device keeps its own ID; the target device is cleaned up and
 * its description is updated to indicate it was replaced.
 */
public class ReplaceDeviceRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	private int targetDeviceId;

	public ReplaceDeviceRequest() {}

	public ReplaceDeviceRequest(final int targetDeviceId) {
		this.targetDeviceId = targetDeviceId;
	}

	public int getTargetDeviceId() {
		return targetDeviceId;
	}

	public void setTargetDeviceId(final int targetDeviceId) {
		this.targetDeviceId = targetDeviceId;
	}
}
