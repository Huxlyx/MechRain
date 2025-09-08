package de.mechrain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

import de.mechrain.log.CliAppender;
import de.mechrain.log.CliService;
import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.DataUnitFactory;
import de.mechrain.protocol.DataUnitValidationException;
import de.mechrain.protocol.DeviceSettingChangeDataUnit;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.DeviceSettingRequestDataUnit;
import de.mechrain.protocol.DeviceSettingRequestDataUnit.DeviceSettingRequestBuilder;
import de.mechrain.protocol.ErrorDataUnit;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.MeasurementRequestDataUnit;
import de.mechrain.protocol.MeasurementRequestDataUnit.MeasurementRequestBuilder;
import de.mechrain.protocol.SoilMoistureAbsDataUnit;
import de.mechrain.protocol.SoilMoisturePercentDataUnit;
import de.mechrain.protocol.StatusMessageDataUnit;

public class Server {
	private static final Logger LOG = LogManager.getLogger(Logging.SERVER);

	private static final int UDP_PORT = 5000;

	public static void main(String[] args) throws IOException, InterruptedException {
		try (final ServerSocket deviceSocket = new ServerSocket(0);
				final ServerSocket cliSocket = new ServerSocket(0)) {
			final int devicePort = deviceSocket.getLocalPort();
			final int cliPort = cliSocket.getLocalPort();

			final Thread udpThread = new Thread(new UdpDiscoveryService(UDP_PORT, devicePort, cliPort));
			udpThread.setDaemon(true);
			udpThread.start();
			
			final CliAppender appender = LoggerContext.getContext(false).getConfiguration().getAppender("CliAppender");
			final Thread cliThread = new Thread(new CliService(appender, cliSocket));
			cliThread.setDaemon(true);
			cliThread.start();
			
			LOG.info("Listening for Connections");
			while (true) {
				try {
					final Socket client = deviceSocket.accept();
					LOG.info("Got connection");
					final InputStream is = client.getInputStream();
					final OutputStream os = client.getOutputStream();

					final byte[] handshakeBytes = new byte[4];
					is.read(handshakeBytes);
					LOG.debug(() -> "Handshake: " + Util.BYTES2HEX(handshakeBytes, 4));

					final ReadThread rt = new ReadThread(is);
					rt.start();

					/* 45s ~ 900 ml bei 5V 	 -> 20ml/s */
					/* 45s ~ 600 ml bei 3.3V -> 13ml/s */

					
					final Timer timer = new Timer();
					final TimerTask task = new TimerTask() {
						
						@Override
						public void run() {
							
							try {
								final MeasurementRequestDataUnit mreQ = new MeasurementRequestBuilder().measurementId(MRP.SOIL_MOISTURE_ABS).channelId((byte) 0).build();
								os.write(mreQ.toBytes());
							
								final MeasurementRequestDataUnit mreQ2 = new MeasurementRequestBuilder().measurementId(MRP.SOIL_MOISTURE_PERCENT).channelId((byte) 0).build();
								os.write(mreQ2.toBytes());
							} catch (IOException | DataUnitValidationException e) {
								LOG.error(() -> "Could not take measurement", e);
							}
						}
					};
					
					timer.schedule(task, 5_000, 30_000);
				} catch (IOException e) {
					e.printStackTrace();
					LOG.error(() -> "Error", e);
				};
			}
		}
	}

	private static class ReadThread extends Thread {

		private final InputStream is;
		private boolean run = true;

		private ReadThread(final InputStream is) {
			this.is = is;
		}

		public void end() {
			this.run = false;
		}

		@Override
		public void run() {
			final byte[] header = new byte[3];
			final DataUnitFactory duf = new DataUnitFactory();
			final InfluxDB db;
			db = InfluxDBFactory.connect("http://127.0.0.1:8086", "MechRain2", "MechRain2");
			db.setDatabase("MechRain2");
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
							LOG.error(() -> "Invalid number of header bytes " + headerLen);
							return;
						}
					}
					LOG.debug(() -> "Header: " + Util.BYTES2HEX(header, 3));

					AbstractMechRainDataUnit dataUnit;
					try {
						dataUnit = duf.getDataUnit(header, is);
						
						if (dataUnit instanceof SoilMoistureAbsDataUnit abs) {
							LOG.info(() -> "Received data unit - " + abs);
							db.write(Point.measurement("SoilMoist").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoistAbs", abs.getSoilMoistAbs()).build());
						} else if (dataUnit instanceof SoilMoisturePercentDataUnit pecent) {
							LOG.info(() -> "Received data unit - " + pecent);
							db.write(Point.measurement("SoilMoist").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoistPercent", pecent.getSoilMoistPercent()).build());
						} else if (dataUnit instanceof ErrorDataUnit error) {
							LOG.error(() -> error.getMessage());
						} else if (dataUnit instanceof StatusMessageDataUnit status) {
							LOG.info(() -> status.getMessage());
						} else {
							LOG.info(() -> "Received data unit - " + dataUnit);
						}
					} catch (final DataUnitValidationException e) {
						LOG.error(() -> "Error receiving data unit", e);
					}
					
				}
			} catch (final IOException e) {
				LOG.error("I/O Error", e);
			} finally {
				try {
					is.close();
				} catch (final IOException e) {
					LOG.error("I/O Error in cleanup", e);
				}
			}
			LOG.info("Read thread ended");
		}
	}

}
