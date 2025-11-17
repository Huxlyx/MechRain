package de.mechrain.protocol;

/**
 * Data unit representing a measurement request.
 */
public class MeasurementRequestDataUnit extends AbstractMechRainDataUnit {
	
	protected final MRP measurementId;

	protected MeasurementRequestDataUnit(final MeasurementRequestBuilder builder) {
		super(builder);
		this.measurementId = builder.measurementId;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[6];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = measurementId.byteVal;
		result[4] = 0x00; /* reserved */
		result[5] = 0x00; /* reserved */
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName())
			.append(" length: ").append(length)
			.append(" measurementId: ").append(measurementId.name());
		return sb.toString();
	}
	
	public static class MeasurementRequestBuilder extends Builder<MeasurementRequestDataUnit, MeasurementRequestBuilder> {

		private MRP measurementId;
		
		public MeasurementRequestBuilder() {
			super(MRP.MEASUREMENT_REQ);
			length(3);
		}
		
		public MeasurementRequestBuilder measurementId(final MRP settingId) {
			this.measurementId = settingId;
			return getThis();
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected MeasurementRequestBuilder getThis() {
			return this;
		}

		@Override
		protected MeasurementRequestDataUnit buildInternal() {
			return new MeasurementRequestDataUnit(this);
		}
	}
}
