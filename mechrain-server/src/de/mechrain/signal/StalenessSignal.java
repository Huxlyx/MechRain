package de.mechrain.signal;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.MRP;

/**
 * A "dead man's switch" signal: active while the target device has not delivered
 * (matching) data units for longer than the configured timeout. Like
 * {@link ThresholdSignal} it is attached to its target device as a pseudo-sink
 * to observe incoming data units. If no specific measurement is configured, any
 * data unit from the device (including heartbeats) counts as contact, so the
 * signal only goes stale once the device is actually silent or disconnected.
 *
 * <p>After a server restart the staleness clock starts fresh at wire time
 * (see {@link #setRegistry(DeviceRegistry)}), so a device that was already
 * silent before the restart only reports stale after the timeout has elapsed
 * since startup.
 *
 * <p>State transitions are logged exactly once each: a warning when the device
 * goes stale (the dead man's switch fires) and an info message when it recovers.
 */
public class StalenessSignal extends AbstractSignal implements IDataSink {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LogManager.getLogger(Logging.SIGNAL);

	private int targetDeviceId;
	/** The measurement to observe, or {@code null} to accept any data unit from the device. */
	private MRP measurement;
	private double timeoutMinutes;

	private transient DeviceRegistry registry;
	/** Epoch millis of the last observed (matching) data unit, set at wire time. */
	private transient volatile long lastSeenMillis;
	/**
	 * Tracks whether the current stale state has already been announced, so each
	 * transition is logged exactly once even if {@link #isActive()} is called from
	 * multiple threads concurrently.
	 */
	private transient final AtomicBoolean staleAnnounced = new AtomicBoolean();

	/** Default constructor for de-serialization purposes. */
	public StalenessSignal() {
		/* empty constructor for de-serialization */
	}

	public StalenessSignal(final int targetDeviceId, final MRP measurement, final double timeoutMinutes) {
		this.targetDeviceId = targetDeviceId;
		this.measurement = measurement;
		this.timeoutMinutes = timeoutMinutes;
	}

	/**
	 * Wires this signal with the live {@link DeviceRegistry}. Not persisted; must be
	 * called again after deserialization/restore. Resets the staleness clock to now,
	 * i.e. after a server restart the timeout runs from startup.
	 *
	 * @param registry the device registry to resolve the target device from
	 */
	public void setRegistry(final DeviceRegistry registry) {
		this.registry = registry;
		this.lastSeenMillis = System.currentTimeMillis();
	}

	public int getTargetDeviceId() {
		return targetDeviceId;
	}

	public void setTargetDeviceId(final int targetDeviceId) {
		this.targetDeviceId = targetDeviceId;
	}

	public MRP getMeasurement() {
		return measurement;
	}

	public void setMeasurement(final MRP measurement) {
		this.measurement = measurement;
	}

	public double getTimeoutMinutes() {
		return timeoutMinutes;
	}

	public void setTimeoutMinutes(final double timeoutMinutes) {
		this.timeoutMinutes = timeoutMinutes;
	}

	@Override
	public boolean isActive() {
		if (registry == null) {
			return false;
		}
		final boolean stale = System.currentTimeMillis() - lastSeenMillis > timeoutMinutes * 60_000.0;
		if (stale != staleAnnounced.get() && staleAnnounced.compareAndSet( ! stale, stale)) {
			if (stale) {
				LOG.warn(() -> "StalenessSignal " + getId() + ": device " + targetDeviceId + " silent for "
						+ (System.currentTimeMillis() - lastSeenMillis) / 60_000 + " min (timeout " + timeoutMinutes
						+ " min), signal now active");
			} else {
				LOG.info(() -> "StalenessSignal " + getId() + ": device " + targetDeviceId
						+ " delivering data again, signal inactive");
			}
		}
		return stale;
	}

	@Override
	public String getSignalType() {
		return "Staleness";
	}

	@Override
	public String getSignalDescription() {
		return "Device " + targetDeviceId + (measurement == null ? "" : " " + measurement)
				+ " silent > " + timeoutMinutes + " min";
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return null;
	}

	@Override
	public String getSinkType() {
		return "StalenessSignal";
	}

	@Override
	public List<String> getFilterNames() {
		return measurement == null ? null : List.of(measurement.name());
	}

	@Override
	public String getSinkDescription() {
		return getSignalDescription();
	}

	@Override
	public Integer getSignalId() {
		/* a staleness signal is not itself gated by another signal */
		return null;
	}

	@Override
	public void setSignalId(final Integer signalId) {
		/* no-op: gating another signal onto a staleness signal is not supported */
	}

	@Override
	public boolean connect() {
		return registry != null;
	}

	@Override
	public void disconnect() {
		/* nothing to release - registry reference is simply left in place */
	}

	@Override
	public boolean isAvailable() {
		return registry != null;
	}

	@Override
	public void handleDataUnit(final AbstractMechRainDataUnit mdu) {
		if (measurement == null || mdu.getId() == measurement) {
			lastSeenMillis = System.currentTimeMillis();
		}
	}

	@Override
	public String toString() {
		return "StalenessSignal id:" + getId() + " " + getSignalDescription();
	}
}
