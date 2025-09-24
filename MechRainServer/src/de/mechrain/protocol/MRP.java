package de.mechrain.protocol;

/**
 * Mech rain Protocol
 */
public enum MRP {
	UNKNOWN                 ((byte) 0x00),
	
	DEVICE_ID         		((byte) 0x01),
	MEASUREMENT_REQ   		((byte) 0x04),
	MEASUREMENT_RESP  		((byte) 0x05),
	TOGGLE_OUT_PIN    		((byte) 0x06),
	DEVICE_SETTING_REQ      ((byte) 0x0C),
	DEVICE_SETTING_CHANGE   ((byte) 0x0D),
	ACK               		((byte) 0x0E),
	RESET             		((byte) 0x0F),

	CHANNEL_ID    			((byte) 0xA0),
	DURATION_MS   			((byte) 0xA1),

	SOIL_MOISTURE_PERCENT 	((byte) 0xB0),
	SOIL_MOISTURE_ABS     	((byte) 0xB1),
	TEMPERATURE           	((byte) 0xB2),
	HUMIDITY              	((byte) 0xB3),
	LIGHT                 	((byte) 0xB4),
	DISTANCE_MM           	((byte) 0xB5),
	DISTANCE_ABS          	((byte) 0xB6),
	CO2_PPM		          	((byte) 0xB7),
	UP_TIME               	((byte) 0xBA),
	IMAGE                 	((byte) 0xBE),

	START_SEGMENT 			((byte) 0xC0),
	SEGMENT       			((byte) 0xC1),
	END_SEGMENT   			((byte) 0xC2),
	
	UDP_BROADCAST_DELAY		((byte) 0xD0),
	CONNECTION_DELAY		((byte) 0xD1),
	IN_PIN_MASK				((byte) 0xD2),
	OUT_PIN_MASK			((byte) 0xD3),

	STATUS_MSG    			((byte) 0xF0),
	ERROR         			((byte) 0xFF);
	
	public final byte byteVal;
	
	private MRP(final byte byteVal) {
		this.byteVal = byteVal;
	}
	
	public static MRP fromByte(final byte val) {
		for (final MRP mrp : MRP.values()) {
			if (mrp.byteVal == val) {
				return mrp;
			}
		}
		return UNKNOWN;
	}
}
