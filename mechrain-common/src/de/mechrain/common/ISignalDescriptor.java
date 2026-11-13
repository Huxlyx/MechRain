package de.mechrain.common;

import java.util.List;

/**
 * Describes the configuration of a signal for use in CLI data transfer.
 * A signal gates whether a sink or task should currently be active
 * (e.g. a time window, a threshold on a measurement, or a logic gate
 * combining other signals with AND/OR).
 */
public interface ISignalDescriptor extends IIdProvider {

	/** Returns a short human-readable type label, e.g. "Time Window", "Threshold", "Logic Gate". */
	String getSignalType();

	/** Returns a concise human-readable description of the signal's definition, e.g. "08:00-20:00". */
	String getSignalDescription();

	/** Returns {@code true} if the signal is currently active (gate open). */
	boolean isActive();

	/**
	 * Returns the IDs of child signals combined by this signal, or {@code null}
	 * if this signal has no children (i.e. it is not a logic gate).
	 */
	List<Integer> getChildSignalIds();
}
