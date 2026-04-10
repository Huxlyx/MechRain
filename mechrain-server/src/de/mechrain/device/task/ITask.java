package de.mechrain.device.task;

import java.io.Serializable;
import java.util.Queue;

import de.mechrain.common.IIdProvider;
import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface ITask extends Serializable, IIdProvider {
	
	boolean queueTask(Queue<AbstractMechRainDataUnit> requests);
}
