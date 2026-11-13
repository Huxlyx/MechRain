package de.mechrain.common.beans;

/**
 * CLI request to set the state of a manually controlled signal. Only manual
 * signals have a settable state; for all other signal types the server responds
 * with a failed {@link SetSignalResponse}. Works directly in the main loop, no
 * config subflow involved.
 */
public class SetSignalStateRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int signalId;
	public final boolean active;

	public SetSignalStateRequest(final int signalId, final boolean active) {
		this.signalId = signalId;
		this.active = active;
	}
}
