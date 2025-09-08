package de.mechrain.protocol;

import java.nio.charset.StandardCharsets;

public class StatusMessageDataUnit extends AbstractMechRainDataUnit {
	
	private final String message;

	protected StatusMessageDataUnit(final StatusMessageBuilder builder) {
		super(builder);
		this.message = builder.message;
	}
	
	public String getMessage() {
		return message;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[message.length() + 3];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		
		final byte[] messageBytes = message.getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(messageBytes, 0, result, 3, messageBytes.length);
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" length: ").append(length).append(" message: ").append(message);
		return sb.toString();
	}
	
	public static class StatusMessageBuilder extends Builder<StatusMessageDataUnit, StatusMessageBuilder> {

		private String message;
		
		public StatusMessageBuilder() {
			super(MRP.STATUS_MSG);
		}
		
		public StatusMessageBuilder message(final String message) {
			length(message.length());
			this.message = message;
			return this;
		}

		@Override
		protected void validate() throws DataUnitValidationException {
			if (message == null) {
				throw new DataUnitValidationException("No message provided");
			}
			if (message.length() > UNSIGNED_SHORT_LENGTH) {
				throw new DataUnitValidationException("Message length " + message.length() + " exceeds maximum " + UNSIGNED_SHORT_LENGTH);
			}
		}
		
		@Override
		protected StatusMessageBuilder getThis() {
			return this;
		}

		@Override
		protected StatusMessageDataUnit buildInternal() {
			return new StatusMessageDataUnit(this);
		}
	}
}
