package de.mechrain.web;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.mechrain.common.beans.DeviceListRequest;
import de.mechrain.common.beans.DeviceListResponse;
import de.mechrain.common.beans.SignalListRequest;
import de.mechrain.common.beans.SignalListResponse;
import de.mechrain.common.beans.SignalListResponse.SignalData;

/**
 * Polls the server every 3 seconds ({@link DeviceListRequest} + {@link SignalListRequest})
 * and keeps the {@link WebState} up to date.
 *
 * <p>Older servers (pre-signals) never respond to {@link SignalListRequest}. After one
 * timeout the poller assumes that signals are not supported and stops sending the request;
 * the probe is re-run after every (re)connection.
 */
public final class StatePoller {

	private static final long POLL_INTERVAL_MS = 3_000;
	private static final long REQUEST_TIMEOUT_MS = 5_000;

	private final MechrainClient client;
	private final WebState state;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		final Thread t = new Thread(r, "WebStatePoller");
		t.setDaemon(true);
		return t;
	});

	private volatile boolean signalsSupported = true;
	private volatile boolean wasConnected = false;

	public StatePoller(final MechrainClient client, final WebState state) {
		this.client = client;
		this.state = state;
	}

	public void start() {
		scheduler.scheduleAtFixedRate(this::tick, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	public void stop() {
		scheduler.shutdownNow();
	}

	private void tick() {
		try {
			final MechrainClient.State cs = client.getState();
			if (cs != MechrainClient.State.CONNECTED) {
				wasConnected = false;
				state.setConnection(false, cs == MechrainClient.State.RECONNECTING || cs == MechrainClient.State.CONNECTING);
				return;
			}
			if (!wasConnected) {
				// (Re)connected: re-probe signal support
				signalsSupported = true;
			}
			wasConnected = true;

			final DeviceListResponse dlr = client.sendAndAwait(DeviceListRequest.INSTANCE, DeviceListResponse.class, REQUEST_TIMEOUT_MS);

			if (signalsSupported) {
				try {
					final SignalListResponse slr = client.sendAndAwait(SignalListRequest.INSTANCE, SignalListResponse.class, REQUEST_TIMEOUT_MS);
					state.update(dlr.getDeviceList(), slr.getSignalList(), client.getServerVersion(), client.getProtocolVersion());
				} catch (final MechrainClient.RequestTimeoutException e) {
					// Server too old for signals (pre-2.1.0): it never responds to SignalListRequest.
					signalsSupported = false;
					state.update(dlr.getDeviceList(), Collections.emptyList(), client.getServerVersion(), client.getProtocolVersion());
				}
			} else {
				state.update(dlr.getDeviceList(), Collections.emptyList(), client.getServerVersion(), client.getProtocolVersion());
			}
		} catch (final Exception e) {
			wasConnected = false;
			state.setConnection(false, true);
		}
	}
}
