package de.mechrain.web;

import java.util.List;

/**
 * A signal creation request from the web UI. Field names match the prototype's
 * {@code collectSpec()} output:
 *
 * <pre>
 * {"type":"threshold","targetDeviceId":1,"measurement":"TEMPERATURE","comparator":"GT",
 *  "threshold":30,"offThreshold":28,"stableForMinutes":5}
 * {"type":"gate","operator":"AND","childSignalIds":[1,3]}
 * {"type":"inverter","childSignalId":2}
 * {"type":"staleness","targetDeviceId":1,"measurement":null,"timeoutMinutes":10}
 * {"type":"timewindow","start":"22:00","end":"06:00","days":[1,2,3,4,5]}
 * {"type":"manual"}
 * </pre>
 *
 * <p>{@code comparator} uses the UI symbols {@code GT}/{@code GTE}/{@code LT}/{@code LTE};
 * {@code days} uses ISO-8601 numbers (1 = MONDAY … 7 = SUNDAY).
 */
public class SignalSpec {

	public String type;

	public Integer targetDeviceId;
	public String measurement;
	public String comparator;
	public Double threshold;
	public Double offThreshold;
	public Integer stableForMinutes;

	public String operator;
	public List<Integer> childSignalIds;

	public Integer childSignalId;

	public Double timeoutMinutes;

	public String start;
	public String end;
	public List<Integer> days;
}
