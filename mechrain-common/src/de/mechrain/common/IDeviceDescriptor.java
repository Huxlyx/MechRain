package de.mechrain.common;

import java.util.List;

/**
 * Describes a MechRain device and its current state as observed by the server.
 */
public interface IDeviceDescriptor {

	/** Returns the unique numeric identifier of this device. */
	int getId();

	/** Returns the name of this device, or {@code null} if not set. */
	String getName();

	/** Returns the human-readable description of this device, or {@code null} if not set. */
	String getDescription();

	/**
	 * Returns the firmware build identifier reported by the device,
	 * or {@code null} if the device has not yet sent it.
	 */
	String getBuildId();

	/** Returns {@code true} if the device is currently connected to the server. */
	boolean isConnected();

	/**
	 * Returns the epoch milliseconds of the most recent disconnect,
	 * or {@code 0} if this device has never connected.
	 */
	long getLastContactAt();

	/** Returns an unmodifiable view of the measurement tasks configured for this device. */
	List<ITaskDescriptor> getTaskDescriptors();

	/** Returns an unmodifiable view of the data sinks configured for this device. */
	List<ISinkDescriptor> getSinkDescriptors();
}
