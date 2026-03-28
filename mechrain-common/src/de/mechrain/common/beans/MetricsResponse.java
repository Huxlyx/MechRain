package de.mechrain.common.beans;

import java.util.List;

public class MetricsResponse implements ICliBean {

	private static final long serialVersionUID = 1L;

	private List<DeviceMetricsData> deviceMetricsList;

	public List<DeviceMetricsData> getDeviceMetricsList() {
		return deviceMetricsList;
	}

	public void setDeviceMetricsList(final List<DeviceMetricsData> deviceMetricsList) {
		this.deviceMetricsList = deviceMetricsList;
	}
}
