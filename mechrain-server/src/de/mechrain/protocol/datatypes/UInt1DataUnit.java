package de.mechrain.protocol.datatypes;

import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.MRP;

public class UInt1DataUnit extends AbstractMechRainDataUnit {
	
	private final int value;

	protected UInt1DataUnit(final UInt1DataUnitBuilder builder) {
		super(builder);
		this.value = builder.value;
	}

	public int getValue() {
		return value;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[4];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) (value >> 8);
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
	
	public static class UInt1DataUnitBuilder extends Builder<UInt1DataUnit, UInt1DataUnitBuilder> {

		private int value;
		
		public UInt1DataUnitBuilder(final MRP mrp) {
			super(mrp);
			length(1);
		}
		
		public UInt1DataUnitBuilder soilMoisturePercent(final int soilMoisturePercent) {
			this.value = soilMoisturePercent;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected UInt1DataUnitBuilder getThis() {
			return this;
		}

		@Override
		protected UInt1DataUnit buildInternal() {
			return new UInt1DataUnit(this);
		}
	}
}
