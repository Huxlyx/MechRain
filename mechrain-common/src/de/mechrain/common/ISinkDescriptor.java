package de.mechrain.common;

import java.util.List;

/**
 * Describes the configuration of a data sink for use in CLI data transfer.
 */
public interface ISinkDescriptor extends IIdProvider {

	/** Returns a short human-readable type label, e.g. "InfluxDB", "VictoriaMetrics", "Dummy". */
	String getSinkType();

	/**
	 * Returns the measurement names this sink accepts, or {@code null} if the sink accepts all
	 * measurements without filtering.
	 */
	List<String> getFilterNames();

	/** Returns a concise human-readable description of the sink endpoint. */
	String getSinkDescription();

	/**
	 * Returns the ID of the signal gating this sink, or {@code null} if the sink is
	 * always active (no gating signal attached).
	 */
	Integer getSignalId();
}
