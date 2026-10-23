package de.mechrain.web;

import java.io.IOException;

/**
 * Mechrain Web – Hauptklasse.
 *
 * <p>Verbindet sich automatisch mit dem Mechrain-Server (UDP-Discovery + TCP/Fory),
 * pollt Geräte/Signale und stellt die HTTP-API sowie das statische Web-UI bereit
 * unter {@code http://localhost:8080}.
 *
 * <p>Aufruf:
 * <pre>
 *   java -jar mechrain-web-1.1.0-all.jar [--port 8080] [--discovery-port 5000] [--test] [--read-only]
 * </pre>
 *
 * <ul>
 *   <li>{@code --port} – HTTP-Port des Web-UI (Default 8080)</li>
 *   <li>{@code --discovery-port} – UDP-Port der Server-Discovery (Default 5000)</li>
 *   <li>{@code --test} – Test-Modus: Discovery-Payloads {@code CLI-TEST}/{@code MECH-RAIN-TEST}
 *       statt {@code CLI-HELLO}/{@code MECH-RAIN-HELLO}</li>
 *   <li>{@code --read-only} – Nur Anzeige (keine Signal-Änderungen); erlaubt ältere
 *       Server ab Protokollversion 2 (ohne Gate-Zuweisungs-Shortcuts)</li>
 * </ul>
 */
public final class WebServerMain {

	private WebServerMain() {
	}

	public static void main(final String[] args) throws IOException {
		int httpPort = 8080;
		int discoveryPort = 5000;
		boolean testMode = false;
		boolean readOnly = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--port") && i + 1 < args.length) {
				httpPort = Integer.parseInt(args[i + 1]);
			} else if (args[i].equals("--discovery-port") && i + 1 < args.length) {
				discoveryPort = Integer.parseInt(args[i + 1]);
			} else if (args[i].equals("--test")) {
				testMode = true;
			} else if (args[i].equals("--read-only")) {
				readOnly = true;
			}
		}

		// Volle Funktionalität braucht Protokoll >= 3 (Gate-Shortcuts);
		// Nur-Lese-Modus funktioniert gegen ältere Server ab Protokoll >= 2.
		final MechrainClient client = new MechrainClient(discoveryPort, testMode, readOnly ? 2 : 4);
		final WebState state = new WebState();
		state.setReadOnly(readOnly);
		final StatePoller poller = new StatePoller(client, state);
		final SignalFlowDriver driver = new SignalFlowDriver(client, state);
		final HttpApi api = new HttpApi(httpPort, state, client, driver);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			api.stop();
			poller.stop();
			client.close();
		}, "WebShutdown"));

		client.start();
		poller.start();
		api.start();
		System.out.println("MechRain Web laeuft auf http://localhost:" + httpPort
				+ (testMode ? " (Test-Modus)" : "")
				+ (readOnly ? " (Nur-Lese-Modus)" : ""));
	}
}
