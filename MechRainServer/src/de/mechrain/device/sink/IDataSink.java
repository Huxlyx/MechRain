package de.mechrain.device.sink;

import java.io.Serializable;

import de.mechrain.device.IIdProvider;
import de.mechrain.protocol.AbstractMechRainDataUnit;

/**
 * Interface for data sinks that can receive and process MechRain data units.
 */
public interface IDataSink extends Serializable, IIdProvider {
	
	/**
	 * Sets the ID of the data sink.
	 * 
	 * @param nextId The ID to set.
	 */
	void setId(int nextId);
	
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
