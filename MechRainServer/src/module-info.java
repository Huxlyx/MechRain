/**
 * 
 */
/**
 * 
 */
module MechRain {
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires influxdb.java;
	requires com.google.gson;
	exports de.mechrain.device;
	exports de.mechrain.device.sink;
	exports de.mechrain.device.task;
	exports de.mechrain.cmdline.beans;
	exports de.mechrain.log to org.apache.logging.log4j.core;
	exports de.mechrain.protocol to com.google.gson;
	opens de.mechrain.device to com.google.gson;
	opens de.mechrain.device.task to com.google.gson;
	opens de.mechrain.device.sink to com.google.gson;
}