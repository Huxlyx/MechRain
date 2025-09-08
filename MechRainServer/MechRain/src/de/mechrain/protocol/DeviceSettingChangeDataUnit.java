package de.mechrain.protocol;

public class DeviceSettingChangeDataUnit extends AbstractMechRainDataUnit {
	
	private final MRP settingId;
	private int value;

	protected DeviceSettingChangeDataUnit(final DeviceSettingChangeBuilder builder) {
		super(builder);
		this.settingId = builder.settingId;
		this.value = builder.value;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[6];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		result[3] = settingId.byteVal;
		result[4] = (byte) (value >> 8);
		result[5] = (byte) value;
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length)
			.append(" settingId: ").append(settingId.name())
			.append(" value: ").append(value);
		return sb.toString();
	}
	
	public static class DeviceSettingChangeBuilder extends Builder<DeviceSettingChangeDataUnit, DeviceSettingChangeBuilder> {

		private MRP settingId;
		private int value;
		
		public DeviceSettingChangeBuilder() {
			super(MRP.DEVICE_SETTING_CHANGE);
			length(3);
		}
		
		public DeviceSettingChangeBuilder settingId(final MRP settingId) {
			this.settingId = settingId;
			return getThis();
		}
		
		public DeviceSettingChangeBuilder settingValue(final int value) {
			this.value = value;
			return getThis();
		}

		@Override
		protected void validate() throws DataUnitValidationException {
			if (settingId != MRP.UDP_BROADCAST_DELAY && settingId != MRP.CONNECTION_DELAY) {
				throw new DataUnitValidationException("Unsupported setting " + settingId.name());
			}
			
			if (value > UNSIGNED_SHORT_LENGTH || value < 0) {
				throw new DataUnitValidationException("Invalid value " + value);
			}
		}
		
		@Override
		protected DeviceSettingChangeBuilder getThis() {
			return this;
		}

		@Override
		public DeviceSettingChangeDataUnit buildInternal() {
			return new DeviceSettingChangeDataUnit(this);
		}
	}
}
