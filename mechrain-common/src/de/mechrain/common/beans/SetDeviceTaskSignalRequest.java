package de.mechrain.common.beans;

/**
 * CLI request to attach or detach the gating signal for a measurement task on
 * a specific device. Shortcut for the device-config subflow: works directly in
 * the main loop without entering the config mode. A {@code null}
 * {@link #signalId} clears the gate (task always polls).
 */
public class SetDeviceTaskSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int deviceId;
	public final int taskId;
	public final Integer signalId;

	public SetDeviceTaskSignalRequest(final int deviceId, final int taskId, final Integer signalId) {
		this.deviceId = deviceId;
		this.taskId = taskId;
		this.signalId = signalId;
	}
}
