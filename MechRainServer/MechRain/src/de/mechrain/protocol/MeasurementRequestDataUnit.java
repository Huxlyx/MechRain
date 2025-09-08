package de.mechrain.protocol;

public class MeasurementRequestDataUnit extends AbstractMechRainDataUnit {
	
	private final MRP measurementId;
	private final byte channelId;

	protected MeasurementRequestDataUnit(final MeasurementRequestBuilder builder) {
		super(builder);
		this.measurementId = builder.measurementId;
		this.channelId = builder.channelId;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[10];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = measurementId.byteVal;
		result[4] = 0x00; /* reserved */
		result[5] = 0x00; /* reserved */
		result[6] = MRP.CHANNEL_ID.byteVal;
		result[4] = 0x00; /* reserved */
		result[5] = 0x00; /* reserved */
		result[9] = channelId;
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName())
			.append(" length: ").append(length)
			.append(" measurementId: ").append(measurementId.name())
			.append(" channelId: ").append(channelId);
		return sb.toString();
	}
	
	public static class MeasurementRequestBuilder extends Builder<MeasurementRequestDataUnit, MeasurementRequestBuilder> {

		private MRP measurementId;
		private byte channelId;
		
		public MeasurementRequestBuilder() {
			super(MRP.MEASUREMENT_REQ);
			length(8);
		}
		
		public MeasurementRequestBuilder measurementId(final MRP settingId) {
			this.measurementId = settingId;
			return getThis();
		}
		
		public MeasurementRequestBuilder channelId(final byte channelId) {
			this.channelId = channelId;
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
		public MeasurementRequestDataUnit buildInternal() {
			return new MeasurementRequestDataUnit(this);
		}
	}
}
