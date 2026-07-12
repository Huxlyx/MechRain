package de.mechrain.device.sink;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.Device;
import de.mechrain.device.DeviceRegistry;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.LedAllRgbDataUnit;
import de.mechrain.protocol.LedAllRgbDataUnit.LedAllRgbBuilder;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.datatypes.FloatDataUnit;
import de.mechrain.protocol.datatypes.UInt1DataUnit;
import de.mechrain.protocol.datatypes.UInt2DataUnit;

/**
 * A data sink that translates a single measurement into an LED color, using linear
 * interpolation ("gradient") between a set of configured threshold/color stops
 * (e.g. 0 ppm -&gt; green, 800 ppm -&gt; yellow, 1200 ppm -&gt; red), and sends the
 * resulting color to a target device (which may be the same device the measurement
 * came from, or a different registered device).
 *
 * <p>The sink only sends an updated LED color when the computed color actually
 * changes, to avoid flooding the target device with redundant commands. If the
 * target device is currently disconnected, the update is skipped entirely (not
 * queued) rather than logged as a warning, since the device may simply be offline
 * on purpose; the color is not marked as sent, so it is retried once the device
 * reconnects and a new measurement is received.</p>
 */
public class LedIndicatorSink extends AbstractFilteredDataSink {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LogManager.getLogger(Logging.SINK);

	private final MRP measurement;
	private final int targetDeviceId;
	private final List<ColorStop> colorStops;

	private transient DeviceRegistry registry;
	private transient int[] lastSentColor;

	private LedIndicatorSink(final Builder builder) {
		super(Collections.singletonList(builder.measurement));
		super.setId(builder.id);
		this.measurement = builder.measurement;
		this.targetDeviceId = builder.targetDeviceId;
		this.colorStops = Collections.unmodifiableList(new ArrayList<>(builder.colorStops));
	}

	/**
	 * Wires this sink with the live {@link DeviceRegistry}, which it needs at
	 * measurement-handling time to resolve the (possibly different) target device.
	 * Not persisted; must be called again after deserialization/restore.
	 *
	 * @param registry the device registry to resolve the target device from
	 */
	public void setRegistry(final DeviceRegistry registry) {
		this.registry = registry;
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
			LOG.trace(() -> "Skip handling of " + mdu.getId() + ", sink configured for " + measurement);
			return;
		}
		if (registry == null) {
			LOG.warn(() -> "LedIndicatorSink " + getId() + " has no DeviceRegistry wired, skipping");
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
			LOG.error(() -> "Data unit " + mdu.getClass().getSimpleName() + " not supported by LedIndicatorSink");
			return;
		}

		final int[] color = computeColor(value);
		if (lastSentColor != null && lastSentColor[0] == color[0] && lastSentColor[1] == color[1]
				&& lastSentColor[2] == color[2]) {
			LOG.trace(() -> "Computed LED color unchanged (" + color[0] + "," + color[1] + "," + color[2]
					+ "), skipping update");
			return;
		}

		final Optional<Device> targetOpt = registry.getDevice(targetDeviceId);
		if (targetOpt.isEmpty()) {
			LOG.warn(() -> "LedIndicatorSink target device " + targetDeviceId + " not found in registry, skipping");
			return;
		}
		final Device targetDevice = targetOpt.get();
		if ( ! targetDevice.isConnected()) {
			/* Device may simply be offline/powered down on purpose - log quietly and don't mark
			 * the color as sent, so a resend is attempted once it reconnects and a new
			 * measurement is received. */
			LOG.debug(() -> "LedIndicatorSink target device " + targetDeviceId + " not connected, skipping LED update");
			return;
		}

		try {
			final LedAllRgbDataUnit du = new LedAllRgbBuilder()
					.red(color[0])
					.green(color[1])
					.blue(color[2])
					.build();
			targetDevice.queueRequest(du);
			lastSentColor = color;
			LOG.debug(() -> "Sent LED color " + color[0] + "," + color[1] + "," + color[2] + " to device " + targetDeviceId + " for " + measurement + "=" + value);
		} catch (final DataUnitValidationException e) {
			LOG.error(() -> "Error building LED update for device " + targetDeviceId, e);
		}
	}

	/**
	 * Computes the interpolated RGB color for the given measurement value, based on
	 * the configured, ascending-sorted color stops. Values below the first stop's
	 * threshold are clamped to the first stop's color; values above the last stop's
	 * threshold are clamped to the last stop's color. Values between stops are
	 * linearly interpolated per channel.
	 *
	 * @param value the measurement value to map to a color
	 * @return a 3-element array {@code [r, g, b]}
	 */
	private int[] computeColor(final double value) {
		final ColorStop first = colorStops.get(0);
		if (value <= first.threshold()) {
			return new int[] { first.r(), first.g(), first.b() };
		}
		final ColorStop last = colorStops.get(colorStops.size() - 1);
		if (value >= last.threshold()) {
			return new int[] { last.r(), last.g(), last.b() };
		}
		for (int i = 0; i < colorStops.size() - 1; i++) {
			final ColorStop lo = colorStops.get(i);
			final ColorStop hi = colorStops.get(i + 1);
			if (value >= lo.threshold() && value <= hi.threshold()) {
				final double t = (value - lo.threshold()) / (hi.threshold() - lo.threshold());
				return new int[] {
						interpolateChannel(lo.r(), hi.r(), t),
						interpolateChannel(lo.g(), hi.g(), t),
						interpolateChannel(lo.b(), hi.b(), t) };
			}
		}
		/* should not be reachable given the clamping above, but fall back safely */
		return new int[] { last.r(), last.g(), last.b() };
	}

	private static int interpolateChannel(final int a, final int b, final double t) {
		return (int) Math.round(a + (b - a) * t);
	}

	public MRP getMeasurement() {
		return measurement;
	}

	public int getTargetDeviceId() {
		return targetDeviceId;
	}

	public List<ColorStop> getColorStops() {
		return colorStops;
	}

	@Override
	public String getSinkType() {
		return "LedIndicator";
	}

	@Override
	public String getSinkDescription() {
		return measurement.name() + " -> Device " + targetDeviceId + " (" + colorStops.size() + " stops)";
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("LedIndicatorSink measurement:").append(measurement)
			.append(" targetDeviceId:").append(targetDeviceId);
		final StringJoiner sj = new StringJoiner(",");
		for (final ColorStop stop : colorStops) {
			sj.add(stop.threshold() + "=" + stop.r() + "/" + stop.g() + "/" + stop.b());
		}
		sb.append(" stops:<").append(sj.toString()).append('>')
			.append(" id:").append(getId());
		return sb.toString();
	}

	/**
	 * A single gradient stop: at {@code threshold} the LED color is exactly
	 * {@code (r, g, b)}; values between two stops are linearly interpolated.
	 */
	public record ColorStop(double threshold, int r, int g, int b) implements Serializable {

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Builder for {@link LedIndicatorSink}. Validates required fields, sorts color
	 * stops ascending by threshold, and enforces strictly ascending thresholds and
	 * valid (0-255) RGB channel values before building.
	 */
	public static class Builder {

		private int id;
		private MRP measurement;
		private int targetDeviceId = -1;
		private final List<ColorStop> colorStops = new ArrayList<>();

		public Builder id(final int id) {
			this.id = id;
			return this;
		}

		public Builder measurement(final MRP measurement) {
			this.measurement = measurement;
			return this;
		}

		public Builder targetDeviceId(final int targetDeviceId) {
			this.targetDeviceId = targetDeviceId;
			return this;
		}

		public Builder colorStop(final double threshold, final int r, final int g, final int b) {
			this.colorStops.add(new ColorStop(threshold, r, g, b));
			return this;
		}

		public Builder colorStops(final List<ColorStop> stops) {
			this.colorStops.clear();
			if (stops != null) {
				this.colorStops.addAll(stops);
			}
			return this;
		}

		private void validate() {
			if (measurement == null) {
				throw new IllegalStateException("LedIndicatorSink: measurement must be provided");
			}
			if (targetDeviceId < 0) {
				throw new IllegalStateException("LedIndicatorSink: targetDeviceId must be provided");
			}
			if (colorStops.size() < 2) {
				throw new IllegalStateException("LedIndicatorSink: at least two color stops are required");
			}
			final List<ColorStop> sorted = new ArrayList<>(colorStops);
			sorted.sort(Comparator.comparingDouble(ColorStop::threshold));
			for (int i = 1; i < sorted.size(); i++) {
				if (sorted.get(i).threshold() <= sorted.get(i - 1).threshold()) {
					throw new IllegalStateException("LedIndicatorSink: color stop thresholds must be strictly ascending");
				}
			}
			for (final ColorStop stop : sorted) {
				validateChannel(stop.r(), "r");
				validateChannel(stop.g(), "g");
				validateChannel(stop.b(), "b");
			}
			colorStops.clear();
			colorStops.addAll(sorted);
		}

		private static void validateChannel(final int value, final String name) {
			if (value < 0 || value > 255) {
				throw new IllegalStateException("LedIndicatorSink: color channel '" + name + "' must be in range 0-255");
			}
		}

		public LedIndicatorSink build() {
			validate();
			return new LedIndicatorSink(this);
		}
	}
}
