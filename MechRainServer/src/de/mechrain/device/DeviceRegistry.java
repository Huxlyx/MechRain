package de.mechrain.device;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

public class DeviceRegistry implements Serializable {
	
	private static final long serialVersionUID = 705000533699185178L;
	
	private static final Logger LOG = LogManager.getLogger(Logging.DEVICE_REGISTRY);
	
	final Map<Integer, Device> deviceMap = new ConcurrentHashMap<>();
	
	public DeviceRegistry() {
		/* empty constructor for de-serialization */
	}
	
	public Device getDevice(final int id) {
		return deviceMap.get(id);
	}
	
	public Device getOrAddDevice(final int id) {
		if (deviceMap.containsKey(id)) {
			return deviceMap.get(id);
		} else {
			final Device newDevice = new Device(id);
			LOG.info(() -> "Added device " + newDevice);
			deviceMap.put(id, newDevice);
			return newDevice;
		}
	}
	
	public void addDevice(final Device device) {
		final Device oldDevice = deviceMap.put(device.getId(), device);
		if (oldDevice == null) {
			LOG.info(() -> "Added device " + device);
		} else {
			LOG.warn(() -> "Overrode old device " + oldDevice + " with " + device);
		}
	}
	
	public void removeDevice(final int id) {
		final Device device = deviceMap.remove(id);
		LOG.info(() -> "Removed device " + device);
	}
	
	public List<Device> getDevices() {
		return Collections.unmodifiableList(deviceMap.entrySet().stream().map(e -> e.getValue()).toList());
	}
}
