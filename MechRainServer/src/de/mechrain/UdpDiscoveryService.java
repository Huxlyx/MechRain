package de.mechrain;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

/**
 * A UDP discovery service that listens for discovery requests and responds with the server's IP and port information.
 */
public class UdpDiscoveryService implements Runnable {
	
	private static final Logger LOG = LogManager.getLogger(Logging.UDP);

	private final int udpPort;
	private final int deviceTcpPort;
	private final int cliTcpPort;

	public UdpDiscoveryService(final int udpPort, final int deviceTcpPort, final int cliTcpPort) {
		this.udpPort = udpPort;
		this.deviceTcpPort = deviceTcpPort;
		this.cliTcpPort = cliTcpPort;
	}

	@Override
	public void run() {
		try (final DatagramSocket socket = new DatagramSocket(udpPort)) {
			final byte[] buf = new byte[256];
			LOG.info(() -> "Discovery on port " + udpPort);

			while (true) {
				final DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);

				final String msg = new String(packet.getData(), 0, packet.getLength());
				
				LOG.info(() ->"Received: " + msg + " from " + packet.getAddress() + ":" + packet.getPort());
				
				final int port;
				switch (msg) {
//					case "MECH-RAIN-HELLO":
					case "MECH-RAIN-TEST":
						port = deviceTcpPort;
						break;
//					case "CLI-HELLO":
					case "CLI-TEST":
						port = cliTcpPort;
						break;
					default:
						LOG.info(() ->"Message did not match any epected and is ignored");
						continue;
				}

				final String currentIp = getLocalNonLoopbackAddress().getHostAddress();
				final String response = "MECH-RAIN-SERVER:IP=" + currentIp + ";PORT=" + port;

				final byte[] sendBuf = response.getBytes();
				final DatagramPacket responseMsg = new DatagramPacket(sendBuf, sendBuf.length, packet.getAddress(), packet.getPort());
				socket.send(responseMsg);

				LOG.info(() ->"Sent response: " + response + " to " +  packet.getAddress() + ":" + packet.getPort());
			}
		} catch (final Exception e) {
			e.printStackTrace();
			LOG.error(e);
		}
	}
	
	/**
	 * Retrieves the local non-loopback IPv4 address of the machine.
	 *
	 * @return The local non-loopback InetAddress, or null if none found.
	 * @throws SocketException If an I/O error occurs.
	 */
	public static InetAddress getLocalNonLoopbackAddress() throws SocketException {
	    for (final NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
	        if (networkInterface.isUp() && ! networkInterface.isLoopback() && ! networkInterface.isVirtual()) {
	        	for (InetAddress addr : Collections.list(networkInterface.getInetAddresses())) {
	        		if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
	        			return addr;
	        		}
	        	}
	        }
	    }
	    return null;
	}
}
