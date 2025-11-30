package de.mechrain.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.sink.IDataSink;
import de.mechrain.device.task.ITask;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitFactory;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.datatypes.TextDataUnit;
import de.mechrain.util.Util;

public class Device implements Serializable {

	private static final long serialVersionUID = -3497448345309413749L;
	private static final Logger LOG_DATA = LogManager.getLogger(Logging.DATA);
	private static final Logger LOG = LogManager.getLogger(Logging.DEVICE);

	private transient Socket socket;
	private transient ReadThread readThread;
	private transient RequestThread requestThread;
	private transient boolean connected;
	private transient boolean isDisconnecting;
	private transient List<Timer> timers = new ArrayList<>();
	private transient BlockingQueue<AbstractMechRainDataUnit> requests = new ArrayBlockingQueue<>(10, true);

	private List<IDataSink> sinks = new ArrayList<>();
	private List<MeasurementTask> tasks = new ArrayList<>();
	
	/** Maps task IDs to their corresponding timers */
	private transient Map<Integer, Timer> taskTimers = new HashMap<>();
	
	private String name;
	private String description;
	private String buildId;
	private int timeout;

	private int id;

	public Device() {
		/* empty constructor for de-serialization */
	}

	public Device(int id) {
		this.id = id;
		this.timeout = 60_000; /* default 60 seconds */
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setBuildId(final String buildId) {
		this.buildId = buildId;
	}

	public String getBuildId() {
		return buildId;
	}

	public void setTimeout(final int timeout) {
		// TODO: provide option to change timeout on active connections
		this.timeout = timeout;
	}

	public void connect(final Socket socket, final InputStream is, final OutputStream os) throws SocketException {
		if (connected) {
			LOG.error("Device already connected");
		} else {
			this.socket = socket;
			/*
			 * Enable TCP keepalive (OS-level) and set a reasonable SO_TIMEOUT so read() can
			 * detect network failures
			 */
			socket.setKeepAlive(true);
			socket.setSoTimeout(timeout); // 30 seconds
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
		}
	}

	public void disconnect() {
		LOG.debug(() -> "Disconnecting (Device " + id + ")");
		if (isDisconnecting || !connected) {
			return;
		}
		try {
			isDisconnecting = true;
			removeTimers();
			timers.clear();
			requests.clear();
			taskTimers.clear();
			try {
				socket.close();
			} catch (final IOException e) {
				LOG.error("I/O Error closing socket", e);
			}
			readThread.end();
			if (readThread.isAlive() && !readThread.isInterrupted()) {
				readThread.interrupt();
				try {
					readThread.join();
				} catch (final InterruptedException e) {
					LOG.debug(() -> "Interrupted (Device " + id + ")", e);
					Thread.currentThread().interrupt();
				}
			}
			requestThread.end();
			if (requestThread.isAlive() && !requestThread.isInterrupted()) {
				requestThread.interrupt();
				try {
					requestThread.join();
				} catch (final InterruptedException e) {
					LOG.debug(() -> "Interrupted (Device " + id + ")", e);
					Thread.currentThread().interrupt();
				}
			}
			for (final IDataSink sink : sinks) {
				sink.disconnect();
			}
		} finally {
			connected = false;
			isDisconnecting = false;
		}
	}

	private void addTimers() {
		for (final ITask task : tasks) {
			addTimer(task);
		}
	}

	public void addTimer(final ITask task) {
		if (task instanceof MeasurementTask mt) {
			final Timer timer = new Timer("Device " + id + " Task " + task.getId());
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					mt.queueTask(requests);
				}
			}, 0, mt.getTimeUnit().toMillis(mt.getInterval()));
			LOG.info(() -> "Started new timer for task " + task);
			timers.add(timer);
			taskTimers.put(task.getId(), timer);
		} else {
			LOG.error(() -> "Unknown task " + task + " " + task.getClass().getSimpleName());
		}
	}

	private void removeTimers() {
		for (final Iterator<Timer> iterator = timers.iterator(); iterator.hasNext();) {
			final Timer timer = iterator.next();
			timer.cancel();
			timer.purge();
			iterator.remove();
		}
		LOG.info(() -> "Timers removed (Device " + id + ")");
	}

	public void resetTimers() {
		removeTimers();
		addTimers();
	}

	public void addSink(final IDataSink sink) {
		sinks.add(sink);
		if (connected) {
			sink.connect();
		}
	}

	public void removeSink(final IDataSink sink) {
		sinks.remove(sink);
	}

	public void removeSink(final int sinkId) {
		for (final Iterator<IDataSink> iterator = sinks.iterator(); iterator.hasNext();) {
			final IDataSink sink = iterator.next();
			if (sink.getId() == sinkId) {
				iterator.remove();
				break;
			}
		}
	}

	public List<IDataSink> getSinks() {
		return sinks;
	}

	public void addTask(final MeasurementTask task) {
		tasks.add(task);
	}
	
	public List<MeasurementTask> getTasks() {
		return tasks;
	}

	public void removeTask(final ITask task) {
		tasks.remove(task);
	}

	public void removeTask(final int taskId) {
		for (final Iterator<MeasurementTask> iterator = tasks.iterator(); iterator.hasNext();) {
			final ITask task = iterator.next();
			if (task.getId() == taskId) {
				iterator.remove();
				final Timer removedTimer = taskTimers.remove(task.getId());
				removedTimer.cancel();
				removedTimer.purge();
				break;
			}
		}
	}

	public ITask getTask(final int idx) {
		return tasks.get(idx);
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public boolean isConnected() {
		return connected;
	}

	public void queueRequest(final AbstractMechRainDataUnit request) {
		requests.add(request);
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
						LOG_DATA.debug(() -> "Sending data unit (Device " + device.id + ") " + poll);
						os.write(poll.toBytes());
						os.flush();
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
						} else {
							LOG_DATA.debug(() -> "Received data unit (Device " + device.id + ") - " + dataUnit);
							for (final IDataSink sink : device.getSinks()) {
								if (sink.isAvailable()) {
									sink.handleDataUnit(dataUnit);
								} else {
									LOG.warn(() -> "Sink " + sink + " unavailable");
								}
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