package de.mechrain.device.sink;

import java.io.Serializable;

import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface IDataSink extends Serializable {
	
	boolean connect();
	
	void disconnect();
	
	boolean isAvailable();
	
	void handleDataUnit(final AbstractMechRainDataUnit mdu);

}
