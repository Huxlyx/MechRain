package de.mechrain;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mechrain.log.Logging;

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
					case "MECH-RAIN-HELLO":
						port = deviceTcpPort;
						break;
					case "CLI-HELLO":
						port = cliTcpPort;
						break;
					default:
						LOG.info(() ->"Message did not match any epected and is ignored");
						continue;
				}

				final String currentIp = InetAddress.getLocalHost().getHostAddress();
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

}
