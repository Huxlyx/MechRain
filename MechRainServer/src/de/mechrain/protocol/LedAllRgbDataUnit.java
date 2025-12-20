package de.mechrain.protocol;

public class LedAllRgbDataUnit extends AbstractMechRainDataUnit {
	
	public final int red;
	public final int green;
	public final int blue;

	public LedAllRgbDataUnit(final LedAllRgbBuilder builder) {
		super(builder);
		this.red = builder.red;
		this.green = builder.green;
		this.blue = builder.blue;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[6];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) red;
		result[4] = (byte) green;
		result[5] = (byte) blue;
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length)
			.append(" red: ").append(red)
			.append(" green: ").append(green)
			.append(" blue: ").append(blue);
		return sb.toString();
	}
	
	public static class LedAllRgbBuilder extends AbstractMechRainDataUnit.Builder<LedAllRgbDataUnit, LedAllRgbBuilder> {
		
		public LedAllRgbBuilder() {
			super(MRP.SET_LED_ALL_RGB);
			length(3);
		}

		private int red;
		private int green;
		private int blue;
		
		public LedAllRgbBuilder red(final int red) {
			this.red = red;
			return this;
		}
		
		public LedAllRgbBuilder green(final int green) {
			this.green = green;
			return this;
		}
		
		public LedAllRgbBuilder blue(final int blue) {
			this.blue = blue;
			return this;
		}
		
		@Override
		protected void validate() throws DataUnitValidationException {
		}

		@Override
		protected LedAllRgbBuilder getThis() {
			return this;
		}

		@Override
		protected LedAllRgbDataUnit buildInternal() {
			return new LedAllRgbDataUnit(this);
		}
	}
}
