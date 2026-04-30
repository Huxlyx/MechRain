package de.mechrain.cli.launch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
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
	
	private static final String RELEASE_API_URL = "https://api.github.com/repos/Huxlyx/MechRain/releases/latest";


	public static void main(final String[] args) throws InterruptedException, IOException {
		
		if ( ! CURRENT_DIR.toFile().exists()) {
			System.out.println("Performing initial setup...");
			if ( ! CURRENT_DIR.toFile().mkdir()) {
				System.err.println("Could not create directory for run executable!");
				System.exit(1);
			}
			if ( ! BACKUP_DIR.toFile().exists() && ! BACKUP_DIR.toFile().mkdir()) {
				System.err.println("Could not create directory for backup executables!");
				System.exit(1);
			}
			try {
				System.out.println("Downloading latest CLI version...");
				downloadLatest(CURRENT_DIR);
			} catch (final Exception e) {
				System.err.println("Could not download latest CLI version!");
				e.printStackTrace();
				System.exit(1);
			}
		} else if ( ! BACKUP_DIR.toFile().exists() && ! BACKUP_DIR.toFile().mkdir()) {
			System.err.println("Could not create directory for backup executables!");
			System.exit(1);
		}

		boolean connectToTestServer = false;

		for (final String arg : args) {
			if (arg.equalsIgnoreCase("--install")) {
				try {
					final String installDir = Paths.get("").toAbsolutePath().normalize().toString();
					writeBatchFile(Paths.get("mechrain.bat"));
					System.out.println("Created mechrain.bat in install directory.");
					addToUserPath(installDir);
				} catch (final Exception e) {
					System.err.println("Could not complete installation!");
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println("Successfully installed! Open a new terminal and type 'mechrain' to start.");
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

	private static void downloadLatest(final Path targetDir) throws Exception {
		final String downloadUrl = getLatestCliReleaseUrl(RELEASE_API_URL);
		if (downloadUrl == null) {
			throw new IOException("Could not find mechrain-cli JAR in latest GitHub release");
		}
		final String filename = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
		final Path dest = targetDir.resolve(filename);
		System.out.println("Downloading from: " + downloadUrl);
		downloadFile(downloadUrl, dest.toString());
	}

	private static void writeBatchFile(final Path batchPath) throws IOException {
		final String content =
			"@echo off\r\n"
			+ "cd /d \"%~dp0\"\r\n"
			+ "for %%f in (mechrain-cli-launcher-*.jar) do (\r\n"
			+ "    java -jar \"%%f\" %*\r\n"
			+ "    exit /b\r\n"
			+ ")\r\n"
			+ "echo ERROR: mechrain-cli-launcher JAR not found in %~dp0 1>&2\r\n"
			+ "exit /b 1\r\n";
		Files.writeString(batchPath, content);
	}

	public static void addToUserPath(final String dir) throws Exception {
		// setx has a 1024-character limit and corrupts PATH on long values.
		// PowerShell's SetEnvironmentVariable writes directly to the registry with no limit
		// and correctly operates on the user-level PATH only.
		final String psScript =
				"$dir = '" + dir.replace("'", "''") + "';"
				+ "$cur = [Environment]::GetEnvironmentVariable('PATH', 'User');"
				+ "if ([string]::IsNullOrEmpty($cur)) { $cur = '' };"
				+ "if ($cur.ToLower().Contains($dir.ToLower())) {"
				+ "  Write-Host 'Directory already in user PATH.';"
				+ "} else {"
				+ "  [Environment]::SetEnvironmentVariable('PATH', $dir + ';' + $cur, 'User');"
				+ "}";
		new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psScript)
				.inheritIO().start().waitFor();
	}

	public static void performUpdate(Path currentDir, Path backupDir) throws Exception {
		final File[] listFiles = currentDir.toFile().listFiles();

		if (listFiles == null || listFiles.length != 1) {
			throw new IOException("Expected exactly one JAR file in current directory");
		}

		final File currentJar = listFiles[0];
		final String currentFilename = currentJar.getName();

		// Resolve download URL first — fail early if unavailable
		final String downloadUrl = getLatestCliReleaseUrl(RELEASE_API_URL);
		if (downloadUrl == null) {
			throw new IOException("Could not find mechrain-cli JAR in latest GitHub release");
		}

		final String newFilename = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

		if (currentFilename.equals(newFilename)) {
			System.out.println("Already on latest version: " + currentFilename);
			return;
		}

		// Download to a temp file first so the old JAR stays intact if download fails
		final Path tempPath = currentDir.resolve(newFilename + ".tmp");
		System.out.println("Downloading from: " + downloadUrl);
		downloadFile(downloadUrl, tempPath.toString());

		// Backup current JAR (keep its original versioned name + timestamp)
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		final Path backupPath = backupDir.resolve(currentFilename.replace(".jar", "_" + timestamp + ".jar"));
		Files.copy(currentJar.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Backed up current version to: " + backupPath);

		// Remove old JAR and promote the downloaded file with the correct versioned name
		currentJar.delete();
		Files.move(tempPath, currentDir.resolve(newFilename));
		System.out.println("Updated to: " + newFilename);
	}

	private static String getLatestCliReleaseUrl(String apiUrl) throws Exception {
		final URL url = new URL(apiUrl);
		final String response;
		try (final InputStream input = url.openStream()) {
			response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

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

	private static void downloadFile(final String urlString, final String destination) throws Exception {
		final HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
		conn.setInstanceFollowRedirects(true);
		final long totalBytes = conn.getContentLengthLong();
		try (final InputStream in = conn.getInputStream();
				final FileOutputStream out = new FileOutputStream(destination)) {
			final byte[] buffer = new byte[32_192];
			long downloaded = 0;
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
				downloaded += bytesRead;
				printProgress(downloaded, totalBytes);
			}
			System.out.printf("%n  Done! (%s)%n", formatBytes(totalBytes > 0 ? totalBytes : downloaded));
		}
	}

	private static void printProgress(final long done, final long total) {
		if (total > 0) {
			final int pct = (int) (done * 100 / total);
			final int filled = pct * 30 / 100;
			final String bar = "█".repeat(filled) + "░".repeat(30 - filled);
			System.out.printf("\r  [%s] %3d%%  %s / %s", bar, pct, formatBytes(done), formatBytes(total));
		} else {
			System.out.printf("\r  Downloaded %s...", formatBytes(done));
		}
	}

	private static String formatBytes(final long bytes) {
		if (bytes < 1024L)             return bytes + " B";
		if (bytes < 1024L * 1024L)     return String.format("%.1f KB", bytes / 1024.0);
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}

}
