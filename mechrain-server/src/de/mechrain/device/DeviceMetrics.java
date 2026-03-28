package de.mechrain.device;

import java.util.TreeMap;

public class DeviceMetrics {

	public static final long WINDOW_HOUR  =        3_600_000L;
	public static final long WINDOW_DAY   =       86_400_000L;
	public static final long WINDOW_WEEK  =      604_800_000L;
	public static final long WINDOW_MONTH =    2_592_000_000L;

	/** Granularity of each bucket. 8,640 buckets max (30 days × 24 h × 12 per hour). */
	private static final long BUCKET_MS   =          300_000L;
	private static final long MAX_AGE_MS  = WINDOW_MONTH;

	private static final int MSG_SENT = 0, MSG_RECV = 1, BYTES_SENT = 2, BYTES_RECV = 3;

	/** Key: bucket start timestamp (truncated to BUCKET_MS). Value: long[4] counters. */
	private final TreeMap<Long, long[]> buckets = new TreeMap<>();

	public synchronized void recordSent(final long bytes) {
		prune();
		final long[] b = getOrCreateBucket();
		b[MSG_SENT]++;
		b[BYTES_SENT] += bytes;
	}

	public synchronized void recordReceived(final long bytes) {
		prune();
		final long[] b = getOrCreateBucket();
		b[MSG_RECV]++;
		b[BYTES_RECV] += bytes;
	}

	private long[] getOrCreateBucket() {
		final long key = System.currentTimeMillis() / BUCKET_MS * BUCKET_MS;
		return buckets.computeIfAbsent(key, k -> new long[4]);
	}

	private void prune() {
		final long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
		buckets.headMap(cutoff).clear();
	}

	public synchronized MetricSnapshot snapshot(final long windowMs) {
		final long cutoff = System.currentTimeMillis() - windowMs;
		long msgSent = 0, msgReceived = 0, bytesSent = 0, bytesReceived = 0;
		for (final long[] b : buckets.tailMap(cutoff).values()) {
			msgSent       += b[MSG_SENT];
			msgReceived   += b[MSG_RECV];
			bytesSent     += b[BYTES_SENT];
			bytesReceived += b[BYTES_RECV];
		}
		return new MetricSnapshot(msgSent, msgReceived, bytesSent, bytesReceived);
	}

	public record MetricSnapshot(long msgSent, long msgReceived, long bytesSent, long bytesReceived) {}
}
