package de.mechrain.protocol;

import java.nio.charset.StandardCharsets;

public class TextDataUnit extends AbstractMechRainDataUnit {
	
	private final String text;

	protected TextDataUnit(final TextDataUnitBuilder builder) {
		super(builder);
		this.text = builder.message;
	}
	
	public String getText() {
		return text;
	}

	@Override
	public byte[] toBytes() {
		final byte[] result = new byte[text.length() + 3];
		result[0] = id.byteVal;
		result[1] = lengthBytes[0];
		result[2] = lengthBytes[1];
		
		final byte[] messageBytes = text.getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(messageBytes, 0, result, 3, messageBytes.length);
		return result;
	}

	@Override
	protected String toStringInternal() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName())
		.append(' ').append(id)
		.append(" length: ").append(length)
		.append(" text: ").append(text);
		return sb.toString();
	}
	
	public static class TextDataUnitBuilder extends Builder<TextDataUnit, TextDataUnitBuilder> {

		private String message;
		
		public TextDataUnitBuilder(final MRP mrp) {
			super(mrp);
		}
		
		public TextDataUnitBuilder message(final String message) {
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
		protected TextDataUnitBuilder getThis() {
			return this;
		}

		@Override
		protected TextDataUnit buildInternal() {
			return new TextDataUnit(this);
		}
	}
}
