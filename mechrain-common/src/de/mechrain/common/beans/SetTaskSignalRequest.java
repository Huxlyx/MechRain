package de.mechrain.common.beans;

/**
 * CLI request to attach or detach the gating signal for a measurement task on
 * the device currently being configured. A {@code null} {@link #signalId} clears
 * the gate (task always polls).
 */
public class SetTaskSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int taskId;
	public final Integer signalId;

	public SetTaskSignalRequest(final int taskId, final Integer signalId) {
		this.taskId = taskId;
		this.signalId = signalId;
	}
}
