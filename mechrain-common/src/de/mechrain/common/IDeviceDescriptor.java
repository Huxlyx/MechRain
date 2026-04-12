package de.mechrain.common;

import java.util.List;

public interface IDeviceDescriptor {

	int getId();

	String getName();

	String getDescription();

	String getBuildId();

	boolean isConnected();

	List<ITaskDescriptor> getTaskDescriptors();

	List<ISinkDescriptor> getSinkDescriptors();
}
