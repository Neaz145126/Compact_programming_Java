import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CONCURRENCY UPDATE: This class is now the main simulation backend.
 * - Creates and manages its own ExecutorService.
 * - Provides public getters for the GUI to poll for status.
 * - startSimulation() is a BLOCKING method that waits for shutdown.
 *
 * REFACTOR:
 * - Fixed bug in 'createStations' to use the 'count' parameter.
 * - Modernized 'writeFinalReport' to use 'java.nio.file.Path'.
 */
public class Warehouse {

    private final String warehouseID;
    private final String name;
    private final LoggerUtil logger;

    // --- Shared Resources ---
    private final Inventory inventory;
    private final PartRequestManager requestManager;
    private final ConcurrentHashMap<String, PartRequest> allRequests;
    private final BlockingQueue<Robot> chargingQueue;

    // --- Simulation Components (Threads) ---
    private final List<Robot> robots;
    private final List<ChargingStation> stations;

    // --- GUI Control ---
    private ExecutorService executor;
    private volatile boolean simulationRunning = false;

    public Warehouse(int robotCount, int stationCount) {
        this.warehouseID = "WH-01";
        this.name = "Main Warehouse";
        this.logger = new LoggerUtil("Warehouse-" + warehouseID);

        // 1. Create Parts and Initial Stock
        List<Part> partDefs = PartDefinitions.createSampleParts();
        this.inventory = new Inventory(500, PartDefinitions.getInitialStock(partDefs));

        // 2. Create Shared Resources
        this.allRequests = new ConcurrentHashMap<>();
        this.chargingQueue = new LinkedBlockingQueue<>();
        this.requestManager = new PartRequestManager(inventory);

        // 3. Create Components
        this.robots = new ArrayList<>();
        this.stations = new ArrayList<>();
        createRobots(robotCount);
        createStations(stationCount); // This will now use stationCount correctly

        logger.log("Warehouse " + warehouseID + " (" + name + ") initialized with "
                + robots.size() + " robots and " + stations.size() + " stations.");
    }

    /**
     * This is the main simulation loop. This is a BLOCKING method.
     * It will not return until stopSimulation() is called.
     */
    public void startSimulation() {
        if (simulationRunning) {
            logger.log("Simulation already running.");
            return;
        }

        // Create a thread pool for all our components
        // +1 for the PartRequestManager
        int poolSize = robots.size() + stations.size() + 1;
        executor = Executors.newFixedThreadPool(poolSize);
        simulationRunning = true;
        logger.log("=== STARTING WAREHOUSE SIMULATION (Pool size: " + poolSize + ") ===");

        // Start all component threads
        // We use 'forEach' for a cleaner, more modern syntax
        stations.forEach(executor::submit);
        robots.forEach(executor::submit);
        executor.submit(requestManager);

        try {
            // This waits indefinitely for all tasks to complete,
            // which will only happen after shutdown.
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            // This is the expected result of stopSimulation()
            logger.log("Simulation main loop interrupted. Shutting down.");
            // Restore the interrupted status
            Thread.currentThread().interrupt();
        }

        logger.log("=== WAREHOUSE SIMULATION STOPPED ===");
        writeFinalReport();
        inventory.printInventory();
    }

    /**
     * Called by the GUI thread to stop the simulation.
     */
    public void stopSimulation() {
        if (!simulationRunning) {
            return; // Already stopped
        }
        logger.log("GUI requested simulation stop.");
        this.simulationRunning = false;
        this.requestManager.stop(); // Tell manager thread to stop its loop

        if (executor != null) {
            // This sends an InterruptedException to all running threads
            executor.shutdownNow();
            logger.log("Executor shutdown requested.");
        }
    }

    /**
     * Called by a Robot thread to get in the charging queue.
     * Implements the charging timeout.
     */
    public boolean queueForCharging(Robot robot, long timeoutMs) {
        if (!simulationRunning) return false;
        try {
            // 'offer' is the correct method for a timed wait.
            boolean accepted = chargingQueue.offer(robot, timeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                logger.log("Robot " + robot.getRobotID() + " timed out waiting for charge. Leaving queue.");
            }
            return accepted;
        } catch (InterruptedException e) {
            logger.log("Robot " + robot.getRobotID() + " interrupted while waiting for charge.");
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Called by Robot threads to add or update a request in the master list.
     * This is thread-safe because 'allRequests' is a ConcurrentHashMap.
     */
    public void addCompletedRequest(PartRequest request) {
        allRequests.put(request.requestID(), request);
    }

    /**
     * Writes the final report by getting the data from the master list.
     * REFACTORED: Now uses java.nio.file.Path and Files.newOutputStream
     */
    public void writeFinalReport() {
        Path reportPath = Paths.get("completed_report.dat");
        logger.log("Writing final binary report to " + reportPath.toAbsolutePath() + "...");

        // Get a snapshot of the requests. This is thread-safe.
        List<PartRequest> completedRequests = new ArrayList<>(allRequests.values());

        // Use try-with-resources for both the stream and the underlying file
        try (var fos = Files.newOutputStream(reportPath);
             var dos = new DataOutputStream(fos)) {

            dos.writeInt(completedRequests.size());
            for (PartRequest req : completedRequests) {
                dos.writeUTF(req.requestID());
                dos.writeUTF(req.part().partID());
                dos.writeInt(req.neededQuantity());
                dos.writeUTF(req.status().toString());
            }
            logger.log("Final report written successfully. Total requests: " + completedRequests.size());
        } catch (IOException e) {
            logger.log("Error writing final report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Component Creation Methods ---

    /**
     * Factory method to create all Robot instances.
     */
    private void createRobots(int count) {
        for (int i = 0; i < count; i++) {
            String robotID = "R-" + String.format("%03d", i + 1);
            // LoggerUtil is now handled inside its own constructor
            // Pass the Warehouse (this) to the robot
            Robot robot = new Robot(robotID, this);
            this.robots.add(robot);
        }
        logger.log("Created " + robots.size() + " robots.");
    }

    /**
     * Factory method to create all ChargingStation instances.
     * BUG FIX: This method now correctly uses the 'count' parameter.
     */
    private void createStations(int count) {
        // --- BUG FIX ---
        // The loop now correctly uses 'count' instead of being hard-coded to '2'.
        for (int i = 0; i < count; i++) {
            // --- END FIX ---
            String stationID = "CS-" + (char) ('A' + i);
            LoggerUtil stationLogger = new LoggerUtil("ChargingStation-" + stationID);
            // Pass the shared charging queue to the station
            ChargingStation station = new ChargingStation(stationID, stationLogger, chargingQueue);
            this.stations.add(station);
        }
        logger.log("Created " + stations.size() + " charging stations.");
    }

    // --- Getters for GUI Polling ---

    public boolean isSimulationRunning() {
        return this.simulationRunning;
    }

    /**
     * Returns a read-only list of robots for the GUI.
     */
    public List<Robot> getRobots() {
        return Collections.unmodifiableList(robots);
    }

    /**
     * Returns a read-only list of stations for the GUI.
     */
    public List<ChargingStation> getStations() {
        return Collections.unmodifiableList(stations);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public PartRequestManager getRequestManager() {
        return requestManager;
    }
}