package de.mechrain.protocol;

/**
 * Exception indicating that a data unit failed validation.
 */
public class DataUnitValidationException extends Exception {

	private static final long serialVersionUID = -968025406062236204L;
	
	public DataUnitValidationException(final String message) {
		super(message);
	}
}
