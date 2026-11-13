package de.mechrain.signal;

import de.mechrain.common.IIdProvider;
import de.mechrain.common.ISignalDescriptor;

/**
 * A signal gates whether sinks/tasks that reference it should currently be active.
 * Signals are managed globally in the {@link SignalRegistry} and referenced by ID
 * from any device's sinks or tasks, so a single signal (e.g. a time window or a
 * threshold on one device's sensor) can gate behaviour on any device.
 */
public interface ISignal extends IIdProvider, ISignalDescriptor {

	/**
	 * Sets the ID of the signal.
	 *
	 * @param id the ID to set
	 */
	void setId(int id);
}
