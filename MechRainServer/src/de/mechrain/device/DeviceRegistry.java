package de.mechrain.device;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

public class DeviceRegistry implements Serializable {
	
	private static final long serialVersionUID = 705000533699185178L;
	
	private static final Logger LOG = LogManager.getLogger(Logging.DEVICE_REGISTRY);
	
	final List<Device> deviceList = Collections.synchronizedList(new ArrayList<>());
	
	public DeviceRegistry() {
		/* empty constructor for de-serialization */
	}
	
	public Optional<Device> getDevice(final int id) {
		synchronized(deviceList) {
			return deviceList.stream().filter(d -> d.getId() == id).findFirst();
		}
	}
	
	public Device getOrAddDevice(final int id) {
		synchronized(deviceList) {
			final Optional<Device> device = deviceList.stream().filter(d -> d.getId() == id).findFirst();
			if (device.isPresent()) {
				return device.get();
			} else {
				final Device newDevice = new Device(id);
				deviceList.add(newDevice);
				LOG.info(() -> "Added device " + newDevice);
				return newDevice;
			}
		}
	}
	
	public void addDevice(final Device device) {
		if (getDevice(device.getId()).isPresent()) {
			LOG.warn(() -> "Device with ID " + device.getId() + " already exists in registry");
			return;
		}
		deviceList.add(device);
		LOG.info(() -> "Added device " + device);
	}
	
	public void removeDevice(final int id) {
		for (final Iterator<Device> iterator = deviceList.iterator(); iterator.hasNext();) {
			final Device device = iterator.next();
			if (device.getId() == id) {
				iterator.remove();
				LOG.info(() -> "Removed device " + device);
				return;
			}
		}
	}
	
	public List<Device> getDevices() {
		synchronized(deviceList) {
			return Collections.unmodifiableList(deviceList);
		}
	}
}
