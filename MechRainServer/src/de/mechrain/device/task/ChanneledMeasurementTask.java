package de.mechrain.device.task;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.ChanneledMeasurementRequestDataUnit;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.MRP;

/**
 * A MeasurementTask that includes a channel ID for channeled measurements.
 */
public class ChanneledMeasurementTask extends MeasurementTask {

	private static final long serialVersionUID = 1L;
	
	private final int channelId;
	
	public ChanneledMeasurementTask(final int interval, final TimeUnit timeUnit, final MRP measurement, final int channelId) {
		super(interval, timeUnit, measurement);
		this.channelId = channelId;
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("MeasurementTask for ").append(measurement)
			.append(" interval:").append(interval).append(timeUnit)
			.append(" channelId:").append(channelId)
			.append(" id:").append(id);
		return sb.toString();
	}
	
	@Override
	public void queueTask(Queue<AbstractMechRainDataUnit> requests) {
		try {
			final ChanneledMeasurementRequestDataUnit mreq = new ChanneledMeasurementRequestDataUnit.ChanneledMeasurementRequestBuilder()
					.measurementId(measurement)
					.channelId((byte) channelId)
					.build();
			requests.add(mreq);
		} catch (final DataUnitValidationException | IllegalStateException e) {
			LOG.error(() -> "Could not queue task " + e.getMessage(), e);
		}
	}
}
