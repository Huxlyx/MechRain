package de.mechrain.cli.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BootstrapMain {

	private static final Path CURRENT_DIR = Paths.get("current");
	private static final Path BACKUP_DIR = Paths.get("backup");


	public static void main(final String[] args) throws InterruptedException, IOException {

		if ( ! CURRENT_DIR.toFile().exists()) {
			/* initial setup */
			System.out.println("Performing initial setup...");
			if ( ! CURRENT_DIR.toFile().mkdir()) {
				System.err.println("Could not create directory for run executable!");
				System.exit(1);
			}
		}

		if ( ! BACKUP_DIR.toFile().exists() && ! BACKUP_DIR.toFile().mkdir()) {
			System.err.println("Could not create directory for backup executables!");
			System.exit(1);
		}

		boolean connectToTestServer = false;

		for (final String arg : args) {
			if (arg.equalsIgnoreCase("--install")) {
				try {
					addToUserPath(CURRENT_DIR.toAbsolutePath().toString());
				} catch (final Exception e) {
					System.err.println("Could not add to user PATH variable!");
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println("Successfully added to user PATH variable!");
				System.exit(0);
			} else if (arg.equalsIgnoreCase("--update")) {
				//				Updater.performUpdate(CURRENT_DIR, CURRENT_BAK);
				//				System.exit(0);
			} else if (arg.equalsIgnoreCase("--test")) {
				System.out.println("Connecting to test server...");
				connectToTestServer = true;
			} else {
				System.err.println("Unknown argument: " + arg);
				System.exit(1);
			}
		}

		final File[] listFiles = CURRENT_DIR.toFile().listFiles();

		if (listFiles == null || listFiles.length == 0) {
			System.err.println("No application found in 'current' directory!");
			System.exit(1);
		}

		if (listFiles.length > 1) {
			System.err.println("Multiple files found in 'current' directory! Cannot determine which to run.");
			System.exit(1);
		}

		final Path appJarPath = listFiles[0].toPath();

		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		cmd.add("-jar");
		cmd.add(appJarPath.toString());
		if (connectToTestServer) {
			cmd.add("--test");
		}

		new ProcessBuilder(cmd)
			.inheritIO()
			.start()
			.waitFor();
	}	

	public static void addToUserPath(String dir) throws Exception {
		Process check = new ProcessBuilder(
				"cmd", "/c", "echo", "%PATH%"
				).start();

		String path;
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(check.getInputStream()))) {
			path = br.readLine();
		}

		if (path != null && path.toLowerCase().contains(dir.toLowerCase())) {
			return; // already present
		}

		new ProcessBuilder(
				"cmd", "/c",
				"setx", "PATH", dir + ";%PATH%"
				).inheritIO().start().waitFor();
	}

}
