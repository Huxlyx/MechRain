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

/**
 * A task that requests a specific measurement at a defined interval.
 */
public class MeasurementTask implements ITask {

	private static final long serialVersionUID = -3426586415869508895L;
	
	protected static final Logger LOG = LogManager.getLogger(Logging.DEVICE_TASK);
	
	protected int interval;
	protected TimeUnit timeUnit;
	
	protected MRP measurement;
	
	protected int id;
	
	/**
	 * Default constructor for de-serialization purposes.
	 */
	public MeasurementTask() {
		/* empty constructor for de-serialization */
	}
	
	/**
	 * Constructs a MeasurementTask with the specified interval, time unit, and measurement type.
	 *
	 * @param interval    the interval at which to perform the measurement
	 * @param timeUnit    the time unit for the interval
	 * @param measurement the type of measurement to request
	 */
	public MeasurementTask(final int interval, final TimeUnit timeUnit, final MRP measurement) {
		this.interval = interval;
		this.timeUnit = timeUnit;
		this.measurement = measurement;
	}

	/**
	 * Gets the interval at which the measurement is requested.
	 *
	 * @return the interval
	 */
	public int getInterval() {
		return interval;
	}

	/**
	 * Gets the time unit for the measurement interval.
	 *
	 * @return the time unit
	 */
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	/**
	 * Gets the type of measurement being requested.
	 *
	 * @return the measurement type
	 */
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
		LOG.trace(() -> "Queueing measurement task: " + this);
		try {
			final MeasurementRequestDataUnit mreq = new MeasurementRequestBuilder().measurementId(measurement).build();
			if ( ! requests.offer(mreq)) {
				LOG.error(() -> "Could not queue measurement request data unit for task: " + this);
			}
		} catch (final DataUnitValidationException | IllegalStateException e) {
			LOG.error(() -> "Could not queue task " + e.getMessage(), e);
		}
	}
}
