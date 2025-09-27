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
	exports de.mechrain.log to org.apache.logging.log4j.core;
	exports de.mechrain.protocol to com.google.gson;
	exports de.mechrain.cmdline.beans to MechRainCLI;
	exports de.mechrain.device to MechRainCLI;
	opens de.mechrain.device to com.google.gson;
	opens de.mechrain.device.task to com.google.gson;
}