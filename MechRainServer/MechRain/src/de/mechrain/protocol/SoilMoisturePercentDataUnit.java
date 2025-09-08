package de.mechrain.protocol;

public class SoilMoisturePercentDataUnit extends AbstractMechRainDataUnit {
	
	private final int soilMoisturePercent;

	protected SoilMoisturePercentDataUnit(final SoilMoisturePercentBuilder builder) {
		super(builder);
		this.soilMoisturePercent = builder.soilMoisturePercent;
	}

	public int getSoilMoistPercent() {
		return soilMoisturePercent;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[5];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) (length >> 8);
		
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" soilMoisturePercent: ").append(soilMoisturePercent);
		return sb.toString();
	}
	
	public static class SoilMoisturePercentBuilder extends Builder<SoilMoisturePercentDataUnit, SoilMoisturePercentBuilder> {

		private int soilMoisturePercent;
		
		public SoilMoisturePercentBuilder() {
			super(MRP.SOIL_MOISTURE_PERCENT);
			length(1);
		}
		
		public SoilMoisturePercentBuilder soilMoisturePercent(final int soilMoisturePercent) {
			this.soilMoisturePercent = soilMoisturePercent;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected SoilMoisturePercentBuilder getThis() {
			return this;
		}

		@Override
		protected SoilMoisturePercentDataUnit buildInternal() {
			return new SoilMoisturePercentDataUnit(this);
		}
	}
}
