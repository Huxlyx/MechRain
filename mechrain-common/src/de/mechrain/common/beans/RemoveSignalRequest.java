package de.mechrain.common.beans;

/** CLI request to remove a signal from the global {@code SignalRegistry} by ID. */
public class RemoveSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final int id;

	public RemoveSignalRequest(final int id) {
		this.id = id;
	}
}
