package de.mechrain.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class with static helper methods.
 */
public final class Util {
	
	private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*(\\w+)");

	private Util() {
		throw new IllegalAccessError("Do not instantiate!");
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	
	/**
	 * Converts a byte array to a hexadecimal string representation.
	 * 
	 * @param bytes the byte array
	 * @return the hexadecimal string representation
	 */
	public static String BYTES2HEX(final byte[] bytes) {
		return BYTES2HEX(bytes, bytes.length);
	}
	
	/**
	 * Converts a byte array to a hexadecimal string representation.
	 * 
	 * @param bytes the byte array
	 * @param len   the number of bytes to convert
	 * @return the hexadecimal string representation
	 */
	public static String BYTES2HEX(final byte[] bytes, final int len) {
		if (len <= 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(len * 3 - 1);

		for (int i = 0; i < len; i++) {
			int v = bytes[i] & 0xFF;
			sb.append(HEX_ARRAY[v >> 4]);
			sb.append(HEX_ARRAY[v & 0x0F]);
			if (i < bytes.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	/**
	 * Parses a time string like "10 seconds" or "5 min" into a ParsedTime object.
	 * 
	 * @param input the input string
	 * @return the parsed time
	 * @throws IllegalArgumentException if the input format is invalid
	 */
	public static ParsedTime parse(String input) {
		Matcher matcher = TIME_PATTERN.matcher(input.trim().toLowerCase());

		if (!matcher.matches()) {
			throw new IllegalArgumentException("No date format: " + input);
		}

		int value = Integer.parseInt(matcher.group(1));
		String unitStr = matcher.group(2);

		TimeUnit unit = mapToTimeUnit(unitStr);
		if (unit == null) {
			throw new IllegalArgumentException("Unknown time unit: " + unitStr);
		}

		return new ParsedTime(value, unit);
	}

	/**
	 * Maps a string representation of a time unit to a TimeUnit enum.
	 * 
	 * @param unit the string representation of the time unit
	 * @return the corresponding TimeUnit
	 * @throws IllegalArgumentException if the unit is unknown
	 */
	private static TimeUnit mapToTimeUnit(final String unit) {
		switch (unit) {
		case "ms":
		case "milli":
		case "millis":
		case "millisecond":
		case "milliseconds":
			return TimeUnit.MILLISECONDS;
		case "s":
		case "sec":
		case "second":
		case "seconds":
			return TimeUnit.SECONDS;
		case "m":
		case "min":
		case "mins":
		case "minute":
		case "minutes":
			return TimeUnit.MINUTES;
		case "h":
		case "hr":
		case "hour":
		case "hours":
			return TimeUnit.HOURS;
		case "d":
		case "day":
		case "days":
			return TimeUnit.DAYS;
		default:
			throw new IllegalArgumentException("Unknown unit " + unit);
		}
	}

	/**
	 * Represents a parsed time with a value and a time unit.
	 */
	public static class ParsedTime {
		public final int value;
		public final TimeUnit unit;

		public ParsedTime(final int value, final TimeUnit unit) {
			this.value = value;
			this.unit = unit;
		}

		@Override
		public String toString() {
			return value + " " + unit;
		}
	}


}
