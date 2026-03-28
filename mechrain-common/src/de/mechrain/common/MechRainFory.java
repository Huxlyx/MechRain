package de.mechrain.common;

import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

import de.mechrain.common.beans.AddSinkRequest;
import de.mechrain.common.beans.AddTaskRequest;
import de.mechrain.common.beans.ConsoleRequest;
import de.mechrain.common.beans.ConsoleResponse;
import de.mechrain.common.beans.DeviceConfigRequest;
import de.mechrain.common.beans.DeviceConfigResponse;
import de.mechrain.common.beans.DeviceListRequest;
import de.mechrain.common.beans.DeviceListResponse;
import de.mechrain.common.beans.DeviceResetRequest;
import de.mechrain.common.beans.EndConfigureDeviceRequest;
import de.mechrain.common.beans.ICliBean;
import de.mechrain.common.beans.LogEvent;
import de.mechrain.common.beans.RemoveDeviceRequest;
import de.mechrain.common.beans.RemoveSinkRequest;
import de.mechrain.common.beans.RemoveTaskRequest;
import de.mechrain.common.beans.SetDescriptionRequest;
import de.mechrain.common.beans.SetIdRequest;
import de.mechrain.common.beans.SetLedAllRgbRequest;
import de.mechrain.common.beans.SetLedMode1Request;
import de.mechrain.common.beans.SetNumPixelsRequest;
import de.mechrain.common.beans.SwitchToNonInteractiveRequest;
import de.mechrain.common.beans.DeviceListResponse.DeviceData;
import de.mechrain.common.beans.DeviceMetricsData;
import de.mechrain.common.beans.MetricsRequest;
import de.mechrain.common.beans.MetricsResponse;
import de.mechrain.common.beans.ServerInfoResponse;

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
		INSTANCE.register(SetNumPixelsRequest.class);
		INSTANCE.register(SetLedAllRgbRequest.class);
		INSTANCE.register(SetLedMode1Request.class);
		INSTANCE.register(MetricsRequest.class);
		INSTANCE.register(MetricsResponse.class);
		INSTANCE.register(DeviceMetricsData.class);
		INSTANCE.register(ServerInfoResponse.class);
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
