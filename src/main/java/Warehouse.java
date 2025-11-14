import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Warehouse {

    private final LoggerUtil logger;

    // --- Shared Resources ---
    private final Inventory inventory;
    private final PartRequestManager requestManager;
    // This is the master list of all requests, for the final report.
    // It must be thread-safe.
    private final ConcurrentHashMap<String, PartRequest> allRequests;
    // This queue is shared between Robots and ChargingStations.
    private final BlockingQueue<Robot> chargingQueue;

    // --- Simulation Components (Threads) ---
    private final List<Robot> robots;
    private final List<ChargingStation> stations;

    public Warehouse(String warehouseID, String name, Inventory inventory, int robotCount, int stationCount) {
        this.inventory = inventory;
        this.logger = new LoggerUtil("Warehouse-" + warehouseID);

        // --- Initialize Shared Resources ---
        this.allRequests = new ConcurrentHashMap<>();
        // CONCURRENCY CHANGE: The queue capacity is now unbounded.
        // A capacity equal to stationCount would cause deadlock if all stations
        // are busy and more robots try to queue.
        this.chargingQueue = new LinkedBlockingQueue<>();
        this.requestManager = new PartRequestManager(inventory);

        // --- Initialize Components ---
        this.robots = new ArrayList<>();
        this.stations = new ArrayList<>();

        createRobots(robotCount);
        createStations(stationCount);

        logger.log("Warehouse " + warehouseID + " (" + name + ") initialized with "
                + robots.size() + " robots and " + stations.size() + " stations.");
    }


    public void startSimulation(ExecutorService executor) {
        logger.log("=== STARTING WAREHOUSE SIMULATION ===");
        // Start the station threads (they will wait for robots)
        for (ChargingStation station : stations) {
            executor.submit(station);
        }
        // Start the robot threads
        for (Robot robot : robots) {
            executor.submit(robot);
        }
        // Start the PartRequestManager thread
        executor.submit(requestManager);
    }

    public void stopSimulation() {
        logger.log("=== STOPPING WAREHOUSE SIMULATION ===");
        this.requestManager.stop();
        // The executor shutdown will interrupt all waiting threads (Robots, Stations)
    }

    public boolean requestCharging(Robot robot) {
        try {
            // "15 min" timeout = 15 seconds in simulation time.
            // .offer() will wait up to 15s. If it fails, it returns false.
            boolean accepted = chargingQueue.offer(robot, 15, TimeUnit.SECONDS);
            if (!accepted) {
                logger.log("Robot " + robot.getRobotID() + " timed out waiting for charge. Leaving queue.");
            }
            return accepted;
        } catch (InterruptedException e) {
            // The simulation is shutting down while we were waiting
            logger.log("Robot " + robot.getRobotID() + " interrupted while waiting for charge.");
            return false;
        }
    }

    // --- Thread-Safe method for report ---

    public void addCompletedRequest(PartRequest request) {
        allRequests.put(request.requestID(), request);
    }

    public void writeFinalReport() {
        String filename = "completed_report.dat";
        logger.log("Writing final binary report to " + filename + "...");

        List<PartRequest> completedRequests = new ArrayList<>(allRequests.values());

        // This is an example of Requirement (c) "Resource Management"
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
            e.printStackTrace(); // Good to see the full error
        }
    }

    // --- Component Creation Methods ---

    private void createRobots(int count) {
        for (int i = 0; i < 11; i++) {
            String robotID = "R-" + String.format("%03d", (i+1));
            LoggerUtil robotLogger = new LoggerUtil("Robot-" + robotID);
            Robot robot = new Robot(robotID, robotLogger, this, this.requestManager, this.inventory);
            this.robots.add(robot);
        }
        logger.log("Created " + robots.size() + " robots, each with its own logger.");
    }

    private void createStations(int count) {
        for (int i = 0; i < 5; i++) {
            String stationID = "CS-" + (char) ('A' + i);
            ChargingStation station = new ChargingStation(stationID, this.chargingQueue);
            this.stations.add(station);
        }
        logger.log("Created " + stations.size() + " charging stations.");
    }
}