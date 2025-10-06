package de.mechrain.protocol;

public class UInt2DataUnit extends AbstractMechRainDataUnit {
	
	private final int value;

	protected UInt2DataUnit(final UInt2DataUnitBuilder builder) {
		super(builder);
		this.value = builder.value;
	}
	
	public int getValue() {
		return value;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[5];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) (value >> 8);
		result[4] = (byte) value;
		
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName())
		.append(' ').append(id)
		.append(" length: ").append(length)
		.append(" val: ").append(value);
		return sb.toString();
	}
	
	public static class UInt2DataUnitBuilder extends Builder<UInt2DataUnit, UInt2DataUnitBuilder> {

		private int value;
		
		public UInt2DataUnitBuilder(final MRP mrp) {
			super(mrp);
			length(2);
		}
		
		public UInt2DataUnitBuilder soilMoistureAbs(final int soilMoistureAbs) {
			this.value = soilMoistureAbs;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected UInt2DataUnitBuilder getThis() {
			return this;
		}

		@Override
		protected UInt2DataUnit buildInternal() {
			return new UInt2DataUnit(this);
		}
	}
}
