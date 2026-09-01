package de.mechrain.signal;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.datatypes.FloatDataUnit;
import de.mechrain.protocol.datatypes.UInt1DataUnit;
import de.mechrain.protocol.datatypes.UInt2DataUnit;

/**
 * A signal that observes a single measurement on a target device (attached to that
 * device like a regular {@link IDataSink}) and is active whenever the last received
 * value satisfies the configured comparison against a threshold.
 */
public class ThresholdSignal extends AbstractSignal implements IDataSink {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LogManager.getLogger(Logging.SIGNAL);

	/** Comparison operator applied as {@code value <op> threshold}. */
	public enum Comparator {
		GT, GTE, LT, LTE;

		public boolean test(final double value, final double threshold) {
			return switch (this) {
				case GT -> value > threshold;
				case GTE -> value >= threshold;
				case LT -> value < threshold;
				case LTE -> value <= threshold;
			};
		}

		/**
		 * Hysteresis test applied while the signal is already active: returns
		 * {@code true} as long as the value has not crossed back over the off-threshold,
		 * i.e. it uses the opposite boundary of {@link #test(double, double)}.
		 */
		public boolean testOff(final double value, final double offThreshold) {
			return switch (this) {
				case GT, GTE -> value >= offThreshold;
				case LT, LTE -> value <= offThreshold;
			};
		}

		public String symbol() {
			return switch (this) {
				case GT -> ">";
				case GTE -> ">=";
				case LT -> "<";
				case LTE -> "<=";
			};
		}

		/** Returns the boundary symbol of the deactivation condition (see {@link #testOff}). */
		public String offSymbol() {
			return switch (this) {
				case GT, GTE -> "<";
				case LT, LTE -> ">";
			};
		}
	}

	private int targetDeviceId;
	private MRP measurement;
	private Comparator comparator;
	private double threshold;

	/**
	 * Optional hysteresis off-threshold: while the signal is active it stays active
	 * until the value crosses back over this boundary (see {@link Comparator#testOff}).
	 * If {@code null}, the signal deactivates as soon as the main comparison fails.
	 */
	private Double offThreshold;

	/**
	 * Optional stability window in minutes: a state change only takes effect after the
	 * new state has held continuously for this long. If {@code null} or not positive,
	 * state changes are immediate.
	 */
	private Integer stableForMinutes;

	private transient DeviceRegistry registry;
	private transient volatile boolean active;
	private transient volatile boolean pendingActive;
	private transient volatile long pendingStateSinceMillis;

	/** Default constructor for de-serialization purposes. */
	public ThresholdSignal() {
		/* empty constructor for de-serialization */
	}

	public ThresholdSignal(final int targetDeviceId, final MRP measurement, final Comparator comparator, final double threshold) {
		this.targetDeviceId = targetDeviceId;
		this.measurement = measurement;
		this.comparator = comparator;
		this.threshold = threshold;
	}

	/**
	 * Wires this signal with the live {@link DeviceRegistry}, needed to resolve
	 * the (possibly different) target device. Not persisted; must be called again
	 * after deserialization/restore.
	 *
	 * @param registry the device registry to resolve the target device from
	 */
	public void setRegistry(final DeviceRegistry registry) {
		this.registry = registry;
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

	public Comparator getComparator() {
		return comparator;
	}

	public void setComparator(final Comparator comparator) {
		this.comparator = comparator;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setThreshold(final double threshold) {
		this.threshold = threshold;
	}

	public Double getOffThreshold() {
		return offThreshold;
	}

	public void setOffThreshold(final Double offThreshold) {
		this.offThreshold = offThreshold;
	}

	public Integer getStableForMinutes() {
		return stableForMinutes;
	}

	public void setStableForMinutes(final Integer stableForMinutes) {
		this.stableForMinutes = stableForMinutes;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public String getSignalType() {
		return "Threshold";
	}

	@Override
	public String getSignalDescription() {
		final StringBuilder sb = new StringBuilder("Device " + targetDeviceId + " " + measurement
				+ " " + comparator.symbol() + " " + threshold);
		if (offThreshold != null) {
			sb.append(" (off ").append(comparator.offSymbol()).append(offThreshold).append(")");
		}
		if (stableForMinutes != null && stableForMinutes > 0) {
			sb.append(", stable ").append(stableForMinutes).append(" min");
		}
		return sb.toString();
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return null;
	}

	@Override
	public String getSinkType() {
		return "ThresholdSignal";
	}

	@Override
	public List<String> getFilterNames() {
		return List.of(measurement.name());
	}

	@Override
	public String getSinkDescription() {
		return getSignalDescription();
	}

	@Override
	public Integer getSignalId() {
		/* a threshold signal is not itself gated by another signal */
		return null;
	}

	@Override
	public void setSignalId(final Integer signalId) {
		/* no-op: gating another signal onto a threshold signal is not supported */
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
		if (mdu.getId() != measurement) {
			return;
		}
		final double value;
		switch (mdu.getId()) {
		case HUMIDITY:
		case TEMPERATURE:
			value = ((FloatDataUnit) mdu).getValue();
			break;
		case SOIL_MOISTURE_ABS:
		case CO2_PPM:
			value = ((UInt2DataUnit) mdu).getValue();
			break;
		case SOIL_MOISTURE_PERCENT:
			value = ((UInt1DataUnit) mdu).getValue();
			break;
		default:
			LOG.error(() -> "Data unit " + mdu.getClass().getSimpleName() + " not supported by ThresholdSignal");
			return;
		}

		final boolean rawActive;
		if (offThreshold == null || ! active) {
			rawActive = comparator.test(value, threshold);
		} else {
			/* hysteresis: once active, stay active until the value crosses back over the off-threshold */
			rawActive = comparator.testOff(value, offThreshold);
		}

		if (rawActive == active) {
			pendingStateSinceMillis = 0;
			return;
		}

		/* the state wants to change */
		final long now = System.currentTimeMillis();
		if (stableForMinutes == null || stableForMinutes <= 0) {
			active = rawActive;
			pendingStateSinceMillis = 0;
			LOG.debug(() -> "ThresholdSignal " + getId() + " state changed to active=" + active + " (value " + value + ")");
			return;
		}

		if (pendingActive != rawActive || pendingStateSinceMillis == 0) {
			/* new candidate state: start the stability window */
			pendingActive = rawActive;
			pendingStateSinceMillis = now;
			return;
		}

		if (now - pendingStateSinceMillis >= stableForMinutes * 60_000L) {
			active = rawActive;
			pendingStateSinceMillis = 0;
			LOG.debug(() -> "ThresholdSignal " + getId() + " state changed to active=" + active
					+ " after " + stableForMinutes + " min stability (value " + value + ")");
		}
	}

	/**
	 * Resolves and returns the target device from the wired registry.
	 *
	 * @return the target device, or empty if not found or the registry is not wired
	 */
	public Optional<Device> resolveTargetDevice() {
		return registry == null ? Optional.empty() : registry.getDevice(targetDeviceId);
	}

	@Override
	public String toString() {
		return "ThresholdSignal id:" + getId() + " " + getSignalDescription();
	}
}
