package de.mechrain.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AckDataUnit.AckBuilder;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.HeartbeatDataUnit.HeartbeatBuilder;
import de.mechrain.protocol.datatypes.FloatDataUnit.FloatDataUnitBuilder;
import de.mechrain.protocol.datatypes.TextDataUnit.TextDataUnitBuilder;
import de.mechrain.protocol.datatypes.UInt1DataUnit.UInt1DataUnitBuilder;
import de.mechrain.protocol.datatypes.UInt2DataUnit.UInt2DataUnitBuilder;
import de.mechrain.util.Util;

/**
 * Factory class for creating MechRain data units from byte streams.
 */
public class DataUnitFactory {
	
	private static final Logger LOG = LogManager.getLogger(Logging.DATA);

	/**
	 * Reads from the provided InputStream and constructs the appropriate MechRain data unit based on the header information.
	 *
	 * @param header the header bytes containing the message type and length
	 * @param is     the InputStream to read the payload from
	 * @return the constructed MechRain data unit
	 * @throws DataUnitValidationException if the data unit validation fails
	 * @throws IOException                 if an I/O error occurs while reading from the InputStream
	 */
	public AbstractMechRainDataUnit getDataUnit(final byte[] header, final InputStream is) throws DataUnitValidationException, IOException {
		final MRP mrp = MRP.fromByte(header[0]);
		final int length = (header[1]& 0xFF) << 8 | header[2] & 0xFF;
		
		final byte[] payload;
		payload = is.readNBytes(length);
		
		LOG.trace(() -> "Payload length " + length + " bytes: " + Util.BYTES2HEX(payload));
		
		switch (mrp) {
			case ACK:
				return new AckBuilder().build();
			case HEARTBEAT:
				return new HeartbeatBuilder().build();
			case DEVICE_SETTING_CHANGE:
				return new DeviceSettingChangeBuilder()
						.settingId(MRP.fromByte(payload[0]))
						.build();
			case STATUS_MSG:
			case ERROR:
			case BUILD_ID:
				return new TextDataUnitBuilder(mrp)
						.message(new String(payload, StandardCharsets.ISO_8859_1))
						.build();
			case UDP_BROADCAST_DELAY:
			case CONNECTION_DELAY:
			case CO2_PPM:
			case DISTANCE_MM:
			case DISTANCE_ABS:
				return new UInt2DataUnitBuilder(mrp)
						.soilMoistureAbs((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case SOIL_MOISTURE_ABS: {
				final AbstractMechRainDataUnit unit = new UInt2DataUnitBuilder(mrp)
						.soilMoistureAbs((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
				unit.setReportedChannelId(readChannelField(payload, 2));
				return unit;
			}
			case SOIL_MOISTURE_PERCENT: {
				final AbstractMechRainDataUnit unit = new UInt1DataUnitBuilder(mrp)
						.soilMoisturePercent(payload[0] & 0xFF)
						.build();
				unit.setReportedChannelId(readChannelField(payload, 1));
				return unit;
			}
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

	/**
	 * Reads the optional channel field newer firmware appends after the value of a channeled
	 * measurement reply: {@code CHANNEL_ID (0xA0), 2 byte length (1), 1 byte channel}.
	 *
	 * @param payload the reply payload
	 * @param offset  the position right after the value
	 * @return the reported channel, or {@code null} if the reply carries no (valid) channel field
	 */
	static Integer readChannelField(final byte[] payload, final int offset) {
		if (payload.length < offset + 4 || payload[offset] != MRP.CHANNEL_ID.byteVal) {
			return null;
		}
		final int fieldLength = (payload[offset + 1] & 0xFF) << 8 | payload[offset + 2] & 0xFF;
		if (fieldLength < 1) {
			LOG.warn(() -> "Ignoring channel field with length " + fieldLength + ": " + Util.BYTES2HEX(payload));
			return null;
		}
		return payload[offset + 3] & 0xFF;
	}
}
