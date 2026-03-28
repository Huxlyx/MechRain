package de.mechrain.common.beans;

public class DeviceMetricsData implements ICliBean {

	private static final long serialVersionUID = 1L;

	private int deviceId;
	private String deviceName;

	private long msgSentHour;
	private long msgReceivedHour;
	private long bytesSentHour;
	private long bytesReceivedHour;

	private long msgSentDay;
	private long msgReceivedDay;
	private long bytesSentDay;
	private long bytesReceivedDay;

	private long msgSentWeek;
	private long msgReceivedWeek;
	private long bytesSentWeek;
	private long bytesReceivedWeek;

	private long msgSentMonth;
	private long msgReceivedMonth;
	private long bytesSentMonth;
	private long bytesReceivedMonth;

	public int getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(final int deviceId) {
		this.deviceId = deviceId;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(final String deviceName) {
		this.deviceName = deviceName;
	}

	public long getMsgSentHour() { return msgSentHour; }
	public void setMsgSentHour(final long v) { this.msgSentHour = v; }

	public long getMsgReceivedHour() { return msgReceivedHour; }
	public void setMsgReceivedHour(final long v) { this.msgReceivedHour = v; }

	public long getBytesSentHour() { return bytesSentHour; }
	public void setBytesSentHour(final long v) { this.bytesSentHour = v; }

	public long getBytesReceivedHour() { return bytesReceivedHour; }
	public void setBytesReceivedHour(final long v) { this.bytesReceivedHour = v; }

	public long getMsgSentDay() { return msgSentDay; }
	public void setMsgSentDay(final long v) { this.msgSentDay = v; }

	public long getMsgReceivedDay() { return msgReceivedDay; }
	public void setMsgReceivedDay(final long v) { this.msgReceivedDay = v; }

	public long getBytesSentDay() { return bytesSentDay; }
	public void setBytesSentDay(final long v) { this.bytesSentDay = v; }

	public long getBytesReceivedDay() { return bytesReceivedDay; }
	public void setBytesReceivedDay(final long v) { this.bytesReceivedDay = v; }

	public long getMsgSentWeek() { return msgSentWeek; }
	public void setMsgSentWeek(final long v) { this.msgSentWeek = v; }

	public long getMsgReceivedWeek() { return msgReceivedWeek; }
	public void setMsgReceivedWeek(final long v) { this.msgReceivedWeek = v; }

	public long getBytesSentWeek() { return bytesSentWeek; }
	public void setBytesSentWeek(final long v) { this.bytesSentWeek = v; }

	public long getBytesReceivedWeek() { return bytesReceivedWeek; }
	public void setBytesReceivedWeek(final long v) { this.bytesReceivedWeek = v; }

	public long getMsgSentMonth() { return msgSentMonth; }
	public void setMsgSentMonth(final long v) { this.msgSentMonth = v; }

	public long getMsgReceivedMonth() { return msgReceivedMonth; }
	public void setMsgReceivedMonth(final long v) { this.msgReceivedMonth = v; }

	public long getBytesSentMonth() { return bytesSentMonth; }
	public void setBytesSentMonth(final long v) { this.bytesSentMonth = v; }

	public long getBytesReceivedMonth() { return bytesReceivedMonth; }
	public void setBytesReceivedMonth(final long v) { this.bytesReceivedMonth = v; }
}
