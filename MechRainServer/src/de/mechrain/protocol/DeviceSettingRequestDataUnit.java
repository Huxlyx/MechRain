package de.mechrain.protocol;

public class DeviceSettingRequestDataUnit extends AbstractMechRainDataUnit {
	
	private final MRP settingId;

	protected DeviceSettingRequestDataUnit(final DeviceSettingRequestBuilder builder) {
		super(builder);
		this.settingId = builder.settingId;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[6];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = settingId.byteVal;
		result[4] = 0x00; /* reserved */
		result[5] = 0x00; /* reserved */
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" settingId: ").append(settingId.name());
		return sb.toString();
	}
	
	public static class DeviceSettingRequestBuilder extends Builder<DeviceSettingRequestDataUnit, DeviceSettingRequestBuilder> {

		private MRP settingId;
		
		public DeviceSettingRequestBuilder() {
			super(MRP.DEVICE_SETTING_REQ);
			length(3);
		}
		
		public DeviceSettingRequestBuilder settingId(final MRP settingId) {
			this.settingId = settingId;
			return getThis();
		}

		@Override
		protected void validate() throws DataUnitValidationException {
			if (settingId != MRP.UDP_BROADCAST_DELAY && settingId != MRP.CONNECTION_DELAY) {
				throw new DataUnitValidationException("Unsupported setting " + settingId.name());
			}
		}
		
		@Override
		protected DeviceSettingRequestBuilder getThis() {
			return this;
		}

		@Override
		protected DeviceSettingRequestDataUnit buildInternal() {
			return new DeviceSettingRequestDataUnit(this);
		}
	}
}
