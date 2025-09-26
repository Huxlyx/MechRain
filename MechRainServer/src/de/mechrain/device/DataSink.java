package de.mechrain.device;

import java.io.Serializable;

import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface DataSink extends Serializable {
	
	boolean connect();
	
	boolean isAvailable();
	
	void handleDataUnit(final AbstractMechRainDataUnit mdu);

}
