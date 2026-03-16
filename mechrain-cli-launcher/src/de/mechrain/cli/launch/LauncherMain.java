package de.mechrain.cli.launch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LauncherMain {

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
				try {
					performUpdate(CURRENT_DIR, BACKUP_DIR);
					System.out.println("Update completed successfully!");
				} catch (final Exception e) {
					System.err.println("Could not perform update!");
					e.printStackTrace();
					System.exit(1);
				}
				System.exit(0);
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
		final String path = System.getenv("PATH");

		if (path != null && path.toLowerCase().contains(dir.toLowerCase())) {
			System.out.println("Directory already in PATH variable.");
			return; // already present
		}


		new ProcessBuilder(
				"cmd", "/c",
				"setx", "PATH", dir + ";" + path
				).inheritIO().start().waitFor();
	}

	public static void performUpdate(Path currentDir, Path backupDir) throws Exception {
		final File[] listFiles = currentDir.toFile().listFiles();

		if (listFiles == null || listFiles.length != 1) {
			throw new IOException("Expected exactly one JAR file in current directory");
		}

		final File currentJar = listFiles[0];
		final String filename = currentJar.getName();

		// Backup current JAR
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		final Path backupPath = backupDir.resolve(filename.replace(".jar", "_" + timestamp + ".jar"));
		Files.copy(currentJar.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Backed up current version to: " + backupPath);

		// Download latest release from GitHub
		final String latestReleaseUrl = "https://api.github.com/repos/MechRain/mechrain/releases/latest";
		final String downloadUrl = getLatestCliReleaseUrl(latestReleaseUrl);

		if (downloadUrl == null) {
			throw new IOException("Could not find mechrain-cli JAR in latest GitHub release");
		}

		System.out.println("Downloading from: " + downloadUrl);
		downloadFile(downloadUrl, currentJar.getAbsolutePath());
		System.out.println("Successfully downloaded and installed new version");
	}

	private static String getLatestCliReleaseUrl(String apiUrl) throws Exception {
		final URL url = new URL(apiUrl);
		final InputStream input = url.openStream();
		final String response = new String(input.readAllBytes());
		input.close();

		// Simple JSON parsing to find mechrain-cli JAR URL
		// Looking for "browser_download_url" containing "mechrain-cli"
		final String searchStr = "\"browser_download_url\":\"";
		int index = response.indexOf(searchStr);

		while (index != -1) {
			final int startIdx = index + searchStr.length();
			final int endIdx = response.indexOf("\"", startIdx);
			final String downloadUrl = response.substring(startIdx, endIdx);

			if (downloadUrl.contains("mechrain-cli") && downloadUrl.endsWith(".jar")) {
				return downloadUrl;
			}

			index = response.indexOf(searchStr, endIdx);
		}

		return null;
	}

	private static void downloadFile(String urlString, String destination) throws Exception {
		final URL url = new URL(urlString);
		try (final InputStream in = url.openStream();
				final FileOutputStream out = new FileOutputStream(destination)) {
			final byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}
	}

}
