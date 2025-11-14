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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Demo {

    // --- CONCURRENCY: Simulation Parameters ---
    // Simulate M tasks with K available AGV (Robots)
    private static final int K_ROBOTS = 20;
    // Simulate N charging stations
    private static final int N_STATIONS = 1;
    // How long to run the simulation for (in milliseconds)
    private static final int SIMULATION_DURATION_MS = 60000; // 30 seconds

    public static void main(String[] args) {

        // --- 1. SETUP PARTS ---
        System.out.println("--- Initializing Parts ---");
        List<Part> partDefinitions = createSampleParts();

        // --- 2. SETUP INVENTORY ---
        System.out.println("--- Initializing Inventory ---");
        Inventory inventory = getInventory(partDefinitions);
        inventory.printInventory();

        // --- 3. SETUP WAREHOUSE (The Conductor) ---
        // CONCURRENCY: Pass K and N to the constructor
        Warehouse warehouse = new Warehouse("WH-01", "Main Warehouse", inventory, K_ROBOTS, N_STATIONS);

        // --- 4. ADD INITIAL REQUESTS (via Character Stream) ---
        System.out.println("\n--- Writing Initial Requests to file (Stream) ---");
        try (PrintWriter writer = new PrintWriter(new FileWriter("pending_requests.txt"))) {
            writer.println("P1001,5");    // Oil Filter
            writer.println("P1002,10");   // Air Filter
            writer.println("P1003,15");  // Spark Plug
            writer.println("P1015,5");    // Tire
            writer.println("P1001,5");
            writer.println("P1002,10");
            writer.println("P1003,15");
            writer.println("P1015,5");
            writer.println("P1001,5");
            writer.println("P1002,10");
            writer.println("P1003,15");
            writer.println("P1015,5");
        } catch (IOException e) {
            System.err.println("Error writing initial requests: " + e.getMessage());
        }

        // --- 5. RUN THE CONCURRENT SIMULATION ---
        System.out.println("\n=== STARTING WAREHOUSE SIMULATION (" + SIMULATION_DURATION_MS / 1000 + "s) ===");

        // Create a thread pool to manage all our Runnables
        // K robots + N stations + 1 PartRequestManager
        ExecutorService executor = Executors.newFixedThreadPool(K_ROBOTS + N_STATIONS + 1);

        // Tell the warehouse to submit all its tasks
        warehouse.startSimulation(executor);

        // --- ADD NEW REQUESTS DURING SIMULATION ---
        // We'll add new requests after 10 seconds
        try {
            Thread.sleep(10000);
            System.out.println(">>> New Requests Arrived! (Writing to file) <<<");
            try (PrintWriter writer = new PrintWriter(new FileWriter("pending_requests.txt", true))) {
                writer.println("P1003,2"); // New spark plug request
                writer.println("P1018,3"); // New Exhaust request
            }
        } catch (Exception e) {
            System.err.println("Error writing new requests: " + e.getMessage());
        }


        // --- WAIT FOR SIMULATION TO END ---
        try {
            // Let the simulation run for the specified duration
            Thread.sleep(SIMULATION_DURATION_MS - 10000); // Already slept 10s
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted.");
        }

        // --- 6. SHUTDOWN THE SIMULATION ---
        System.out.println("\n=================================");
        System.out.println("=== SIMULATION TIME ELAPSED ===");
        System.out.println("=================================\n");

        // Stop the Warehouse loops
        warehouse.stopSimulation();

        // Tell the executor to stop accepting new tasks and interrupt all threads
        executor.shutdownNow();
        try {
            // Wait for all threads to terminate
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Threads did not terminate gracefully.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        System.out.println("All simulation threads stopped.");

        // --- 7. PRINT FINAL INVENTORY ---
        inventory.printInventory();

        // --- 8. WRITE FINAL REPORT (via Byte Stream) ---
        warehouse.writeFinalReport();

        // --- 9. READ FINAL REPORT ---
        readFinalReport();

        // --- 10. INTERACTIVE LOG VIEWER ---
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
                "^(R-\\d{3}|CS-[A-Z]|Warehouse-WH-01|InventoryLog|PartRequestManager|\\d{6})$",
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
        Part exhaust = partDefinitions.get(17); // P1018
        Part tire = partDefinitions.get(14); // P1015


        initialStock.put(oilFilter, 25);
        initialStock.put(airFilter, 30);
        initialStock.put(sparkPlug, 50);
        initialStock.put(exhaust, 10);
        initialStock.put(tire, 20);

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