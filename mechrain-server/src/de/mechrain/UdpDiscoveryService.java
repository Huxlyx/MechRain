package de.mechrain;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

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
	private final boolean testMode;

	public UdpDiscoveryService(final int udpPort, final int deviceTcpPort, final int cliTcpPort, final boolean testMode) {
		this.udpPort = udpPort;
		this.deviceTcpPort = deviceTcpPort;
		this.cliTcpPort = cliTcpPort;
		this.testMode = testMode;
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
				
				LOG.debug(() ->"Received: " + msg + " from " + packet.getAddress() + ":" + packet.getPort());
				
				final int port;
				if (testMode) {
					switch (msg) {
					case "MECH-RAIN-TEST":
						port = deviceTcpPort;
						break;
					case "CLI-TEST":
						port = cliTcpPort;
						break;
					default:
						LOG.debug(() ->"Message did not match any expected message and is ignored");
						continue;
					}
				} else {
					switch (msg) {
					case "MECH-RAIN-HELLO":
						port = deviceTcpPort;
						break;
					case "CLI-HELLO":
						port = cliTcpPort;
						break;
					default:
						LOG.debug(() ->"Message did not match any expected message and is ignored");
						continue;
					}
				}

				final String currentIp = getLocalAddressFor(packet.getAddress()).getHostAddress();
				final String response = "MECH-RAIN-SERVER:IP=" + currentIp + ";PORT=" + port;

				final byte[] sendBuf = response.getBytes();
				final DatagramPacket responseMsg = new DatagramPacket(sendBuf, sendBuf.length, packet.getAddress(), packet.getPort());
				socket.send(responseMsg);

				LOG.info(() ->"Sent response: " + response + " to " +  packet.getAddress() + ":" + packet.getPort());
			}
		} catch (final Exception e) {
			LOG.error("UDP discovery service error", e);
		}
	}
	
	/**
	 * Determines which local IP address the OS would use to reach {@code remoteAddress}
	 * by briefly connecting a datagram socket (no packets are sent).
	 * This correctly selects the right interface even on hosts with multiple NICs or
	 * virtual interfaces (e.g. Docker bridges).
	 *
	 * @param remoteAddress the address of the discovery requester
	 * @return the local {@link InetAddress} on the outgoing interface toward {@code remoteAddress}
	 * @throws Exception if the socket cannot be created or connected
	 */
	static InetAddress getLocalAddressFor(final InetAddress remoteAddress) throws Exception {
		try (final DatagramSocket socket = new DatagramSocket()) {
			socket.connect(new InetSocketAddress(remoteAddress, 9));
			return socket.getLocalAddress();
		}
	}
}
