package de.mechrain.device;

import de.mechrain.protocol.MRP;

/**
 * Matches measurement replies to the channel of the channeled request they answer.
 * The device's reply does not carry the channel, so the request thread announces each
 * channeled request before sending it and then waits until the read thread has received
 * the matching reply (or a timeout passes) before sending the next request.
 */
final class ChannelTracker {

	/** Maximum time to wait for the reply to a channeled request before sending the next request. */
	static final long REPLY_TIMEOUT_MS = 2_000;

	private MRP pendingMeasurement;
	private int pendingChannel;

	/**
	 * Announces a channeled request that is about to be sent. Must be called before writing
	 * the request, so a fast reply can't arrive before it is expected.
	 */
	synchronized void expect(final MRP measurement, final int channel) {
		this.pendingMeasurement = measurement;
		this.pendingChannel = channel;
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
		if (measurement != pendingMeasurement) {
			return null;
		}
		pendingMeasurement = null;
		notifyAll();
		return pendingChannel;
	}
}
