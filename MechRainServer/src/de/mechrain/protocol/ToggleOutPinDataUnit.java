package de.mechrain.protocol;

/**
 * Data unit to toggle an output pin for a defined duration.
 */
public class ToggleOutPinDataUnit extends AbstractMechRainDataUnit {
	
	private final byte channel;
	private int duration;

	protected ToggleOutPinDataUnit(final ToggleOutPinBuilder builder) {
		super(builder);
		this.channel = builder.channel;
		this.duration = builder.duration;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[6];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = channel;
		result[4] = (byte) (duration >> 8);
		result[5] = (byte) duration;
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length)
			.append(" channel: ").append(channel)
			.append(" duration: ").append(duration).append("ms");
		return sb.toString();
	}
	
	public class ToggleOutPinBuilder extends Builder<ToggleOutPinDataUnit, ToggleOutPinBuilder> {

		private byte channel;
		private int duration;
		
		public ToggleOutPinBuilder() {
			super(MRP.TOGGLE_OUT_PIN);
			length(3);
		}
		
		public ToggleOutPinBuilder channel(final byte channel) {
			this.channel = channel;
			return getThis();
		}
		
		public ToggleOutPinBuilder duration(final int duration) {
			this.duration = duration;
			return getThis();
		}

		@Override
		protected void validate() throws DataUnitValidationException {
			if (duration > UNSIGNED_SHORT_LENGTH || duration < 0) {
				throw new DataUnitValidationException("Invalid duration " + duration);
			}
		}
		
		@Override
		protected ToggleOutPinBuilder getThis() {
			return this;
		}

		@Override
		public ToggleOutPinDataUnit buildInternal() {
			return new ToggleOutPinDataUnit(this);
		}
	}
}
