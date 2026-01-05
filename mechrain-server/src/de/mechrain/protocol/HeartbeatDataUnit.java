package de.mechrain.protocol;

/**
 * Data unit representing a heartbeat in the MechRain protocol.
 */
public class HeartbeatDataUnit extends AbstractMechRainDataUnit {
	
	protected HeartbeatDataUnit(final HeartbeatBuilder builder) {
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
	
	public static class HeartbeatBuilder extends Builder<HeartbeatDataUnit, HeartbeatBuilder> {
		
		public HeartbeatBuilder() {
			super(MRP.HEARTBEAT);
			length(0);
		}

		@Override
		protected HeartbeatBuilder getThis() {
			return this;
		}

		@Override
		protected void validate() {
			/* nothing to do */
		}

		@Override
		public HeartbeatDataUnit buildInternal() {
			return new HeartbeatDataUnit(this);
		}
	}
}
