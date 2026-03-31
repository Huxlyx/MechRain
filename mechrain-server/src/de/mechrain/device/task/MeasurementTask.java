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

	// Adaptive configuration (persisted)
	private boolean adaptive = false;
	private long minIntervalMs = 5_000;
	private double changeThreshold = 1.0;
	private double speedupFactor = 0.5;
	private double slowdownFactor = 1.5;

	// Transient runtime state (not persisted)
	private transient volatile long currentIntervalMs = 0;
	private transient volatile long lastPollTime = 0;
	private transient Double lastValue = null;

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

	public boolean isAdaptive() {
		return adaptive;
	}

	public void setAdaptive(final boolean adaptive) {
		this.adaptive = adaptive;
	}

	public long getMinIntervalMs() {
		return minIntervalMs;
	}

	public void setMinIntervalMs(final long minIntervalMs) {
		this.minIntervalMs = minIntervalMs;
	}

	public double getChangeThreshold() {
		return changeThreshold;
	}

	public void setChangeThreshold(final double changeThreshold) {
		this.changeThreshold = changeThreshold;
	}

	public double getSpeedupFactor() {
		return speedupFactor;
	}

	public void setSpeedupFactor(final double speedupFactor) {
		this.speedupFactor = speedupFactor;
	}

	public double getSlowdownFactor() {
		return slowdownFactor;
	}

	public void setSlowdownFactor(final double slowdownFactor) {
		this.slowdownFactor = slowdownFactor;
	}

	/**
	 * Adjusts the current polling interval based on the magnitude of change
	 * observed in the latest measurement value.
	 */
	public synchronized void onValueReceived(final double newValue) {
		final long baseMs = timeUnit.toMillis(interval);
		if (currentIntervalMs == 0) {
			currentIntervalMs = baseMs;
		}
		if (lastValue != null) {
			final long prevIntervalMs = currentIntervalMs;
			final double delta = Math.abs(newValue - lastValue);
			if (delta >= changeThreshold) {
				currentIntervalMs = Math.max(minIntervalMs, (long) (currentIntervalMs * speedupFactor));
				LOG.info(() -> String.format(
					"Adaptive speedup [%s] delta=%.4f >= threshold=%.4f  %dms -> %dms",
					measurement, delta, changeThreshold, prevIntervalMs, currentIntervalMs));
			} else {
				currentIntervalMs = Math.min(baseMs, (long) (currentIntervalMs * slowdownFactor));
				if (currentIntervalMs != prevIntervalMs) {
					LOG.debug(() -> String.format(
						"Adaptive slowdown [%s] delta=%.4f < threshold=%.4f  %dms -> %dms",
						measurement, delta, changeThreshold, prevIntervalMs, currentIntervalMs));
				}
			}
		}
		lastValue = newValue;
	}

	/**
	 * Returns {@code true} if this adaptive task should be skipped because not
	 * enough time has elapsed since the last poll. Updates {@code lastPollTime}
	 * when it returns {@code false}.
	 */
	protected boolean checkAdaptiveGate() {
		if (!adaptive) {
			return false;
		}
		final long now = System.currentTimeMillis();
		final long effectiveInterval = currentIntervalMs > 0 ? currentIntervalMs : timeUnit.toMillis(interval);
		if (now - lastPollTime < effectiveInterval) {
			return true;
		}
		lastPollTime = now;
		return false;
	}


	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("MeasurementTask for ").append(measurement)
			.append(" interval:").append(interval).append(timeUnit)
			.append(" id:").append(id);
		if (adaptive) {
			sb.append(" adaptive[min:").append(minIntervalMs).append("ms")
				.append(" threshold:").append(changeThreshold)
				.append(" speedup:").append(speedupFactor)
				.append(" slowdown:").append(slowdownFactor).append(']');
		}
		return sb.toString();
	}

	@Override
	public void queueTask(final Queue<AbstractMechRainDataUnit> requests) {
		if (checkAdaptiveGate()) {
			return;
		}
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
