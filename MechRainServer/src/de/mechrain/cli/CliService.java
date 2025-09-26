package de.mechrain.cli;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import de.mechrain.Server;
import de.mechrain.log.CliAppender;

public class CliService implements Runnable {
	
	private final CliAppender appender;
	private final ServerSocket cliSocket;
	private final Server server;

	public CliService(final CliAppender appender, final ServerSocket cliSocket, final Server server) {
		this.appender = appender;
		this.cliSocket = cliSocket;
		this.server = server;
	}

	@Override
	public void run() {
		while (true) {
			try {
				final Socket socket = cliSocket.accept();
				appender.addSink(new CliConnector(socket, appender, server));
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}
}
