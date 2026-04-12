package de.mechrain.common;

/**
 * Describes the configuration of a measurement task for use in CLI data transfer.
 */
public interface ITaskDescriptor extends IIdProvider {

	String getMeasurementName();

	int getInterval();

	String getTimeUnitName();

	/** Returns the channel ID for channeled tasks, or {@code null} if the task is not channeled. */
	Integer getChannelId();

	boolean isAdaptive();

	long getMinIntervalMs();

	double getChangeThreshold();

	double getSpeedupFactor();

	double getSlowdownFactor();
}
