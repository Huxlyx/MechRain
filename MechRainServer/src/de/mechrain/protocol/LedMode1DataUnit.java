package de.mechrain.protocol;

public class LedMode1DataUnit extends AbstractMechRainDataUnit {
	
	public final int mode;

	public LedMode1DataUnit(final LedMode1Builder builder) {
		super(builder);
		this.mode = builder.mode;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[4];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) mode;
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length)
			.append(" mode: ").append(mode);
		return sb.toString();
	}
	
	public static class LedMode1Builder extends AbstractMechRainDataUnit.Builder<LedMode1DataUnit, LedMode1Builder> {
		
		public LedMode1Builder() {
			super(MRP.SET_LED_MODE_1);
			length(1);
		}

		private int mode;
		
		public LedMode1Builder mode(final int mode) {
			this.mode = mode;
			return this;
		}
		
		@Override
		protected void validate() throws DataUnitValidationException {
		}

		@Override
		protected LedMode1Builder getThis() {
			return this;
		}

		@Override
		protected LedMode1DataUnit buildInternal() {
			return new LedMode1DataUnit(this);
		}
	}
}
