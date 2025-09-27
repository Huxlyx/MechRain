package de.mechrain.device;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AbstractMechRainDataUnit;
import de.mechrain.protocol.MRP;

public class InfluxSink implements DataSink {
	
	private static final long serialVersionUID = -5866434237318835300L;

	private static final Logger LOG = LogManager.getLogger(Logging.DATA);
	
	private transient InfluxDB db; 
	private transient boolean connected;
	
	private List<MRP> filter;
	
	private String name;
	private String host;
	private String port;
	private String user;
	private String password;
	private String dbName;
	private String measurementName;
	
	@Override
	public boolean connect() {
		/* http://127.0.0.1:8086 */
		db = InfluxDBFactory.connect("http://" + host + ':' + port, user, password);
		db.setDatabase(dbName);
		final Pong ping = db.ping();
		connected = true;
		return ping.isGood();
	}
	
	public void disconnect() {
		db.close();
		connected = false;
	}

	@Override
	public boolean isAvailable() {
		return connected;
	}

	@Override
	public void handleDataUnit(AbstractMechRainDataUnit mdu) {
		final Point point;
		switch (mdu.getId()) {
		case CO2_PPM:
			point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("pwm", isAvailable()).build();
			break;
		case HUMIDITY:
			point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("humidity", isAvailable()).build();
			break;
		case TEMPERATURE:
			point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("temperature", isAvailable()).build();
			break;
		case SOIL_MOISTURE_ABS:
			point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoistAbs", isAvailable()).build();
			break;
		case SOIL_MOISTURE_PERCENT:
			point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoisPercent", isAvailable()).build();
			break;
		case LIGHT:
		case DISTANCE_ABS:
		case DISTANCE_MM:
		default:
			LOG.error(() -> "Data unit " + mdu.getClass().getSimpleName() + " not supported");
			return;
		}
		db.write(point);
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("InfluxSink db:").append(dbName).append('@').append(host).append(" measurement:").append(measurementName);
		
		final StringJoiner sj = new StringJoiner(",");
		for (final MRP mrp : filter) {
			sj.add(mrp.name());
		}
		sb.append(" filter:<").append(sj.toString()).append('>');
		return sb.toString();
	}

	public List<MRP> getFilter() {
		return filter;
	}

	public void setFilter(List<MRP> filter) {
		this.filter = filter;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public String getMeasurementName() {
		return measurementName;
	}

	public void setMeasurementName(String measurementName) {
		this.measurementName = measurementName;
	}
}
