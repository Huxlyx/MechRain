package de.mechrain.protocol;

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
	 * Constructs the appropriate MechRain data unit from a received frame.
	 *
	 * @param header  the header bytes containing the message type and length
	 * @param payload the payload announced by the header
	 * @return the constructed MechRain data unit, or {@code null} for an unknown message type
	 * @throws DataUnitValidationException if the data unit validation fails
	 */
	public AbstractMechRainDataUnit getDataUnit(final byte[] header, final byte[] payload) throws DataUnitValidationException {
		final MRP mrp = MRP.fromByte(header[0]);
		final int length = payload.length;

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
			case SOIL_MOISTURE_ABS:
			case CO2_PPM:
			case DISTANCE_MM:
			case DISTANCE_ABS:
				return new UInt2DataUnitBuilder(mrp)
						.soilMoistureAbs((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case SOIL_MOISTURE_PERCENT:
				return new UInt1DataUnitBuilder(mrp)
						.soilMoisturePercent(payload[0] & 0xFF)
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

	/**
	 * Returns the channel a measurement reply names itself. Newer firmware appends it after the value
	 * of a channeled measurement: {@code CHANNEL_ID (0xA0), 2 byte length (1), 1 byte channel}.
	 *
	 * @param mrp     the message type of the reply
	 * @param payload the reply payload
	 * @return the reported channel, or {@code null} if the reply carries no (valid) channel field
	 */
	public static Integer reportedChannel(final MRP mrp, final byte[] payload) {
		switch (mrp) {
			case SOIL_MOISTURE_ABS:
				return readChannelField(payload, 2);
			case SOIL_MOISTURE_PERCENT:
				return readChannelField(payload, 1);
			default:
				return null;
		}
	}

	/**
	 * @param payload the reply payload
	 * @param offset  the position right after the value
	 * @return the channel if the payload continues with exactly one well-formed channel field, otherwise {@code null}
	 */
	static Integer readChannelField(final byte[] payload, final int offset) {
		if (payload.length <= offset) {
			/* older firmware: the value is all there is */
			return null;
		}
		final boolean wellFormed = payload.length == offset + 4
				&& payload[offset] == MRP.CHANNEL_ID.byteVal
				&& payload[offset + 1] == 0x00
				&& payload[offset + 2] == 0x01;
		if ( ! wellFormed) {
			LOG.warn(() -> "Ignoring unexpected bytes after the value (no valid channel field): " + Util.BYTES2HEX(payload));
			return null;
		}
		return payload[offset + 3] & 0xFF;
	}
}
