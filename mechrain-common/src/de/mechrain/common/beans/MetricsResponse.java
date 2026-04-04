package de.mechrain.common.beans;

import java.util.List;

public class MetricsResponse implements ICliBean {

	private static final long serialVersionUID = 5814327093916559004L;

	private List<DeviceMetricsData> deviceMetricsList;
	private DeviceMetricsData totalMetrics;
	private DeviceMetricsData cliMetrics;

	public List<DeviceMetricsData> getDeviceMetricsList() {
		return deviceMetricsList;
	}

	public void setDeviceMetricsList(final List<DeviceMetricsData> deviceMetricsList) {
		this.deviceMetricsList = deviceMetricsList;
	}

	public DeviceMetricsData getTotalMetrics() {
		return totalMetrics;
	}

	public void setTotalMetrics(final DeviceMetricsData totalMetrics) {
		this.totalMetrics = totalMetrics;
	}

	public DeviceMetricsData getCliMetrics() {
		return cliMetrics;
	}

	public void setCliMetrics(final DeviceMetricsData cliMetrics) {
		this.cliMetrics = cliMetrics;
	}
}
