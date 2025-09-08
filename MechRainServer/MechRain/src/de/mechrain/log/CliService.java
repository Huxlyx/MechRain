package de.mechrain.log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CliService implements Runnable {
	
	private static final Logger LOG = LogManager.getLogger(Logging.CLI);
	
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
				
				LOG.fatal("LOG-TEST (fatal)");
				LOG.error("LOG-TEST");
				LOG.warn("LOG-TEST");
				LOG.info("LOG-TEST");
				LOG.debug("LOG-TEST");
				LOG.trace("LOG-TEST");
			} catch (final IOException e) {
				e.printStackTrace();
			}
			
		}
	}

}
