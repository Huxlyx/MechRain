package de.mechrain.device.task;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.MeasurementRequestDataUnit;
import de.mechrain.protocol.MeasurementRequestDataUnit.MeasurementRequestBuilder;

public class MeasurementTask implements ITask {

	private static final long serialVersionUID = -3426586415869508895L;
	
	protected static final Logger LOG = LogManager.getLogger(Logging.DEVICE_TASK);
	
	protected int interval;
	protected TimeUnit timeUnit;
	
	protected MRP measurement;
	
	protected int id;
	
	public MeasurementTask() {
		/* empty constructor for de-serialization */
	}
	
	public MeasurementTask(final int interval, final TimeUnit timeUnit, final MRP measurement) {
		this.interval = interval;
		this.timeUnit = timeUnit;
		this.measurement = measurement;
	}

	public int getInterval() {
		return interval;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	public MRP getMeasurement() {
		return measurement;
	}
	
	@Override
	public int getId() {
		return id;
	}
	
	public void setId(final int id) {
		this.id = id;
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("MeasurementTask for ").append(measurement)
			.append(" interval:").append(interval).append(timeUnit)
			.append(" id:").append(id);
		return sb.toString();
	}

	@Override
	public void queueTask(final Queue<AbstractMechRainDataUnit> requests) {
		try {
			final MeasurementRequestDataUnit mreq = new MeasurementRequestBuilder().measurementId(measurement).build();
			requests.add(mreq);
		} catch (final DataUnitValidationException | IllegalStateException e) {
			LOG.error(() -> "Could not queue task " + e.getMessage(), e);
		}
	}
}
