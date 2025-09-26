package de.mechrain.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;
import de.mechrain.protocol.AckDataUnit.AckBuilder;
import de.mechrain.protocol.ConnectionDelayDataUnit.ConnectionDelayBuilder;
import de.mechrain.protocol.DeviceSettingChangeDataUnit.DeviceSettingChangeBuilder;
import de.mechrain.protocol.ErrorDataUnit.ErrorBuilder;
import de.mechrain.protocol.SoilMoistureAbsDataUnit.SoilMoistureAbsBuilder;
import de.mechrain.protocol.SoilMoisturePercentDataUnit.SoilMoisturePercentBuilder;
import de.mechrain.protocol.StatusMessageDataUnit.StatusMessageBuilder;
import de.mechrain.protocol.UdpBroadcastDelayDataUnit.UdpBroadcastDelayBuilder;
import de.mechrain.util.Util;

public class DataUnitFactory {
	private static final Logger LOG = LogManager.getLogger(Logging.SERVER);

	public AbstractMechRainDataUnit getDataUnit(final byte[] header, final InputStream is) throws DataUnitValidationException, IOException {
		final MRP mrp = MRP.fromByte(header[0]);
		final int length = (header[1]& 0xFF) << 8 | header[2] & 0xFF;
		
		final byte[] payload;
		payload = is.readNBytes(length);
		
		LOG.trace(() -> "Payload length " + length);
		LOG.trace(() -> "Payload: " + Util.BYTES2HEX(payload, payload.length));
		
		switch (mrp) {
			case ACK:
				return new AckBuilder().build();
			case DEVICE_SETTING_CHANGE:
				return new DeviceSettingChangeBuilder()
						.settingId(MRP.fromByte(payload[0]))
						.build();
			case STATUS_MSG:
				return new StatusMessageBuilder()
						.message(new String(payload, StandardCharsets.ISO_8859_1))
						.build();
			case ERROR:
				return new ErrorBuilder()
						.message(new String(payload, StandardCharsets.ISO_8859_1))
						.build();
			case UDP_BROADCAST_DELAY:
				return new UdpBroadcastDelayBuilder()
						.delay((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case CONNECTION_DELAY:
				return new ConnectionDelayBuilder()
						.delay((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case SOIL_MOISTURE_ABS:
				return new SoilMoistureAbsBuilder()
						.soilMoistureAbs((payload[0] & 0xFF)  << 8 | payload[1] & 0xFF)
						.build();
			case SOIL_MOISTURE_PERCENT:
				return new SoilMoisturePercentBuilder()
						.soilMoisturePercent(payload[0])
						.build();
			default:
				LOG.warn("Unknown messsage type " + mrp.name());
				return null;
		}
		
	}
}
