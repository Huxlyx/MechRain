package de.mario.mechrain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

	private static final int UDP_PORT = 5000;

	public static void main(String[] args) throws IOException, InterruptedException {
		final ServerSocket serverSocket = new ServerSocket(0);
		final int tcpPort = serverSocket.getLocalPort();

		final Thread udpThread = new Thread(new UdpDiscoveryService(UDP_PORT, tcpPort));
		udpThread.setDaemon(true);
		udpThread.start();

		System.out.println(InetAddress.getLocalHost().getHostAddress());

		while (true) {
			try {
				final Socket client = serverSocket.accept();
				System.out.println("Got connection");
				final InputStream is = client.getInputStream();
				final OutputStream os = client.getOutputStream();

				final byte[] handshakeBytes = new byte[4];
				is.read(handshakeBytes);
				System.out.println(Util.BYTES2HEX(handshakeBytes, 4));

				final ReadThread rt = new ReadThread(is);
				rt.start();

				int duration = 10_000;

				os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x00, (byte) (duration >> 8), (byte) duration});
//				os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x01, (byte) (duration >> 8), (byte) duration});
//				os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x02, (byte) (duration >> 8), (byte) duration});
//				os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x03, (byte) (duration >> 8), (byte) duration});

				//					Thread.sleep(5_000);
				//					
				//					os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x01, (byte) (duration >> 8), (byte) duration});
				//					
				//					Thread.sleep(5_000);
				//					
				//					os.write(new byte[] {MRP.TOGGLE_OUT_PIN.byteVal, 0x00, 0x03, 0x02, (byte) (duration >> 8), (byte) duration});
				//					

//				os.write(new byte[] {MRP.RESET.byteVal});
				//					System.out.println("we wait");
				//					Thread.sleep(10_000);
				//					System.out.println("end of wait");

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			};
		}
	}

	private static class ReadThread extends Thread {

		private final InputStream is;
		private boolean run = true;

		private ReadThread(final InputStream is) {
			this.is = is;
		}

		public void end() {
			this.run = false;
		}

		@Override
		public void run() {
			final byte[] header = new byte[3];
			final byte[] payload = new byte[65535];
			try {
				while (run)
				{
					int read = is.read(header);
					if (read != 3) {
						if (read == -1) {
							System.out.println("Input stream no longer available");
							run = false;
							break;
						}
						throw new IllegalStateException("Invalid number of header bytes " + read);
					}
					System.out.println(Util.BYTES2HEX(header, 3));

					final int len = header[1] << 8 | header[2];
					System.out.println("Reading " + len + " bytes");

					read = is.read(payload, 0, len);
					if (read != len) {
						System.err.println("Didn't receive whole payload, expected " + len + " but got " + read);
					}
					if (header[0] == MRP.STATUS_MSG.byteVal) {
						System.out.println(new String(payload, 0, read));
					} else {
						System.err.println(new String(payload, 0, read));
					}
					System.out.println(Util.BYTES2HEX(payload, read));
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
