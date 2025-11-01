package de.mechrain.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AckDataUnit.AckBuilder;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.datatypes.FloatDataUnit.FloatDataUnitBuilder;
import de.mechrain.protocol.datatypes.TextDataUnit.TextDataUnitBuilder;
import de.mechrain.protocol.datatypes.UInt1DataUnit.UInt1DataUnitBuilder;
import de.mechrain.protocol.datatypes.UInt2DataUnit.UInt2DataUnitBuilder;
import de.mechrain.util.Util;

public class DataUnitFactory {
	private static final Logger LOG = LogManager.getLogger(Logging.DATA);

	public AbstractMechRainDataUnit getDataUnit(final byte[] header, final InputStream is) throws DataUnitValidationException, IOException {
		final MRP mrp = MRP.fromByte(header[0]);
		final int length = (header[1]& 0xFF) << 8 | header[2] & 0xFF;
		
		final byte[] payload;
		payload = is.readNBytes(length);
		
		LOG.trace(() -> "Payload length " + length + " bytes: " + Util.BYTES2HEX(payload));
		
		switch (mrp) {
			case ACK:
				return new AckBuilder().build();
			case DEVICE_SETTING_CHANGE:
				return new DeviceSettingChangeBuilder()
						.settingId(MRP.fromByte(payload[0]))
						.build();
			case STATUS_MSG:
			case ERROR:
				return new TextDataUnitBuilder(mrp)
						.message(new String(payload, StandardCharsets.ISO_8859_1))
						.build();
			case UDP_BROADCAST_DELAY:
			case CONNECTION_DELAY:
			case SOIL_MOISTURE_ABS:
			case CO2_PPM:
			case DISTANCE_MM:
			case DISTANCE_ABS:
				return new UInt2DataUnitBuilder(mrp)
						.soilMoistureAbs((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case SOIL_MOISTURE_PERCENT:
				return new UInt1DataUnitBuilder(mrp)
						.soilMoisturePercent(payload[0])
						.build();
			case TEMPERATURE:
			case HUMIDITY:
				final int floatBits = payload[3] << 24 
				| (payload[2] & 0xFF) << 16 
				| (payload[1] & 0xFF) << 8 
				| (payload[0] & 0xFF);
				return new FloatDataUnitBuilder(mrp)
						.humidity(Float.intBitsToFloat(floatBits))
						.build();
			default:
				LOG.warn("Unknown message type " + mrp.name());
				return null;
		}
	}
}
