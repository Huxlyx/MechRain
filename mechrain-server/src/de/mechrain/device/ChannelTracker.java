package de.mechrain.device;

import java.util.EnumSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.MRP;

/**
 * Matches measurement replies to the channel of the channeled request they answer.
 * <p>
 * Older firmware does not include the channel in its reply, so the request thread announces each
 * channeled request before sending it and then waits until the read thread has received the
 * matching reply (or a timeout passes) before sending the next request.
 * <p>
 * Newer firmware appends the channel to the reply. As soon as the read thread sees such a reply
 * ({@link #channelReported}), the tracker switches off for that measurement for the rest of the
 * connection: the request thread no longer waits, and replies are attributed by their own channel
 * field. This is decided per measurement, so a measurement whose replies carry no channel keeps
 * being matched to its requests. Should a later reply of such a measurement come without the field
 * after all, tracking is switched back on for it.
 */
final class ChannelTracker {

	private static final Logger LOG = LogManager.getLogger(Logging.DATA);

	/** Maximum time to wait for the reply to a channeled request before sending the next request. */
	static final long REPLY_TIMEOUT_MS = 2_000;

	private MRP pendingMeasurement;
	private int pendingChannel;
	/* measurements whose replies carry their channel; one tracker exists per connection */
	private final Set<MRP> reportedMeasurements = EnumSet.noneOf(MRP.class);

	/**
	 * Called by the read thread for a reply that carries its own channel. Releases a request
	 * thread that may still be waiting for it and stops tracking this measurement for this connection.
	 */
	synchronized void channelReported(final MRP measurement) {
		reportedMeasurements.add(measurement);
		if (measurement == pendingMeasurement) {
			pendingMeasurement = null;
			notifyAll();
		}
	}

	/**
	 * Announces a channeled request that is about to be sent. Must be called before writing
	 * the request, so a fast reply can't arrive before it is expected.
	 *
	 * @return {@code true} if the reply has to be awaited via {@link #awaitReply}, {@code false} if
	 *         the device reports the channel of this measurement itself and nothing is tracked
	 */
	synchronized boolean expect(final MRP measurement, final int channel) {
		if (reportedMeasurements.contains(measurement)) {
			return false;
		}
		this.pendingMeasurement = measurement;
		this.pendingChannel = channel;
		return true;
	}

	/**
	 * Waits until the reply announced via {@link #expect} has been received.
	 *
	 * @return {@code true} if the reply arrived, {@code false} on timeout (the expectation is dropped)
	 */
	synchronized boolean awaitReply() throws InterruptedException {
		final long deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS;
		while (pendingMeasurement != null) {
			final long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0) {
				pendingMeasurement = null;
				return false;
			}
			wait(remaining);
		}
		return true;
	}

	/** Drops a pending expectation, e.g. when sending the request failed. */
	synchronized void clear() {
		pendingMeasurement = null;
		notifyAll();
	}

	/**
	 * Called by the read thread for each received measurement.
	 *
	 * @return the channel of the pending request if the measurement answers it, otherwise {@code null}
	 */
	synchronized Integer takeChannel(final MRP measurement) {
		if (reportedMeasurements.remove(measurement)) {
			/* this reply was not announced, so its channel is unknown; match the following ones to their requests again */
			LOG.warn(() -> "Reply for " + measurement + " carries no channel although earlier ones did,"
					+ " matching replies to requests again");
			return null;
		}
		if (measurement != pendingMeasurement) {
			return null;
		}
		pendingMeasurement = null;
		notifyAll();
		return pendingChannel;
	}
}
