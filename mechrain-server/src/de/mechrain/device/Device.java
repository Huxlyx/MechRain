package de.mechrain.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.common.IDeviceDescriptor;
import de.mechrain.common.ISinkDescriptor;
import de.mechrain.common.ITaskDescriptor;
import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.task.ITask;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.AckDataUnit;
import de.mechrain.protocol.DataUnitFactory;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.HeartbeatDataUnit;
import de.mechrain.protocol.HeartbeatDataUnit.HeartbeatBuilder;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.MeasurementRequestDataUnit;
import de.mechrain.protocol.datatypes.FloatDataUnit;
import de.mechrain.protocol.datatypes.TextDataUnit;
import de.mechrain.protocol.datatypes.UInt1DataUnit;
import de.mechrain.protocol.datatypes.UInt2DataUnit;
import de.mechrain.util.Util;

public class Device implements IDeviceDescriptor, Serializable {

	private static final long serialVersionUID = -3497448345309413749L;
	private static final Logger LOG_DATA = LogManager.getLogger(Logging.DATA);
	private static final Logger LOG = LogManager.getLogger(Logging.DEVICE);

	/** Disconnect the device proactively when the request queue reaches this size (half of capacity 20). */
	private static final int QUEUE_DISCONNECT_THRESHOLD = 10;

	private final Object lifecycleLock = new Object();

	private transient Socket socket;
	private transient ReadThread readThread;
	private transient RequestThread requestThread;
	private transient volatile boolean connected;
	private transient volatile boolean isDisconnecting;
	private transient List<Timer> timers = new CopyOnWriteArrayList<>();
	private transient BlockingQueue<AbstractMechRainDataUnit> requests = new ArrayBlockingQueue<>(20, true);
	private transient Timer heartbeatTimer;
	private transient DeviceMetrics metrics = new DeviceMetrics();

	private List<IDataSink> sinks = new CopyOnWriteArrayList<>();
	private List<MeasurementTask> tasks = new CopyOnWriteArrayList<>();
	
	/** Maps task IDs to their corresponding timers */
	private transient Map<Integer, Timer> taskTimers = new ConcurrentHashMap<>();
	
	private String name;
	private String description;
	private String buildId;
	private int timeout;

	private int id;
	/** Epoch millis of the most recent disconnect; 0 if this device has never been connected. */
	private long lastContactAt;

	/** No-arg constructor required for Gson/Java de-serialization. Do not call directly. */
	public Device() {
		/* empty constructor for de-serialization */
	}

	/**
	 * Creates a new device with the given ID and a default socket timeout of 70 seconds.
	 *
	 * @param id the unique numeric identifier for this device
	 */
	public Device(int id) {
		this.id = id;
		this.timeout = 70_000; /* default 70 seconds */
	}

	/** {@inheritDoc} */
	@Override
	public int getId() {
		return id;
	}

	/**
	 * Sets the device identifier.
	 *
	 * @param id the new device ID
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * Sets the firmware build identifier reported by the device.
	 *
	 * @param buildId the build identifier string
	 */
	public void setBuildId(final String buildId) {
		this.buildId = buildId;
	}

	/** {@inheritDoc} */
	@Override
	public String getBuildId() {
		return buildId;
	}

	/**
	 * Sets the socket read timeout in milliseconds.
	 * Applies on the next {@link #connect} call; does not affect already-open sockets.
	 *
	 * @param timeout read timeout in milliseconds
	 */
	public void setTimeout(final int timeout) {
		// TODO: provide option to change timeout on active connections
		this.timeout = timeout;
	}

	/**
	 * Establishes a live connection for this device.
	 * Starts the read and request threads, connects all registered sinks,
	 * and schedules measurement or heartbeat timers as appropriate.
	 *
	 * @param socket the accepted client socket
	 * @param is     the socket input stream
	 * @param os     the socket output stream
	 * @throws SocketException if configuring the socket fails
	 */
	public void connect(final Socket socket, final InputStream is, final OutputStream os) throws SocketException {
		if (connected) {
			LOG.error("Device already connected");
		} else {
			if (metrics == null) {
				metrics = new DeviceMetrics();
			}
			this.socket = socket;
			/* Enable TCP keepalive (OS-level) and set a reasonable SO_TIMEOUT so read() can
			 * detect network failures */
			socket.setKeepAlive(true);
			socket.setSoTimeout(timeout);
			this.connected = true;
			this.readThread = new ReadThread(is, this);
			readThread.setName("ReadThread(" + id + ")");
			readThread.start();
			this.requestThread = new RequestThread(os, this, requests);
			requestThread.setName("RequestThread(" + id + ")");
			requestThread.start();
			for (final IDataSink sink : sinks) {
				sink.connect();
			}
			addTimers();
			
			if (tasks.isEmpty()) {
				heartbeatTimer = new Timer("Device " + id + " Heartbeat Timer");
				heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						HeartbeatDataUnit heartbeat;
						try {
							heartbeat = new HeartbeatBuilder().build();
							queueRequest(heartbeat);
						} catch (final DataUnitValidationException e) {
							LOG.error("Error building heartbeat for device " + id, e);
						}
					}
				}, 60_000, 60_000); /* every 60 seconds */
				LOG.info(() -> "Started heartbeat timer (Device " + id + ")");
			}
		}
	}

	/**
	 * Disconnects the device.
	 * Cancels timers, closes the socket, waits for I/O threads to exit,
	 * and disconnects all sinks. Records {@link #lastContactAt}.
	 * Safe to call from either I/O thread or any external thread.
	 * Re-entrant: a second concurrent call is a no-op.
	 */
	public void disconnect() {
		LOG.debug(() -> "Disconnecting (Device " + id + ")");
		synchronized (lifecycleLock) {
			if (isDisconnecting || !connected) {
				return;
			}
			isDisconnecting = true;
			lastContactAt = System.currentTimeMillis();
		}
		try {
			removeTimers();
			timers.clear();
			requests.clear();
			taskTimers.clear();
			
			if (heartbeatTimer != null) {
				heartbeatTimer.cancel();
				heartbeatTimer.purge();
				heartbeatTimer = null;
				LOG.debug(() -> "Stopped heartbeat timer (Device " + id + ")");
			}
			
			try {
				socket.close();
			} catch (final IOException e) {
				LOG.error("I/O Error closing socket", e);
			}
			readThread.end();
			if (readThread.isAlive() && Thread.currentThread() != readThread) {
				readThread.interrupt();
				try {
					readThread.join(5000); /* Wait up to 5 seconds for clean shutdown */
				} catch (final InterruptedException e) {
					/* Expected during shutdown - don't propagate interrupt */
					LOG.warn(() -> "Read thread did not exit cleanly (Device " + id + ")");
				}
			}
			requestThread.end();
			if (requestThread.isAlive() && Thread.currentThread() != requestThread) {
				requestThread.interrupt();
				try {
					requestThread.join(5000); /* Wait up to 5 seconds for clean shutdown */
				} catch (final InterruptedException e) {
					/* Expected during shutdown - don't propagate interrupt */
					LOG.warn(() -> "Request thread did not exit cleanly (Device " + id + ")");
				}
			}
			for (final IDataSink sink : sinks) {
				sink.disconnect();
			}
		} finally {
			synchronized (lifecycleLock) {
				connected = false;
				isDisconnecting = false;
			}
		}
		LOG.info(() -> "Disconnected (Device " + id + ")");
	}

	private void addTimers() {
		long initialDelayMs = 0;
		for (final ITask task : tasks) {
			addTimer(task, initialDelayMs);
			initialDelayMs += 200;
		}
	}

	/**
	 * Adds and immediately starts a measurement timer for the given task.
	 *
	 * @param task the task to schedule
	 */
	public void addTimer(final ITask task) {
		addTimer(task, 0);
	}

	private void addTimer(final ITask task, final long initialDelayMs) {
		if (task instanceof MeasurementTask mt) {
			final Timer timer = new Timer("Device " + id + " Task " + task.getId());
			final long periodMs = mt.isAdaptive() ? mt.getMinIntervalMs() : mt.getTimeUnit().toMillis(mt.getInterval());
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					final int queueSize = requests.size();
					if (queueSize >= QUEUE_DISCONNECT_THRESHOLD) {
						LOG.warn(() -> "Request queue for device " + id + " (" + description + ") has " + queueSize
								+ " items — device appears unresponsive, disconnecting");
						final Thread t = new Thread(() -> disconnect(), "Device-" + id + "-DisconnectOnQueueFull");
						t.setDaemon(true);
						t.start();
						return;
					}
					if ( ! mt.queueTask(requests)) {
						LOG.error(() -> "Request queue full for device " + id + " (" + description + "), dropped task: " + mt);
					}
				}
			}, initialDelayMs, periodMs);
			LOG.info(() -> "Started new timer for task " + task);
			timers.add(timer);
			taskTimers.put(task.getId(), timer);
		} else {
			LOG.error(() -> "Unknown task " + task + " " + task.getClass().getSimpleName());
		}
	}

	private void removeTimers() {
		for (final Timer timer : timers) {
			timer.cancel();
			timer.purge();
		}
		timers.clear();
		LOG.info(() -> "Timers removed (Device " + id + ")");
	}

	/**
	 * Cancels all running measurement timers and restarts them from scratch.
	 * Used after a task interval change.
	 */
	public void resetTimers() {
		removeTimers();
		addTimers();
	}

	/**
	 * Registers a data sink with this device and connects it immediately if the device is online.
	 *
	 * @param sink the sink to add
	 */
	public void addSink(final IDataSink sink) {
		sinks.add(sink);
		if (connected) {
			sink.connect();
		}
	}

	/**
	 * Removes the given data sink from this device.
	 *
	 * @param sink the sink to remove
	 */
	public void removeSink(final IDataSink sink) {
		sinks.remove(sink);
	}

	/**
	 * Removes the data sink with the given ID from this device.
	 *
	 * @param sinkId the ID of the sink to remove
	 */
	public void removeSink(final int sinkId) {
		sinks.removeIf(sink -> sink.getId() == sinkId);
	}

	/**
	 * Returns the list of data sinks configured for this device.
	 *
	 * @return the live sink list (modifications are reflected immediately)
	 */
	public List<IDataSink> getSinks() {
		return sinks;
	}
	
	/** {@inheritDoc} */
	@Override
	public List<ISinkDescriptor> getSinkDescriptors() {
		return sinks.stream().map(s -> (ISinkDescriptor) s).toList();
	}

	/**
	 * Adds a measurement task to this device.
	 * Cancels the heartbeat timer if one was running, since tasks replace heartbeats.
	 *
	 * @param task the task to add
	 */
	public void addTask(final MeasurementTask task) {
		if (heartbeatTimer != null) {
			heartbeatTimer.cancel();
			heartbeatTimer.purge();
			heartbeatTimer = null;
			LOG.info(() -> "Stopped heartbeat timer (Device " + id + ")");
		}
		tasks.add(task);
	}
	
	/**
	 * Returns the list of measurement tasks configured for this device.
	 *
	 * @return the live task list
	 */
	public List<MeasurementTask> getTasks() {
		return tasks;
	}
	
	/** {@inheritDoc} */
	@Override
	public List<ITaskDescriptor> getTaskDescriptors() {
		return tasks.stream().map(t -> (ITaskDescriptor) t).toList();
	}

	/**
	 * Removes the given task and cancels its associated timer.
	 *
	 * @param task the task to remove
	 */
	public void removeTask(final ITask task) {
		removeTask(task.getId());
	}

	/**
	 * Removes the task with the given ID and cancels its associated timer.
	 * Any pending requests for that measurement type are also cleared from the queue.
	 *
	 * @param taskId the ID of the task to remove
	 */
	public void removeTask(final int taskId) {
		tasks.stream()
			.filter(t -> t.getId() == taskId)
			.findFirst()
			.ifPresent(task -> {
				tasks.remove(task);
				final Timer removedTimer = taskTimers.remove(task.getId());
				if (removedTimer != null) {
					removedTimer.cancel();
					removedTimer.purge();
					timers.remove(removedTimer);
					LOG.info(() -> "Cancelled timer for task " + task);
				}
				final MRP measurement = task.getMeasurement();
				final long count = requests.stream()
					.filter(du -> du instanceof MeasurementRequestDataUnit mreq && mreq.getId() == measurement)
					.count();
				requests.removeIf(du -> du instanceof MeasurementRequestDataUnit mreq && mreq.getId() == measurement);
				if (count > 0) {
					LOG.info(() -> "Cleared " + count + " pending request(s) for removed task " + task);
				}
			});
	}

	/**
	 * Returns the task at the given list index.
	 *
	 * @param idx zero-based index into the task list
	 * @return the task at that index
	 * @throws IndexOutOfBoundsException if {@code idx} is out of range
	 */
	public ITask getTask(final int idx) {
		return tasks.get(idx);
	}

	private void notifyMeasurement(final MRP type, final double value) {
		for (final MeasurementTask task : tasks) {
			if (task.getMeasurement() == type && task.isAdaptive()) {
				task.onValueReceived(value);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Sets the human-readable name for this device.
	 *
	 * @param name the device name, or {@code null} to clear it
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/** {@inheritDoc} */
	@Override
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description for this device.
	 *
	 * @param description the device description, or {@code null} to clear it
	 */
	public void setDescription(final String description) {
		this.description = description;
	}

	/**
	 * Returns the epoch milliseconds of the most recent disconnect,
	 * or {@code 0} if this device has never connected.
	 *
	 * @return epoch millis of last disconnect, or 0
	 */
	@Override
	public long getLastContactAt() {
		return lastContactAt;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isConnected() {
		return connected;
	}

	/**
	 * Enqueues a data unit to be sent to the device.
	 * If the queue is full the request is silently dropped and a warning is logged.
	 *
	 * @param request the data unit to enqueue
	 */
	public void queueRequest(final AbstractMechRainDataUnit request) {
		if (!requests.offer(request)) {
			LOG.warn(() -> "Request queue full for device " + id + ", dropping: " + request);
		}
	}

	/**
	 * Returns the live metrics object tracking sent/received byte counts for this device.
	 *
	 * @return the device metrics
	 */
	public DeviceMetrics getMetrics() {
		return metrics;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Device ").append(id).append(' ').append(name != null ? name : "").append(' ')
				.append(description != null ? description : "").append(' ')
				.append(connected ? "<connected>" : "<disconnected>").append(' ').append("sinks: ").append(sinks.size())
				.append(' ').append("tasks: ").append(tasks.size());
		return sb.toString();
	}

	private static class RequestThread extends Thread {

		private final OutputStream os;
		private final Device device;
		private final BlockingQueue<AbstractMechRainDataUnit> requests;
		private boolean run = true;

		private RequestThread(final OutputStream os, final Device device,
				final BlockingQueue<AbstractMechRainDataUnit> requests) {
			this.os = os;
			this.device = device;
			this.requests = requests;
		}

		public void end() {
			this.run = false;
		}

		@Override
		public void run() {
			while (run) {
				try {
					final AbstractMechRainDataUnit poll = requests.poll(60, TimeUnit.SECONDS);
					if (poll != null) {
						final byte[] bytes = poll.toBytes();
						LOG_DATA.debug(() -> "Sending data unit (Device " + device.id + ") " + poll);
						LOG_DATA.trace(() -> "Data: " + Util.BYTES2HEX(bytes));
						os.write(bytes);
						os.flush();
						device.metrics.recordSent(bytes.length);
					}
				} catch (final InterruptedException e) {
					LOG.debug(() -> "Interrupted (Device " + device.id + ")", e);
					run = false;
					Thread.currentThread().interrupt();
				} catch (final IOException e) {
					LOG.error(() -> "Error sending data unit (Device " + device.id + ")", e);
					run = false;
				}
			}
			LOG.info("Request thread ended (Device " + device.id + ")");
			requests.clear();
			device.disconnect();
		}
	}

	private static class ReadThread extends Thread {

		private final InputStream is;
		private final Device device;
		private boolean run = true;

		private ReadThread(final InputStream is, final Device device) {
			this.is = is;
			this.device = device;
		}

		public void end() {
			this.run = false;
		}

		@Override
		public void run() {
			final byte[] header = new byte[3];
			final DataUnitFactory duf = new DataUnitFactory();
			int timeoutCounter = 0;
			final int maxTimeouts = 3; // after 3 consecutive timeouts treat as disconnected
			try {
				while (run) {
					int readSoFar = 0;
					try {
						while (readSoFar < header.length) {
							final int bytesRead = is.read(header, readSoFar, header.length - readSoFar);
							if (bytesRead == -1) {
								// EOF - remote closed connection
								LOG.info("Input stream no longer available");
								run = false;
								break;
							}
							readSoFar += bytesRead;
						}
						/* if we got here with full header, reset timeout counter */
						if (readSoFar == header.length) {
							timeoutCounter = 0;
						}
					} catch (final SocketTimeoutException ste) {
						/* read timed out - increment counter and possibly treat as disconnected */
						++timeoutCounter;
						LOG.debug("Socket read timed out (Device " + device.id + ") - count " + timeoutCounter, ste);
						if (timeoutCounter < maxTimeouts) {
							/* try again */
							continue;
						} else {
							LOG.warn("Connection appears dead after " + timeoutCounter + " read timeouts (Device "
									+ device.id + ")");
							run = false;
							break;
						}
					}
					if (!run) {
						break;
					}
					if (readSoFar != header.length) {
						/* we already logged EOF above; but safeguard */
						LOG_DATA.error("Invalid number of header bytes " + readSoFar);
						run = false;
						break;
					}
					LOG_DATA.trace(() -> "Header: " + Util.BYTES2HEX(header, 3));

					try {
						final AbstractMechRainDataUnit dataUnit = duf.getDataUnit(header, is);
					/* header = 1 byte ID + 2 bytes payload length (big-endian) */
					final int payloadLen = ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
					device.metrics.recordReceived(3 + payloadLen);
						if (dataUnit instanceof TextDataUnit text) {
							if (text.getId() == MRP.STATUS_MSG) {
								LOG_DATA.info(() -> "Received status (Device " + device.id + ") " + text.getText());
							} else if (text.getId() == MRP.ERROR) {
								LOG_DATA.error(() -> "Received error (Device " + device.id + ") " + text.getText());
							} else if (text.getId() == MRP.BUILD_ID) {
								LOG_DATA.info(() -> "Received build ID (Device " + device.id + ") " + text.getText());
								device.setBuildId(text.getText());
							} else {
								LOG_DATA.error(() -> "Unknown Message type " + text.getId() + " " + text);
							}
						} else if (dataUnit instanceof AckDataUnit) {
							LOG_DATA.debug(() -> "Received ACK (Device " + device.id + ")");
						} else if (dataUnit instanceof HeartbeatDataUnit) {
							LOG_DATA.debug(() -> "Received Heartbeat (Device " + device.id + ")");
						} else {
							LOG_DATA.debug(() -> "Received data unit (Device " + device.id + ") - " + dataUnit);
							for (final IDataSink sink : device.getSinks()) {
								if (sink.isAvailable()) {
									sink.handleDataUnit(dataUnit);
								} else {
									LOG.warn(() -> "Sink " + sink + " unavailable");
								}
							}
							if (dataUnit instanceof FloatDataUnit fdu) {
								device.notifyMeasurement(dataUnit.getId(), fdu.getValue());
							} else if (dataUnit instanceof UInt1DataUnit u1du) {
								device.notifyMeasurement(dataUnit.getId(), u1du.getValue());
							} else if (dataUnit instanceof UInt2DataUnit u2du) {
								device.notifyMeasurement(dataUnit.getId(), u2du.getValue());
							}
						}
					} catch (final DataUnitValidationException e) {
						LOG_DATA.error(() -> "Error receiving data unit (Device " + device.id + ")", e);
					}

				}
			} catch (final IOException e) {
				LOG.error("I/O Error (Device " + device.id + ")", e);
			} finally {
				try {
					is.close();
				} catch (final IOException e) {
					LOG.error("I/O Error in cleanup", e);
				}
			}
			LOG.info("Read thread ended (Device " + device.id + ")");
			device.disconnect();
		}
	}
}