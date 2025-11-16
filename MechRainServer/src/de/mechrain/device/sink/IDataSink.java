package de.mechrain.device.sink;

import java.io.Serializable;

import de.mechrain.device.IIdProvider;
import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface IDataSink extends Serializable, IIdProvider {
	
	void setId(int nextId);
	
	boolean connect();
	
	void disconnect();
	
	boolean isAvailable();
	
	void handleDataUnit(final AbstractMechRainDataUnit mdu);

}
