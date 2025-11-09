package de.mechrain.cmdline;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.ConfigDeviceRequest;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.DeviceResetRequest;
import de.mechrain.cmdline.beans.EndConfigureDeviceRequest;
import de.mechrain.cmdline.beans.RemoveDeviceRequest;
import de.mechrain.cmdline.beans.RemoveSinkRequest;
import de.mechrain.cmdline.beans.RemoveTaskRequest;
import de.mechrain.cmdline.beans.SetDescriptionRequest;
import de.mechrain.cmdline.beans.SetIdRequest;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.cmdline.beans.DeviceListResponse.DeviceData;
import de.mechrain.log.LogEvent;

public class MechRainFory {
	
	public static final ThreadSafeFory INSTANCE = Fory.builder()
			.withName("MechRainCmdlineFory")
			.withLanguage(Language.JAVA)
			.requireClassRegistration(true)
			.buildThreadSafeFory();
	
	static {
		INSTANCE.register(AddSinkRequest.class);
		INSTANCE.register(AddTaskRequest.class);
		INSTANCE.register(SetIdRequest.class);
		INSTANCE.register(SetDescriptionRequest.class);
		INSTANCE.register(DeviceResetRequest.class);
		INSTANCE.register(ConsoleRequest.class);
		INSTANCE.register(ConsoleResponse.class);
		INSTANCE.register(DeviceListRequest.class);
		INSTANCE.register(DeviceData.class);
		INSTANCE.register(DeviceListResponse.class);
		INSTANCE.register(ConfigDeviceRequest.class);
		INSTANCE.register(SwitchToNonInteractiveRequest.class);
		INSTANCE.register(LogEvent.class);
		INSTANCE.register(RemoveSinkRequest.class);
		INSTANCE.register(RemoveTaskRequest.class);
		INSTANCE.register(RemoveDeviceRequest.class);
		INSTANCE.register(EndConfigureDeviceRequest.class);
	}

}
