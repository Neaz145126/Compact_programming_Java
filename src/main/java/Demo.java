import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Demo {

    public static void main(String[] args) {

        // --- 1. SETUP PARTS ---
        System.out.println("--- Initializing Parts ---");
        List<Part> partDefinitions = createSampleParts();

        // --- 2. SETUP INVENTORY ---
        System.out.println("--- Initializing Inventory ---");
        Inventory inventory = getInventory(partDefinitions);
        inventory.printInventory();

        // --- 3. SETUP WAREHOUSE ---
        Warehouse warehouse = new Warehouse("WH-01", "Main Warehouse", inventory, 10, 5);

        // ---ADD INITIAL REQUESTS (via Character Stream) ---
        System.out.println("\n--- Writing Initial Requests to file (Stream) ---");
        try (PrintWriter writer = new PrintWriter(new FileWriter("pending_requests.txt"))) {
            writer.println("P1001,5");    // Oil Filter
            writer.println("P1002,10");   // Air Filter
            writer.println("P1003,15");  // Spark Plug
            writer.println("P1015,5");    // Tire
        } catch (IOException e) {
            System.err.println("Error writing initial requests: " + e.getMessage());
        }

        // --- 5. RUN THE SIMULATION ---
        System.out.println("\n=== STARTING WAREHOUSE SIMULATION ===");
        for (int i = 1; i <= 30; i++) {
            System.out.println("\n--- TICK " + i + " ---");
            warehouse.update();

            // Add a new request every 5 ticks
            if (i % 5 == 0) {
                System.out.println(">>> New Request Arrived! (Writing to file) <<<");
                try (PrintWriter writer = new PrintWriter(new FileWriter("pending_requests.txt", true))) {
                    writer.println("P1003,2"); // New spark plug request
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try (PrintWriter writer = new PrintWriter(new FileWriter("pending_requests.txt", true))) {
                    writer.println("P1018,3"); // New Exhaust request
                } catch (IOException e) {
                    System.err.println("Error writing new request: " + e.getMessage());
                }
            }
        }

        System.out.println("\n=================================");
        System.out.println("=== SIMULATION FINISHED ===");
        System.out.println("=================================\n");

        // --- 6. PRINT FINAL INVENTORY ---
        inventory.printInventory();

        // --- 7. WRITE FINAL REPORT (via Byte Stream) ---
        warehouse.writeFinalReport();

        // --- 8. READ FINAL REPORT (MINIMAL CHANGE) ---
        // This demonstrates the Byte Stream read, fulfilling the ReportReader.java requirement.
        readFinalReport();

        // --- 9. INTERACTIVE LOG VIEWER (MINIMAL CHANGE) ---
        // This fulfills the final requirements for user input and regex.
        runLogViewer();

        System.out.println("\nApplication finished.");
    }

    /**
     * Reads the binary report file.
     * This logic is from ReportReader.java.
     */
    private static void readFinalReport() {
        String filename = "completed_report.dat";
        System.out.println("\n--- Reading Binary Report (" + filename + ") ---");

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
            int requestCount = dis.readInt();
            System.out.println("Found " + requestCount + " total requests in the report.");
            System.out.println("----------------------------------------------");

            for (int i = 0; i < requestCount; i++) {
                String requestID = dis.readUTF();
                String partID = dis.readUTF();
                int quantity = dis.readInt();
                String status = dis.readUTF();
                System.out.println("  > ID: " + requestID + ", Part: " + partID + ", Qty: " + quantity + ", Status: " + status);
            }
        } catch (IOException e) {
            System.err.println("Error reading report file: " + e.getMessage());
        }
    }

    /**
     * REQUIREMENT: Interactive menu to view/delete logs using Regex.
     */
    private static void runLogViewer() {
        System.out.println("\n--- INTERACTIVE LOG VIEWER ---");

        // REGEX: Matches equipment IDs (R-001, CS-A, etc.) or a 6-digit date (ddMMyy)
        Pattern logPattern = Pattern.compile(
                "^(R-\\d{3}|CS-[A-Z]|Warehouse-WH-01|InventoryLog|\\d{6})$",
                Pattern.CASE_INSENSITIVE
        );

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nEnter Equipment ID (R-001), Station (CS-A), Date (ddMMyy), or 'exit':");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting log viewer...");
                break;
            }

            // REGEX: Check if input matches the allowed pattern
            Matcher matcher = logPattern.matcher(input);
            if (matcher.matches()) {
                findAndShowLogs(input);
            } else {
                System.out.println("Invalid format. Examples: R-002, CS-B, 131125, or exit");
            }
        }
    }

    /**
     * REQUIREMENT: Finds and displays logs based on the validated input.
     */
    private static void findAndShowLogs(String query) {
        File logDir = new File("Logs");
        if (!logDir.exists()) {
            System.out.println("Log directory not found.");
            return;
        }

        // Check if the query is a date (all digits) or an equipment name
        boolean isDateQuery = query.matches("^\\d{6}$");
        int foundCount = 0;

        File[] logFiles = logDir.listFiles();
        if (logFiles == null) return;

        for (File logFile : logFiles) {
            String filename = logFile.getName();
            boolean match = false;

            if (isDateQuery) {
                // Find by date: filename starts with "131125"
                if (filename.startsWith(query)) {
                    match = true;
                }
            } else {
                // Find by equipment: filename ends with "-R-001.txt"
                if (filename.endsWith("-" + query + ".txt")) {
                    match = true;
                }
            }

            if (match) {
                LoggerUtil.viewLog(filename);
                foundCount++;
            }
        }

        if (foundCount == 0) {
            System.out.println("No logs found matching query: " + query);
        }
    }


    // --- Original Setup Methods (Unchanged) ---

    private static Inventory getInventory(List<Part> partDefinitions) {
        Map<Part, Integer> initialStock = new HashMap<>();
        Part oilFilter = partDefinitions.get(0);
        Part airFilter = partDefinitions.get(1);
        Part sparkPlug = partDefinitions.get(2);

        initialStock.put(oilFilter, 25);
        initialStock.put(airFilter, 30);
        initialStock.put(sparkPlug, 50);

        return new Inventory(500, initialStock);
    }

    private static List<Part> createSampleParts() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("P1001", "Oil Filter", "Standard oil filter"));
        parts.add(new Part("P1002", "Air Filter", "Engine air filter"));
        parts.add(new Part("P1003", "Spark Plug", "Iridium spark plug"));
        parts.add(new Part("P1004", "Brake Pad", "Front ceramic pads"));
        parts.add(new Part("P1005", "Brake Disc", "Vented front brake disc"));
        parts.add(new Part("P1006", "Wiper Blade", "22-inch all-weather"));
        parts.add(new Part("P1007", "Headlight Bulb", "H4 Halogen bulb"));
        parts.add(new Part("P1A008", "Taillight Bulb", "P21W bulb"));
        parts.add(new Part("P1009", "Battery", "12V 60Ah AGM battery"));
        parts.add(new Part("P1010", "Alternator", "120A alternator"));
        parts.add(new Part("P1S11", "Starter Motor", "1.4kW starter"));
        parts.add(new Part("P1012", "Timing Belt", "Rubber timing belt kit"));
        parts.add(new Part("P1013", "Water Pump", "Coolant water pump"));
        parts.add(new Part("P1014", "Radiator", "Aluminum core radiator"));
        parts.add(new Part("P1015", "Tire", "205/55R16 All-Season"));
        parts.add(new Part("P1016", "Wheel Rim", "16-inch alloy rim"));
        parts.add(new Part("P1017", "Shock Absorber", "Front gas shock"));
        parts.add(new Part("P1018", "Exhaust Muffler", "Stainless steel muffler"));
        parts.add(new Part("P1019", "Catalytic Converter", "OEM spec converter"));
        parts.add(new Part("P1020", "Fuel Injector", "Bosch fuel injector"));
        return parts;
    }
}