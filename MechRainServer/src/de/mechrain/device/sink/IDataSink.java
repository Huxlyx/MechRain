package de.mechrain.device.sink;

import java.io.Serializable;

import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface IDataSink extends Serializable {
	
	int getId();
	
	boolean connect();
	
	void disconnect();
	
	boolean isAvailable();
	
	void handleDataUnit(final AbstractMechRainDataUnit mdu);
}
