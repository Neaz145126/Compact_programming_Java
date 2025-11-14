import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CONCURRENCY UPDATE:
 * - log() method is synchronized.
 * - ADDED: Static methods for the GUI to read log files.
 *
 * REFACTOR:
 * - Migrated from 'java.io.File' to 'java.nio.file.Path' for modern file handling.
 * - Implemented 'handleExistingLog' to move old logs to an 'Archive' sub-directory.
 * - Simplified static helper methods using new file APIs.
 */
public class LoggerUtil {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyy_HHmmss");
    private static final DateTimeFormatter LOG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");

    private final String logType;
    private final Path logDir = Path.of("Logs");
    private final Path logFile;

    public LoggerUtil(String logType) {
        this.logType = logType;
        try {
            // Ensure the main "Logs" directory exists
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (IOException e) {
            System.err.println("CRITICAL: Could not create Logs directory at " + logDir.toAbsolutePath());
            // Fallback to a non-existent file, write operations will fail
            this.logFile = logDir.resolve("error.txt");
            return;
        }

        // Create the new log file path
        String filename = LocalDateTime.now().format(FILE_DATE_FORMAT) + "-" + logType + ".txt";
        this.logFile = logDir.resolve(filename);

        // --- This is the new, implemented logic ---
        handleExistingLog();
        // ---

        createNewLogHeader();
    }

    /**
     * REFACTORED: This method now moves old logs to an "Archive" folder
     * to keep the main "Logs" directory clean.
     */
    private void handleExistingLog() {
        Path archiveDir = logDir.resolve("Archive");
        try {
            // 1. Ensure the "Archive" directory exists
            if (!Files.exists(archiveDir)) {
                Files.createDirectories(archiveDir);
            }

            // 2. Find all old log files matching our logType
            String logSuffix = "-" + logType + ".txt";
            List<Path> oldLogs;
            try (Stream<Path> stream = Files.list(logDir)) {
                oldLogs = stream
                        .filter(Files::isRegularFile) // Make sure it's a file
                        .filter(path -> path.getFileName().toString().endsWith(logSuffix))
                        .collect(Collectors.toList());
            }

            // 3. Move them to the archive
            for (Path oldLog : oldLogs) {
                // Use the same filename, but inside the archive directory
                Path destination = archiveDir.resolve(oldLog.getFileName());
                Files.move(oldLog, destination);
            }

            if (!oldLogs.isEmpty()) {
                System.out.println("Archived " + oldLogs.size() + " old logs for " + logType);
            }

        } catch (IOException e) {
            System.err.println("Warning: Could not archive old logs: " + e.getMessage());
        }
    }

    /**
     * REFACTORED: Uses 'Files.newBufferedWriter' for a cleaner try-with-resources block.
     */
    private void createNewLogHeader() {
        String header = "==== Log started at [" + LocalDateTime.now().format(LOG_DATE_FORMAT) + "] ====";

        // Using 'CREATE' and 'APPEND' ensures the file is created if it doesn't exist
        // and we write to the end of it.
        try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(header);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error creating new log header: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * REFACTORED: Uses 'Files.newBufferedWriter' and modern open options.
     * This method is synchronized to prevent race conditions from multiple threads
     * writing to the same log file instance.
     */
    public synchronized void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
        String logEntry = "[" + timestamp + "] " + message;

        // Using 'CREATE' (in case header failed) and 'APPEND' is the correct
        // combination for logging.
        try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Helper methods (unchanged) ---
    public void logRobotStatus(String robotId, String status, int battery) {
        log("Robot " + robotId + " - Status: " + status + ", Battery level " + battery + "%");
    }

    public void logTaskReceived(String robotId, String taskId) {
        log("Robot " + robotId + " - Received task " + taskId);
    }

    public void logTaskCompleted(String robotId, String taskId) {
        log("Robot " + robotId + " - Completed task " + taskId);
    }

    // --- Static utility methods ---

    /**
     * REFACTORED: GUI INTEGRATION. Gets all log file names from the "Logs" directory.
     * Uses 'Files.list()' and filters out the "Archive" directory itself.
     */
    public static List<String> getLogFiles() {
        Path logDir = Path.of("Logs");
        if (!Files.isDirectory(logDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(logDir)) {
            return stream
                    .filter(Files::isRegularFile) // Only include files
                    .filter(path -> path.getFileName().toString().endsWith(".txt")) // Only .txt files
                    .map(path -> path.getFileName().toString()) // Get just the name
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading log directory: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * REFACTORED: GUI INTEGRATION. Reads the entire content of a log file.
     * This is now much simpler and more efficient using 'Files.readString'.
     */
    public static String getLogContent(String logName) {
        Path file = Path.of("Logs", logName);
        if (!Files.exists(file)) {
            return "Error: Log file not found: " + logName;
        }

        try {
            // This one-liner replaces the entire StringBuilder/BufferedReader loop
            return Files.readString(file);
        } catch (IOException e) {
            return "Error reading log: " + e.getMessage();
        }
    }

    // ... viewLog and deleteLogs methods are unchanged but no longer used by the GUI ...

    public static void viewLog(String logName) {
        // ...
    }

    public static void deleteLogs(String target) {
        // ...
    }

    private static void deleteRecursively(File file) {
        // ...
    }

    // --- Getters (unchanged) ---
    public String getLogPath() {
        return logFile.toAbsolutePath().toString();
    }

    public String getLogType() {
        return logType;
    }
}