package de.mechrain.common.beans;

/**
 * CLI request to attach or detach the gating signal for a sink on a specific
 * device. Shortcut for the device-config subflow: works directly in the main
 * loop without entering the config mode. A {@code null} {@link #signalId}
 * clears the gate (sink is always active).
 */
public class SetDeviceSinkSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int deviceId;
	public final int sinkId;
	public final Integer signalId;

	public SetDeviceSinkSignalRequest(final int deviceId, final int sinkId, final Integer signalId) {
		this.deviceId = deviceId;
		this.sinkId = sinkId;
		this.signalId = signalId;
	}
}
