package de.mechrain.protocol;

/**
 * Data unit representing a reset request.
 */
public class ResetRequestDataUnit extends AbstractMechRainDataUnit {

	protected ResetRequestDataUnit(final ResetRequestBuilder builder) {
		super(builder);
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[3];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length);
		return sb.toString();
	}

	public static class ResetRequestBuilder extends Builder<ResetRequestDataUnit, ResetRequestBuilder> {

		public ResetRequestBuilder() {
			super(MRP.RESET);
			length(0);
		}

		@Override
		protected void validate() throws DataUnitValidationException {
			/* No additional validation needed for reset request */
		}

		@Override
		protected ResetRequestBuilder getThis() {
			return this;
		}

		@Override
		protected ResetRequestDataUnit buildInternal() {
			return new ResetRequestDataUnit(this);
		}
	}

}
