package de.mechrain.device.task;

import java.io.Serializable;
import java.util.Queue;

import de.mechrain.protocol.AbstractMechRainDataUnit;

public interface ITask extends Serializable {

	void queueTask(Queue<AbstractMechRainDataUnit> requests);
}
