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
	exports de.mechrain.log to org.apache.logging.log4j.core;
}