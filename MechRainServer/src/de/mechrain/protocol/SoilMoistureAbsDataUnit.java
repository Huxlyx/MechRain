package de.mechrain.protocol;

public class SoilMoistureAbsDataUnit extends AbstractMechRainDataUnit {
	
	private final int soilMoistureAbs;

	protected SoilMoistureAbsDataUnit(final SoilMoistureAbsBuilder builder) {
		super(builder);
		this.soilMoistureAbs = builder.soilMoistureAbs;
	}
	
	public int getSoilMoistAbs() {
		return soilMoistureAbs;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[5];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) (soilMoistureAbs >> 8);
		result[4] = (byte) soilMoistureAbs;
		
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" soilMoistureAbs: ").append(soilMoistureAbs);
		return sb.toString();
	}
	
	public static class SoilMoistureAbsBuilder extends Builder<SoilMoistureAbsDataUnit, SoilMoistureAbsBuilder> {

		private int soilMoistureAbs;
		
		public SoilMoistureAbsBuilder() {
			super(MRP.SOIL_MOISTURE_ABS);
			length(1);
		}
		
		public SoilMoistureAbsBuilder soilMoistureAbs(final int soilMoistureAbs) {
			this.soilMoistureAbs = soilMoistureAbs;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected SoilMoistureAbsBuilder getThis() {
			return this;
		}

		@Override
		protected SoilMoistureAbsDataUnit buildInternal() {
			return new SoilMoistureAbsDataUnit(this);
		}
	}
}
