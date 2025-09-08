package de.mechrain.protocol;

public class DeviceIdDataUnit extends AbstractMechRainDataUnit {
	
	final byte deviceId;

	protected DeviceIdDataUnit(final byte deviceId) {
		super(MRP.DEVICE_ID, 1);
		this.deviceId = deviceId;
	}
	
	protected DeviceIdDataUnit(final DeviceIdBuilder builder) {
		super(builder);
		this.deviceId = builder.deviceId;
	}

	@Override
	public
	byte[] toBytes() {
		final byte[] result = new byte[4];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = deviceId;
		return result;
	}
	
	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" deviceId: ").append(deviceId);
		return sb.toString();
	}
	
	public class DeviceIdBuilder extends Builder<DeviceIdDataUnit, DeviceIdBuilder> {
		private byte deviceId;
		
		public DeviceIdBuilder() {
			super(MRP.DEVICE_ID);
			length(1);
		}
		
		public DeviceIdBuilder deviceId(final byte deviceId) {
			this.deviceId = deviceId;
			return getThis();
		}

		@Override
		protected DeviceIdBuilder getThis() {
			return this;
		}

		@Override
		protected void validate() {
			/* nothing to do */
		}

		@Override
		public DeviceIdDataUnit buildInternal() {
			return new DeviceIdDataUnit(this);
		}
	}
}
