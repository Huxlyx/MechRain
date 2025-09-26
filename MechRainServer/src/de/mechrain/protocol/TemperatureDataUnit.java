package de.mechrain.protocol;

public class TemperatureDataUnit extends AbstractMechRainDataUnit {
	
	private final float temperature;

	protected TemperatureDataUnit(TemperatureBuilder builder) {
		super(builder);
		this.temperature = builder.temperature;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[7];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		
		final int intBits = Float.floatToIntBits(temperature);
		result[3] = (byte) (intBits >> 24);
		result[4] = (byte) (intBits >> 16);
		result[5] = (byte) (intBits >> 8);
		result[6] = (byte) (intBits);
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" temperature: ").append(temperature);
		return sb.toString();
	}
	
	public static class TemperatureBuilder extends Builder<TemperatureDataUnit, TemperatureBuilder> {

		private float temperature;
		
		public TemperatureBuilder() {
			super(MRP.TEMPERATURE);
			length(4);
		}
		
		public TemperatureBuilder temperature(final float temperature) {
			this.temperature = temperature;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected TemperatureBuilder getThis() {
			return this;
		}

		@Override
		protected TemperatureDataUnit buildInternal() {
			return new TemperatureDataUnit(this);
		}
	}
}
