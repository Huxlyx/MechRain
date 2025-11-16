package de.mechrain.device;

/**
 * Functional interface for providing an ID.
 */
@FunctionalInterface
public interface IIdProvider {
	
	/**
	 * Gets the ID.
	 *
	 * @return the ID
	 */
	int getId();

}
