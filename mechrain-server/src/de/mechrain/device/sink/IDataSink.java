package de.mechrain.device.sink;

import java.io.Serializable;

import de.mechrain.common.IIdProvider;
import de.mechrain.common.ISinkDescriptor;
import de.mechrain.protocol.AbstractMechRainDataUnit;

/**
 * Interface for data sinks that can receive and process MechRain data units.
 */
public interface IDataSink extends Serializable, IIdProvider, ISinkDescriptor {
	
	/**
	 * Sets the ID of the data sink.
	 * 
	 * @param nextId The ID to set.
	 */
	void setId(int nextId);

	/**
	 * Returns the ID of the signal gating this sink, or {@code null} if the sink is
	 * always active (no gating signal attached).
	 */
	Integer getSignalId();

	/**
	 * Sets the ID of the signal gating this sink.
	 *
	 * @param signalId the signal ID to gate on, or {@code null} to clear gating
	 */
	void setSignalId(Integer signalId);
	
	/**
	 * Connects the data sink.
	 * 
	 * @return true if the connection was successful, false otherwise.
	 */
	boolean connect();
	
	/**
	 * Disconnects the data sink.
	 */
	void disconnect();
	
	/**
	 * Checks if the data sink is available for receiving data units.
	 * 
	 * @return true if available, false otherwise.
	 */
	boolean isAvailable();
	
	/**
	 * Handles the given MechRain data unit.
	 * 
	 * @param mdu The data unit to handle.
	 */
	void handleDataUnit(final AbstractMechRainDataUnit mdu);
}
