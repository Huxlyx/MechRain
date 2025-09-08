package de.mechrain;

public final class Util {
	
	private Util() {
		throw new IllegalAccessError("Do not instantiate!");
	}
	
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	

	public static final String BYTES2HEX(final byte[] bytes, final int len) {
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

}
