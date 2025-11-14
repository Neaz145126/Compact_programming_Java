import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggerUtil {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyy_HHmmss");
    private static final DateTimeFormatter LOG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");

    private final String logType;
    private final File logDir = new File("Logs");
    private final File logFile;

    /**
     * Creates a new logger for a specific log type (e.g. "RobotLog", "TaskLog", "SystemLog").
     * If a log already exists for this type, it will be archived in /Logs/<date>/ before a new one is created.
     */
    public LoggerUtil(String logType) {
        this.logType = logType;
        if (!logDir.exists()) logDir.mkdirs();

        String filename = LocalDateTime.now().format(FILE_DATE_FORMAT) + "-" + logType + ".txt";
        this.logFile = new File(logDir, filename);

        // This method is only called from the constructor, which is single-threaded,
        // so it doesn't need to be synchronized.
        handleExistingLog();
        createNewLogHeader();
    }

    /** Moves existing log to /Logs/<date>/ if one already exists for the same logType */
    private void handleExistingLog() {
        File[] existingLogs = logDir.listFiles((dir, name) -> name.endsWith("-" + logType + ".txt"));
        if (existingLogs == null || existingLogs.length == 0) return;

        String dateFolderName = LocalDate.now().toString(); // e.g. "2025-10-31"
        File dateDir = new File(logDir, dateFolderName);
        if (!dateDir.exists()) dateDir.mkdirs();

        for (File oldLog : existingLogs) {
            try {
                Files.move(oldLog.toPath(),
                        new File(dateDir, oldLog.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Archived old log: " + oldLog.getName());
            } catch (IOException e) {
                System.err.println("Error archiving old log: " + e.getMessage());
            }
        }
    }

    /** Writes the log header line when a new file is created */
    private void createNewLogHeader() {
        // This is only called from the constructor, but we'll make it private and
        // ensure it's not called from multiple threads.
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

    // --- Helper methods for robot logging (they all call the synchronized 'log' method) ---
    public void logRobotStatus(String robotId, String status, int battery) {
        log("Robot " + robotId + " - Status: " + status + ", Battery level " + battery + "%");
    }

    public void logTaskReceived(String robotId, String taskId) {
        log("Robot " + robotId + " - Received task " + taskId);
    }

    public void logTaskCompleted(String robotId, String taskId) {
        log("Robot " + robotId + " - Completed task " + taskId);
    }

    // --- Static utility methods for viewing and deleting logs ---
    // These are typically called from the main thread at the end, not concurrently.

    /** Views the content of a specified log file */
    public static void viewLog(String logName) {
        File file = new File("Logs", logName.endsWith(".txt") ? logName : logName + ".txt");
        if (!file.exists()) {
            System.out.println("No log found with name: " + logName);
            return;
        }

        System.out.println("\n--- Viewing Log: " + file.getName() + " ---");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null)
                System.out.println(line);
        } catch (IOException e) {
            System.err.println("Error reading log: " + e.getMessage());
        }
        System.out.println("--- End of Log ---\n");
    }

    /** Deletes a specific log file or all logs */
    public static void deleteLogs(String target) {
        File logDir = new File("Logs");

        if (target.equalsIgnoreCase("all")) {
            deleteRecursively(logDir);
            logDir.mkdirs();
            System.out.println("All logs deleted.");
            return;
        }

        File targetFile = new File(logDir, target.endsWith(".txt") ? target : target + ".txt");
        if (targetFile.exists()) {
            targetFile.delete();
            System.out.println("Deleted log: " + targetFile.getName());
        } else {
            System.out.println("No log found with name: " + target);
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File sub : files) deleteRecursively(sub);
            }
        }
        file.delete();
    }

    // --- Getters ---
    public String getLogPath() {
        return logFile.getAbsolutePath();
    }

    public String getLogType() {
        return logType;
    }
}