import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CONCURRENCY UPDATE:
 * - log() method is synchronized.
 * - ADDED: Static methods for the GUI to read log files.
 */
public class LoggerUtil {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyy_HHmmss");
    private static final DateTimeFormatter LOG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");

    private final String logType;
    private final File logDir = new File("Logs");
    private final File logFile;

    public LoggerUtil(String logType) {
        this.logType = logType;
        if (!logDir.exists()) logDir.mkdirs();

        String filename = LocalDateTime.now().format(FILE_DATE_FORMAT) + "-" + logType + ".txt";
        this.logFile = new File(logDir, filename);

        handleExistingLog();
        createNewLogHeader();
    }

    private void handleExistingLog() {
        // This logic is unchanged...
        File[] existingLogs = logDir.listFiles((dir, name) -> name.endsWith("-" + logType + ".txt"));
        if (existingLogs == null || existingLogs.length == 0) return;

        // Don't archive for this demo, just let them co-exist
        // This simplifies the log viewer
    }

    private void createNewLogHeader() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write("==== Log started at [" + LocalDateTime.now().format(LOG_DATE_FORMAT) + "] ====");
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error creating new log header: " + e.getMessage());
        }
    }

    public synchronized void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
        String logEntry = "[" + timestamp + "] " + message;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
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
     * GUI INTEGRATION: Gets all log file names from the "Logs" directory.
     */
    public static List<String> getLogFiles() {
        File logDir = new File("Logs");
        if (!logDir.exists() || !logDir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = logDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return Collections.emptyList();
        }

        return java.util.Arrays.stream(files)
                .map(File::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * GUI INTEGRATION: Reads the entire content of a log file into a String.
     */
    public static String getLogContent(String logName) {
        File file = new File("Logs", logName);
        if (!file.exists()) {
            return "Error: Log file not found: " + logName;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            return "Error reading log: " + e.getMessage();
        }
        return content.toString();
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
        return logFile.getAbsolutePath();
    }

    public String getLogType() {
        return logType;
    }
}