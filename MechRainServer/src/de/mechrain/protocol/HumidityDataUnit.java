package de.mechrain.protocol;

public class HumidityDataUnit extends AbstractMechRainDataUnit {
	
	private final float humidity;

	protected HumidityDataUnit(HumidityBuilder builder) {
		super(builder);
		this.humidity = builder.humidity;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[7];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		
		final int intBits = Float.floatToIntBits(humidity);
		result[3] = (byte) (intBits >> 24);
		result[4] = (byte) (intBits >> 16);
		result[5] = (byte) (intBits >> 8);
		result[6] = (byte) (intBits);
		return result;
	}
	
	public float getHumidity() {
		return humidity;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" humidity: ").append(humidity);
		return sb.toString();
	}
	
	public static class HumidityBuilder extends Builder<HumidityDataUnit, HumidityBuilder> {

		private float humidity;
		
		public HumidityBuilder() {
			super(MRP.HUMIDITY);
			length(4);
		}
		
		public HumidityBuilder humidity(final float humidity) {
			this.humidity = humidity;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected HumidityBuilder getThis() {
			return this;
		}

		@Override
		protected HumidityDataUnit buildInternal() {
			return new HumidityDataUnit(this);
		}
	}
}
