package de.mechrain.protocol;

public class FloatDataUnit extends AbstractMechRainDataUnit {
	
	private final float value;

	protected FloatDataUnit(FloatDataUnitBuilder builder) {
		super(builder);
		this.value = builder.value;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[7];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		
		final int intBits = Float.floatToIntBits(value);
		result[3] = (byte) (intBits >> 24);
		result[4] = (byte) (intBits >> 16);
		result[5] = (byte) (intBits >> 8);
		result[6] = (byte) (intBits);
		return result;
	}
	
	public float getValue() {
		return value;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName())
		.append(' ').append(id)
		.append(" length: ").append(length)
		.append(" humidity: ").append(value);
		return sb.toString();
	}
	
	public static class FloatDataUnitBuilder extends Builder<FloatDataUnit, FloatDataUnitBuilder> {

		private float value;
		
		public FloatDataUnitBuilder(final MRP mrp) {
			super(mrp);
			length(4);
		}
		
		public FloatDataUnitBuilder humidity(final float humidity) {
			this.value = humidity;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected FloatDataUnitBuilder getThis() {
			return this;
		}

		@Override
		protected FloatDataUnit buildInternal() {
			return new FloatDataUnit(this);
		}
	}
}
