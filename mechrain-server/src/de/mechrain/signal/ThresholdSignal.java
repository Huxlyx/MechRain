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

		public String symbol() {
			return switch (this) {
				case GT -> ">";
				case GTE -> ">=";
				case LT -> "<";
				case LTE -> "<=";
			};
		}
	}

	private int targetDeviceId;
	private MRP measurement;
	private Comparator comparator;
	private double threshold;

	private transient DeviceRegistry registry;
	private transient volatile boolean active;

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
		return "Device " + targetDeviceId + " " + measurement + " " + comparator.symbol() + " " + threshold;
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
		active = comparator.test(value, threshold);
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
