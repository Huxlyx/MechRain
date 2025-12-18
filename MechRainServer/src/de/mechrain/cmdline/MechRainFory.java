package de.mechrain.cmdline;

import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.DeviceConfigRequest;
import de.mechrain.cmdline.beans.DeviceConfigResponse;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.DeviceResetRequest;
import de.mechrain.cmdline.beans.EndConfigureDeviceRequest;
import de.mechrain.cmdline.beans.ICliBean;
import de.mechrain.cmdline.beans.LogEvent;
import de.mechrain.cmdline.beans.RemoveDeviceRequest;
import de.mechrain.cmdline.beans.RemoveSinkRequest;
import de.mechrain.cmdline.beans.RemoveTaskRequest;
import de.mechrain.cmdline.beans.SetDescriptionRequest;
import de.mechrain.cmdline.beans.SetIdRequest;
import de.mechrain.cmdline.beans.SetNumPixelsRequest;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.cmdline.beans.DeviceListResponse.DeviceData;

public class MechRainFory {
	
	private static final ThreadSafeFory INSTANCE = Fory.builder()
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
		INSTANCE.register(DeviceConfigRequest.class);
		INSTANCE.register(DeviceConfigResponse.class);
		INSTANCE.register(SwitchToNonInteractiveRequest.class);
		INSTANCE.register(LogEvent.class);
		INSTANCE.register(RemoveSinkRequest.class);
		INSTANCE.register(RemoveTaskRequest.class);
		INSTANCE.register(RemoveDeviceRequest.class);
		INSTANCE.register(EndConfigureDeviceRequest.class);
		INSTANCE.register(DeviceResetRequest.class);
		INSTANCE.register(SetNumPixelsRequest.class);
	}
	
	/**
	 * Serializes the given CLI bean and sends it over the provided DataOutputStream.
	 * 
	 * @param cliBean the CLI bean to serialize
	 * @param dos     the DataOutputStream to send the serialized data
	 * @throws IOException if an I/O error occurs
	 */
	public static void serializeAndSend(final ICliBean cliBean, final DataOutputStream dos) throws IOException {
		final byte[] data = INSTANCE.serialize(cliBean);
		dos.writeInt(data.length);
		dos.write(data);
		dos.flush();
	}
	
	/**
	 * Deserializes the given byte array into an ICliBean.
	 * 
	 * @param data the byte array to deserialize
	 * @return the deserialized ICliBean
	 */
	public static ICliBean deserialize(final byte[] data) {
		return (ICliBean) INSTANCE.deserialize(data);
	}

}
