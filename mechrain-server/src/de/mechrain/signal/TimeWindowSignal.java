package de.mechrain.signal;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A signal that is active during a configured time-of-day window, optionally
 * restricted to specific days of the week. Supports overnight windows where
 * {@code endMinuteOfDay < startMinuteOfDay} (e.g. 22:00-06:00).
 */
public class TimeWindowSignal extends AbstractSignal {

	private static final long serialVersionUID = 1L;

	/** Minute of day (0-1439, inclusive) at which the window becomes active. */
	private int startMinuteOfDay;

	/** Minute of day (0-1439, inclusive) at which the window becomes inactive. */
	private int endMinuteOfDay;

	/** Days of week the window applies to, or {@code null}/empty to apply every day. */
	private Set<DayOfWeek> days;

	/** Default constructor for de-serialization purposes. */
	public TimeWindowSignal() {
		/* empty constructor for de-serialization */
	}

	public TimeWindowSignal(final int startMinuteOfDay, final int endMinuteOfDay, final Set<DayOfWeek> days) {
		this.startMinuteOfDay = startMinuteOfDay;
		this.endMinuteOfDay = endMinuteOfDay;
		this.days = (days == null || days.isEmpty()) ? null : EnumSet.copyOf(days);
	}

	public int getStartMinuteOfDay() {
		return startMinuteOfDay;
	}

	public void setStartMinuteOfDay(final int startMinuteOfDay) {
		this.startMinuteOfDay = startMinuteOfDay;
	}

	public int getEndMinuteOfDay() {
		return endMinuteOfDay;
	}

	public void setEndMinuteOfDay(final int endMinuteOfDay) {
		this.endMinuteOfDay = endMinuteOfDay;
	}

	/** Returns the configured days of week, or {@code null} if the window applies every day. */
	public Set<DayOfWeek> getDays() {
		return days;
	}

	public void setDays(final Set<DayOfWeek> days) {
		this.days = (days == null || days.isEmpty()) ? null : EnumSet.copyOf(days);
	}

	@Override
	public boolean isActive() {
		final LocalTime now = LocalTime.now();
		if (days != null && !days.contains(java.time.LocalDate.now().getDayOfWeek())) {
			return false;
		}
		final int nowMinute = now.getHour() * 60 + now.getMinute();
		if (startMinuteOfDay <= endMinuteOfDay) {
			return nowMinute >= startMinuteOfDay && nowMinute < endMinuteOfDay;
		}
		/* overnight window, e.g. 22:00-06:00 */
		return nowMinute >= startMinuteOfDay || nowMinute < endMinuteOfDay;
	}

	@Override
	public String getSignalType() {
		return "Time Window";
	}

	@Override
	public String getSignalDescription() {
		final StringBuilder sb = new StringBuilder();
		sb.append(formatMinute(startMinuteOfDay)).append('-').append(formatMinute(endMinuteOfDay));
		if (days != null) {
			sb.append(" [").append(String.join(",", days.stream().map(DayOfWeek::name).sorted().toList())).append(']');
		}
		return sb.toString();
	}

	@Override
	public List<Integer> getChildSignalIds() {
		return null;
	}

	private static String formatMinute(final int minuteOfDay) {
		return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
	}

	@Override
	public String toString() {
		return "TimeWindowSignal id:" + getId() + " " + getSignalDescription();
	}
}
