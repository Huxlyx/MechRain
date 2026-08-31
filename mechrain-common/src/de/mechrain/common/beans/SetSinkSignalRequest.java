package de.mechrain.common.beans;

/**
 * CLI request to attach or detach the gating signal for a sink on the device
 * currently being configured. A {@code null} {@link #signalId} clears the gate
 * (sink is always active).
 */
public class SetSinkSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int sinkId;
	public final Integer signalId;

	public SetSinkSignalRequest(final int sinkId, final Integer signalId) {
		this.sinkId = sinkId;
		this.signalId = signalId;
	}
}
