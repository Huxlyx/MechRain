package de.mechrain.device.sink;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.MRP;
import de.mechrain.protocol.datatypes.FloatDataUnit;
import de.mechrain.protocol.datatypes.UInt1DataUnit;
import de.mechrain.protocol.datatypes.UInt2DataUnit;

public class VictoriaMetricsSink extends AbstractFilteredDataSink {
	
	private static final long serialVersionUID = -9045802626420394242L;
	private static final Logger LOG = LogManager.getLogger(Logging.SINK);
	
	private List<MRP> filter;
	private String host;
	private int port;
	private String measurementName;
	private transient boolean connected;

	protected VictoriaMetricsSink(final Builder builder) {
		super(builder.filter);
		super.setId(builder.id);
		this.host = builder.host;
		this.port = builder.port;
		this.measurementName = builder.measurementName;
	}

	@Override
	public boolean connect() {
		final String url = "http://" + host + ':' + port + "/"; // simple root check
		try {
			final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(2000);
			conn.setReadTimeout(2000);
			conn.setRequestMethod("GET");
			conn.connect();
			final int rc = conn.getResponseCode();
			connected = rc >= 200 && rc < 400;
			conn.disconnect();
			if (connected) {
				LOG.info(() -> "Connected to VictoriaMetrics at " + host + ':' + port);
			} else {
				LOG.warn(() -> "VictoriaMetrics returned HTTP " + rc + " for " + url);
			}
			return connected;
		} catch (final IOException e) {
			LOG.error(() -> "Unable to reach VictoriaMetrics at " + host + ':' + port, e);
			connected = false;
			return false;
		}
	}

	@Override
	public void disconnect() {
		connected = false;
	}

	@Override
	public boolean isAvailable() {
		return connected;
	}

	public List<MRP> getFilter() {
		return filter;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getMeasurementName() {
		return measurementName;
	}

	@Override
	public void handleDataUnit(final AbstractMechRainDataUnit mdu) {
		/* Respect filter from AbstractFilteredDataSink */
		if ( ! isAvailable()) {
			/* attempt reconnect once */
			if ( ! connect()) {
				LOG.debug(() -> "VictoriaMetrics sink not available, skipping data unit " + mdu.getId());
				return;
			}
		}
		/* AbstractFilteredDataSink already filters by id; but double-check local filter if present */
		if (filter != null && ! filter.contains(mdu.getId())) {
			LOG.trace(() -> "Skip handling of " + mdu.getId() + " not in filter");
			return;
		}
		final double value;
		switch (mdu.getId()) {
		case HUMIDITY:
		case TEMPERATURE:
			value = ((FloatDataUnit) mdu).getValue();
			break;
		case SOIL_MOISTURE_ABS:
		case CO2_PPM:
			value = ((UInt2DataUnit) mdu).getValue();
			break;
		case SOIL_MOISTURE_PERCENT:
			value = ((UInt1DataUnit) mdu).getValue();
			break;
		case LIGHT:
		case DISTANCE_ABS:
		case DISTANCE_MM:
		default:
			LOG.error(() -> "Data unit " + mdu.getClass().getSimpleName() + " not supported by VictoriaMetricsSink");
			return;
		}
		final String metricName = measurementName != null ? measurementName : mdu.getId().name().toLowerCase();
		final StringBuilder sb = new StringBuilder();
		sb.append(metricName).append(',').append(mdu.getId().name().toLowerCase()).append('=').append(value);
		final String payload = sb.toString();
		final String writeUrl = "http://" + host + ':' + port + "/api/v1/write";
		LOG.debug(() -> "Sending metric to VictoriaMetrics: " + payload.trim());
		HttpURLConnection conn = null;
		try {
			final URL u = new URL(writeUrl);
			conn = (HttpURLConnection) u.openConnection();
			conn.setConnectTimeout(3000);
			conn.setReadTimeout(5000);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
			try (final OutputStream os = conn.getOutputStream()) {
				os.write(payload.getBytes("UTF-8"));
				os.flush();
			}
			final int rc = conn.getResponseCode();
			if (rc < 200 || rc >= 300) {
				LOG.error(() -> "VictoriaMetrics write failed with HTTP " + rc);
				connected = false;
			} else {
				LOG.debug(() -> "VictoriaMetrics write succeeded (HTTP " + rc + ")");
			}
		} catch (final IOException e) {
			LOG.error(() -> "Error sending metrics to VictoriaMetrics", e);
			connected = false;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}
	
	public static class Builder {
		
		private int id;
		private List<MRP> filter;
		private String host;
		private int port;
		private String measurementName;
		
		public Builder id(final int id) {
			this.id = id;
			return this;
		}
		
		public Builder filter(final List<MRP> filter) {
			this.filter = filter;
			return this;
		}
		
		public Builder host(final String host) {
			this.host = host;
			return this;
		}
		
		public Builder port(final int port) {
			this.port = port;
			return this;
		}
		
		public Builder measurementName(final String measurementName) {
			this.measurementName = measurementName;
			return this;
		}
		
		public void validate() {
			if (host == null || host.isEmpty()) {
				throw new IllegalStateException("Host is not set");
			}
			if (port <= 0) {
				throw new IllegalStateException("Port is not set");
			}
			if (measurementName == null || measurementName.isEmpty()) {
				throw new IllegalStateException("Measurement name is not set");
			}
		}
		
		public VictoriaMetricsSink build() {
			validate();
			return new VictoriaMetricsSink(this);
		}
	}
}