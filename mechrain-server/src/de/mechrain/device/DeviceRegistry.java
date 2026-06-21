package de.mechrain.device;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.task.MeasurementTask;
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
	
/**
	 * Atomically reassigns a device to a new ID within the registry.
	 *
	 * @param oldId  The current ID of the device.
	 * @param newId  The new ID to assign.
	 * @param device The device instance whose ID will be changed.
	 */
	public void updateDeviceId(final int oldId, final int newId, final Device device) {
		synchronized(deviceList) {
			deviceList.removeIf(d -> d.getId() == oldId);
			device.setId(newId);
			deviceList.add(device);
		}
		LOG.info(() -> "Updated device ID " + oldId + " -> " + newId);
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

	/**
	 * Transfers sinks, tasks, and description from the target device to the replacing device.
	 * The target device must be registered and disconnected. Both devices keep their own IDs.
	 * After the transfer the target device's sinks and tasks are cleared and its description is
	 * updated to reflect which device replaced it.
	 *
	 * @param targetId         the ID of the device whose configuration is transferred
	 * @param replacingDevice  the device that will receive the configuration
	 * @throws IllegalArgumentException if the target device is not found or is still connected
	 */
	public void transferDevice(final int targetId, final Device replacingDevice) {
		synchronized(deviceList) {
			final Optional<Device> targetOpt = getDevice(targetId);
			if (targetOpt.isEmpty()) {
				throw new IllegalArgumentException("Target device " + targetId + " not found in registry");
			}
			final Device target = targetOpt.get();
			if (target.isConnected()) {
				throw new IllegalArgumentException("Target device " + targetId + " is still connected; disconnect it first");
			}

			for (final IDataSink sink : new ArrayList<>(target.getSinks())) {
				replacingDevice.addSink(sink);
			}
			for (final MeasurementTask task : new ArrayList<>(target.getTasks())) {
				replacingDevice.addTask(task);
			}
			if (target.getDescription() != null && replacingDevice.getDescription() == null) {
				replacingDevice.setDescription(target.getDescription());
			}

			/* Clear the replaced device and mark it so it is identifiable if it reconnects */
			for (final IDataSink sink : new ArrayList<>(target.getSinks())) {
				target.removeSink(sink);
			}
			final List<MeasurementTask> targetTasks = new ArrayList<>(target.getTasks());
			for (final MeasurementTask task : targetTasks) {
				target.removeTask(task.getId());
			}
			target.setDescription("Replaced by Device " + replacingDevice.getId());
		}
		LOG.info(() -> "Transferred configuration from Device " + targetId + " to Device " + replacingDevice.getId());
	}

	public List<Device> getDevices() {
		synchronized(deviceList) {
			return Collections.unmodifiableList(deviceList);
		}
	}
}
