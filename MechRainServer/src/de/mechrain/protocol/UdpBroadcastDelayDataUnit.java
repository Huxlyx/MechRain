package de.mechrain.protocol;

public class UdpBroadcastDelayDataUnit extends AbstractMechRainDataUnit {
	
	private final int delay;

	protected UdpBroadcastDelayDataUnit(final UdpBroadcastDelayBuilder builder) {
		super(builder);
		this.delay = builder.delay;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[5];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = (byte) (length >> 8);
		result[4] = (byte) length;
		
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" delay: ").append(delay);
		return sb.toString();
	}
	
	public static class UdpBroadcastDelayBuilder extends Builder<UdpBroadcastDelayDataUnit, UdpBroadcastDelayBuilder> {

		private int delay;
		
		public UdpBroadcastDelayBuilder() {
			super(MRP.UDP_BROADCAST_DELAY);
			length(2);
		}
		
		public UdpBroadcastDelayBuilder delay(final int delay) {
			this.delay = delay;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
		}
		
		@Override
		protected UdpBroadcastDelayBuilder getThis() {
			return this;
		}

		@Override
		protected UdpBroadcastDelayDataUnit buildInternal() {
			return new UdpBroadcastDelayDataUnit(this);
		}
	}

}
