package de.mechrain.protocol;

/**
 * Data unit representing an acknowledgment (ACK) in the MechRain protocol.
 */
public class AckDataUnit extends AbstractMechRainDataUnit {
	
	protected AckDataUnit(final AckBuilder builder) {
		super(builder);
	}

	@Override
	public
	byte[] toBytes() {
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
	
	public static class AckBuilder extends Builder<AckDataUnit, AckBuilder> {
		
		public AckBuilder() {
			super(MRP.ACK);
			length(0);
		}

		@Override
		protected AckBuilder getThis() {
			return this;
		}

		@Override
		protected void validate() {
			/* nothing to do */
		}

		@Override
		public AckDataUnit buildInternal() {
			return new AckDataUnit(this);
		}
	}
}
