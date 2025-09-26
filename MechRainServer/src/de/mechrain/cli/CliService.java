package de.mechrain.log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class CliService implements Runnable {
	
	private final CliAppender appender;
	private final ServerSocket cliSocket;

	public CliService(final CliAppender appender, final ServerSocket cliSocket) {
		this.appender = appender;
		this.cliSocket = cliSocket;
	}

	@Override
	public void run() {
		while (true) {
			try {
				final Socket socket = cliSocket.accept();
				appender.addSink(new CliConnector(socket, appender));
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}
}
