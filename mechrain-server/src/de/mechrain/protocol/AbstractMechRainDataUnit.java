package de.mechrain.protocol;

/**
 * Abstract base class for MechRain data units.
 * A data unit consists of an ID and length (possibly 0) forming the header and data bytes.
 * <pre>
 * |<-  HEADER ->|
 * +----+--------+---------+
 * | ID | LENGTH | PAYLOAD |
 * +----+--------+---------+
 * </pre>
 */
public abstract class AbstractMechRainDataUnit {
	
	protected static final int UNSIGNED_SHORT_LENGTH = 65_536;
	
	protected final MRP id;
	protected final int length;
	protected final byte[] lengthBytes;

	/* where a received data unit came from, set by the receiving device (not part of the wire format) */
	private Integer sourceDeviceId;
	private Integer sourceChannelId;

	protected AbstractMechRainDataUnit(final MRP id, final int length) {
		this.id = id;
		this.length = length;
		this.lengthBytes = new byte[] {(byte) (length >> 8), (byte) length};
	}
	
	protected AbstractMechRainDataUnit(final Builder<?, ?> builder) {
		this.id = builder.id;
		this.length = builder.length;
		this.lengthBytes = new byte[] {(byte) (length >> 8), (byte) length};
	}
	
	@Override
	public String toString() {
		return toStringInternal();
	}
	
	public abstract byte[] toBytes();
	
	protected abstract String toStringInternal();
	
	public MRP getId() {
		return id;
	}

	/**
	 * Records the origin of a received data unit.
	 *
	 * @param deviceId  the ID of the device the data unit was received from
	 * @param channelId the channel of the request it answers, or {@code null} if not channeled
	 */
	public void setSource(final Integer deviceId, final Integer channelId) {
		this.sourceDeviceId = deviceId;
		this.sourceChannelId = channelId;
	}

	/** @return the ID of the device the data unit was received from, or {@code null} if unknown */
	public Integer getSourceDeviceId() {
		return sourceDeviceId;
	}

	/** @return the channel of the request this data unit answers, or {@code null} if not channeled */
	public Integer getSourceChannelId() {
		return sourceChannelId;
	}

	public abstract static class Builder<D extends AbstractMechRainDataUnit, B extends Builder<D, B>> {
		private final MRP id;
		private int length;
		protected boolean lengthSet;
		
		protected Builder(final MRP id) {
			this.id = id;
		}
		
		protected B length (final int length) {
			this.length = length;
			lengthSet = true;
			return getThis();
		}
		
		public D build() throws DataUnitValidationException {
			if ( ! lengthSet) {
				throw new DataUnitValidationException("No lenght set");
			}
			validate();
			return buildInternal();
		}
		
		protected abstract void validate() throws DataUnitValidationException;
		
		protected abstract B getThis();
		
		protected abstract D buildInternal();
	}
}
