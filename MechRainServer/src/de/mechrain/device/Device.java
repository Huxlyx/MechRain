package de.mechrain.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.device.task.ITask;
import de.mechrain.device.task.MeasurementTask;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitFactory;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.ErrorDataUnit;
import de.mechrain.protocol.StatusMessageDataUnit;
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
	private transient List<Timer> timers;
	private transient BlockingQueue<AbstractMechRainDataUnit> requests = new ArrayBlockingQueue<>(10, true);
	
	private List<DataSink> sinks = new ArrayList<>();
	private List<MeasurementTask> tasks = new ArrayList<>();
	private String name;
	private String description;
	
	private int id;
	
	public Device() {
		/* empty constructor for de-serialization */
	}
	
	public Device(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	public void connect(final Socket socket, final InputStream is, final OutputStream os) {
		if (connected) {
			LOG.error("Device already connected");
		} else {
			this.socket = socket;
			this.connected = true;
			this.readThread = new ReadThread(is, this);
			readThread.start();
			this.requestThread = new RequestThread(os, this, requests);
			requestThread.start();
			if (timers == null) {
				timers = new ArrayList<>();
			}
			addTimers();
		}
	}
	
	public synchronized void disconnect() {
		if (isDisconnecting || ! connected) {
			return;
		}
		try {
			isDisconnecting = true;
			removeTimers();
			timers.clear();
			readThread.end();
			if (readThread.isAlive() && ! readThread.isInterrupted()) {
				readThread.interrupt();
				try {
					readThread.join();
				} catch (final InterruptedException e) {
					LOG.debug(() -> "Interrupted (Device " + id + ")", e);
					Thread.currentThread().interrupt();
				}
			}
			requestThread.end();
			if (requestThread.isAlive() && ! requestThread.isInterrupted()) {
				requestThread.interrupt();
				try {
					requestThread.join();
				} catch (final InterruptedException e) {
					LOG.debug(() -> "Interrupted (Device " + id + ")", e);
					Thread.currentThread().interrupt();
				}
			}
			try {
				socket.close();
			} catch (final IOException e) {
				LOG.error("I/O Error closing socket", e);
			}
		} finally {
			connected = false;
			isDisconnecting = false;
		}
	}
	
	private void addTimers() {
		for (final ITask task : tasks) {
			if (task instanceof MeasurementTask mt) {
				final Timer timer = new Timer();
				timer.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						mt.queueTask(requests);
					}
				}, 0, mt.getTimeUnit().toMillis(mt.getInterval()));
				LOG.info(() -> "Started new timer for task " + task);
				timers.add(timer);
			} else {
				LOG.error(() -> "Unknown task " + task + " " + task.getClass().getSimpleName());
			}
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
	
	public void addSink(final DataSink sink) {
		sinks.add(sink);
	}
	
	public void removeSink(final DataSink sink) {
		sinks.remove(sink);
	}
	
	public void addTask(final MeasurementTask task) {
		tasks.add(task);
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
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Device ").append(id)
		.append(' ').append(name != null ? name : "")
		.append(' ').append(description != null ? description : "")
		.append(' ').append(connected ? "<connected>" : "<disconnected>");
		return sb.toString();
	}
	
	private static class RequestThread extends Thread {

		private final OutputStream os;
		private final Device device;
		private final BlockingQueue<AbstractMechRainDataUnit> requests;
		private boolean run = true;

		private RequestThread(final OutputStream os, final Device device, final BlockingQueue<AbstractMechRainDataUnit> requests) {
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
						LOG_DATA.debug(() -> "Sending data unit (Device " + device.id + ") "  + poll);
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
			try {
				while (run)
				{
					final int headerLen = is.read(header);
					if (headerLen != 3) {
						if (headerLen == -1) {
							LOG.info("Input stream no longer available");
							run = false;
							break;
						} else {
							LOG_DATA.error(() -> "Invalid number of header bytes " + headerLen);
							return;
						}
					}
					LOG_DATA.trace(() -> "Header: " + Util.BYTES2HEX(header, 3));
					
					try {
						final AbstractMechRainDataUnit dataUnit = duf.getDataUnit(header, is);
						if (dataUnit instanceof StatusMessageDataUnit status) {
							LOG_DATA.info(() -> status.getMessage());
						} else if (dataUnit instanceof ErrorDataUnit error) {
							LOG_DATA.error(() -> "Received error (Device " + device.id + ") " + error.getMessage());
						} else {
							LOG_DATA.debug(() -> "Received data unit (Device " + device.id + ") - " + dataUnit);
						}
					} catch (final DataUnitValidationException e) {
						LOG_DATA.error(() -> "Error receiving data unit (Device " + device.id + ")", e);
					}
					
				}
			} catch (final IOException e) {
				LOG.error("I/O Error (Device " + device.id + ")" , e);
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
