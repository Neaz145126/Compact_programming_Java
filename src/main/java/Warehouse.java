import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
        createStations(stationCount);

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
        executor = Executors.newFixedThreadPool(robots.size() + stations.size() + 1);
        simulationRunning = true;
        logger.log("=== STARTING WAREHOUSE SIMULATION ===");

        // Start all component threads
        for (ChargingStation station : stations) {
            executor.submit(station);
        }
        for (Robot robot : robots) {
            executor.submit(robot);
        }
        executor.submit(requestManager);

        try {
            // This waits indefinitely for all tasks to complete,
            // which will only happen after shutdown.
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            // This is the expected result of stopSimulation()
            logger.log("Simulation main loop interrupted. Shutting down.");
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
        this.requestManager.stop(); // Tell manager thread to stop
        if (executor != null) {
            executor.shutdownNow(); // Interrupt all threads (robots, stations, etc)
        }
    }

    /**
     * Called by a Robot thread to get in the charging queue.
     * Implements the "15 min" (15 second) timeout.
     */
    public boolean queueForCharging(Robot robot, long timeoutMs) {
        if (!simulationRunning) return false;
        try {
            boolean accepted = chargingQueue.offer(robot, timeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                logger.log("Robot " + robot.getRobotID() + " timed out waiting for charge. Leaving queue.");
            }
            return accepted;
        } catch (InterruptedException e) {
            logger.log("Robot " + robot.getRobotID() + " interrupted while waiting for charge.");
            return false;
        }
    }

    /**
     * Called by Robot threads to add or update a request in the master list.
     */
    public void addCompletedRequest(PartRequest request) {
        allRequests.put(request.requestID(), request);
    }

    /**
     * Writes the final report by getting the data from the master list.
     */
    public void writeFinalReport() {
        String filename = "completed_report.dat";
        logger.log("Writing final binary report to " + filename + "...");

        List<PartRequest> completedRequests = new ArrayList<>(allRequests.values());

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filename))) {
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
    private void createRobots(int count) {
        for (int i = 0; i < count; i++) {
            String robotID = "R-" + String.format("%03d", i + 1);
            LoggerUtil robotLogger = new LoggerUtil("Robot-" + robotID);
            // Pass the Warehouse (this) to the robot
            Robot robot = new Robot(robotID, this);
            this.robots.add(robot);
        }
        logger.log("Created " + robots.size() + " robots.");
    }

    private void createStations(int count) {
        for (int i = 0; i < count; i++) {
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

    public List<Robot> getRobots() {
        return Collections.unmodifiableList(robots);
    }

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