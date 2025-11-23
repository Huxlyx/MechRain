package de.mechrain.device.sink;

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
import de.mechrain.protocol.datatypes.FloatDataUnit;
import de.mechrain.protocol.datatypes.UInt1DataUnit;
import de.mechrain.protocol.datatypes.UInt2DataUnit;

/**
 * A data sink that writes MechRain data units to an InfluxDB database.
 * The sink instance is serialized but the InfluxDB connection is transient and must be re-established after deserialization.
 */
public class InfluxSink extends AbstractFilteredDataSink {
    
    private static final long serialVersionUID = -5866434237318835300L;

    private static final Logger LOG = LogManager.getLogger(Logging.SINK);
    
    private transient InfluxDB db; 
    private transient boolean connected;
    
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String dbName;
    private final String measurementName;
    
    private InfluxSink(final Builder builder) {
    	super(builder.filter);
    	super.setId(builder.id);
        this.host = builder.host;
        this.port = builder.port;
        this.user = builder.user;
        this.password = builder.password;
        this.dbName = builder.dbName;
        this.measurementName = builder.measurementName;
    }
    
    @Override
    public boolean connect() {
        try {
            /* http://127.0.0.1:8086 */
            db = InfluxDBFactory.connect("http://" + host + ':' + port, user, password);
            db.setDatabase(dbName);
            final Pong ping = db.ping();
            connected = ping.isGood();
            return connected;
        } catch (final Exception e) {
            LOG.error(() -> "Could not connect", e);
        }
        return false;
    }
    
    public void disconnect() {
        if (db != null) {
            db.close();
        }
        connected = false;
    }

    @Override
    public boolean isAvailable() {
        return connected;
    }

    @Override
    public void handleDataUnit(final AbstractMechRainDataUnit mdu) {
        // If a filter is set and it does not contain this id, skip the unit
        if (filter != null && !filter.contains(mdu.getId())) {
            LOG.trace(() -> "Skip handling of " + mdu.getId() + " not in filter");
            return;
        }
        LOG.debug(() -> "Handling data unit " + mdu.getId());
        final Point point;
        switch (mdu.getId()) {
        case HUMIDITY:
            point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("humidity", ((FloatDataUnit)mdu).getValue()).build();
            break;
        case TEMPERATURE:
            point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("temperature", ((FloatDataUnit)mdu).getValue()).build();
            break;
        case SOIL_MOISTURE_ABS:
            point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoistAbs", ((UInt2DataUnit)mdu).getValue()).build();
            break;
        case CO2_PPM:
            point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("pwm", ((UInt2DataUnit)mdu).getValue()).build();
            break;
        case SOIL_MOISTURE_PERCENT:
            point = Point.measurement(measurementName).time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).addField("MoisPercent", ((UInt1DataUnit)mdu).getValue()).build();
            break;
        case LIGHT:
        case DISTANCE_ABS:
        case DISTANCE_MM:
        default:
            LOG.error(() -> "Data unit " + mdu.getClass().getSimpleName() + " not supported");
            return;
        }
        LOG.debug(() -> "Writing point " + point + " to influx");
        db.write(point);
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

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getDbName() {
        return dbName;
    }

    public String getMeasurementName() {
        return measurementName;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InfluxSink db:").append(dbName).append('@').append(host).append(" measurement:").append(measurementName);
        
        final StringJoiner sj = new StringJoiner(",");
        if (filter != null) {
            for (final MRP mrp : filter) {
                sj.add(mrp.name());
            }
        }
        sb.append(" filter:<").append(sj.toString()).append('>')
        .append(" id:").append(getId());
        return sb.toString();
    }
    
    /**
     * Builder for InfluxSink. Validates required fields before building.
     */
    public static class Builder {
        private int id = -1;
        private List<MRP> filter;
        private String host;
        private int port = -1;
        private String user;
        private String password;
        private String dbName;
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
        
        public Builder user(final String user) {
            this.user = user;
            return this;
        }
        
        public Builder password(final String password) {
            this.password = password;
            return this;
        }
        
        public Builder dbName(final String dbName) {
            this.dbName = dbName;
            return this;
        }
        
        public Builder measurementName(final String measurementName) {
            this.measurementName = measurementName;
            return this;
        }
        
        private void validate() {
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("InfluxSink: host must be provided");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalStateException("InfluxSink: port must be a valid TCP port (1-65535)");
            }
            if (dbName == null || dbName.isEmpty()) {
                throw new IllegalStateException("InfluxSink: dbName must be provided");
            }
            if (measurementName == null || measurementName.isEmpty()) {
                throw new IllegalStateException("InfluxSink: measurementName must be provided");
            }
            if (filter == null || filter.isEmpty()) {
                throw new IllegalStateException("InfluxSink: filter must contain at least one MRP");
            }
        }
        
        public InfluxSink build() {
            validate();
            return new InfluxSink(this);
        }
    }
}
