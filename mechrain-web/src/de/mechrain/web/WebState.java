package de.mechrain.web;

import java.util.List;

import de.mechrain.common.beans.DeviceListResponse.DeviceData;
import de.mechrain.common.beans.SignalListResponse.SignalData;

/**
 * The current web state: connection status plus the latest device/signal snapshot.
 * Updated by {@link StatePoller}, served by the HTTP API.
 */
public final class WebState {

	private volatile boolean connected;
	private volatile boolean reconnecting;
	private volatile boolean readOnly;
	private volatile String serverVersion;
	private volatile int protocolVersion;
	private volatile long lastUpdated;
	private volatile List<DeviceData> devices = List.of();
	private volatile List<SignalData> signals = List.of();

	public boolean isConnected() {
		return connected;
	}

	public boolean isReconnecting() {
		return reconnecting;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(final boolean readOnly) {
		this.readOnly = readOnly;
	}

	public String getServerVersion() {
		return serverVersion;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public long getLastUpdated() {
		return lastUpdated;
	}

	public List<DeviceData> getDevices() {
		return devices;
	}

	public List<SignalData> getSignals() {
		return signals;
	}

	public void setConnection(final boolean connected, final boolean reconnecting) {
		this.reconnecting = reconnecting;
		this.connected = connected;
	}

	public void update(final List<DeviceData> devices, final List<SignalData> signals,
			final String serverVersion, final int protocolVersion) {
		this.devices = devices;
		this.signals = signals;
		this.serverVersion = serverVersion;
		this.protocolVersion = protocolVersion;
		this.lastUpdated = System.currentTimeMillis();
		this.connected = true;
		this.reconnecting = false;
	}

	/** Refreshes only the signal list (e.g. right after signal creation). */
	public void setSignals(final List<SignalData> signals) {
		this.signals = signals;
		this.lastUpdated = System.currentTimeMillis();
	}

	/** Refreshes only the device list (e.g. right after a device name change). */
	public void setDevices(final List<DeviceData> devices) {
		this.devices = devices;
		this.lastUpdated = System.currentTimeMillis();
	}
}
