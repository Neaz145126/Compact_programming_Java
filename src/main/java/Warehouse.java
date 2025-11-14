import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HA2 Update: This class now handles the RequestProcessingException
 * thrown by the PartRequestManager.
 */
public class Warehouse {

    private final String warehouseID;
    private final String name;

    private final PartRequestManager requestManager;
    private final List<Robot> robots;
    private final List<ChargingStation> stations;

    private final Dispatcher dispatcher;
    private final LoggerUtil logger;

    public Warehouse(String warehouseID, String name, Inventory inventory, int robotCount, int stationCount) {
        this.warehouseID = warehouseID;
        this.name = name;

        // Initialize logger for this warehouse
        this.logger = new LoggerUtil("Warehouse-" + warehouseID);

        this.requestManager = new PartRequestManager(inventory);
        this.robots = new ArrayList<>();
        this.stations = new ArrayList<>();

        // Create the components internally
        createRobots(robotCount);
        createStations(stationCount);

        // Create the dispatcher and give it the lists it needs to manage
        this.dispatcher = new Dispatcher(robots, stations, inventory, requestManager);

        logger.log("Warehouse " + warehouseID + " (" + name + ") initialized with "
                + robots.size() + " robots and " + stations.size() + " stations.");
    }

    /**
     * HA2 UPDATE: This method now handles the RequestProcessingException
     * from the requestManager.
     */
    public void update() {
        // 1. Update all autonomous objects
        updateRobots();
        updateStations();

        // 2. Check for new requests from the file stream
        try {
            // This line now requires a try-catch
            requestManager.update();
        } catch (RequestProcessingException e) {
            // This is a critical failure in the system, log it.
            logger.log("CRITICAL: Failed to process part requests file. No new tasks will be loaded this tick.");
            // We also print the stack trace so the developer can debug the *original* cause
            e.printStackTrace();
        }

        // 3. Delegate all management logic to the dispatcher
        dispatcher.dispatchAndManageTasks();

        logger.log("Warehouse " + warehouseID + " updated all components.");
    }

    /**
     * Writes the final report by getting the data from the dispatcher.
     */
    public void writeFinalReport() {
        String filename = "completed_report.dat";
        logger.log("Writing final binary report to " + filename + "...");

        List<PartRequest> completedRequests = dispatcher.getCompletedRequests();

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
        for (int i = 0; i < count; i++) {
            String robotID = "R-" + String.format("%03d", i);
            LoggerUtil robotLogger = new LoggerUtil("Robot-" + robotID);
            Robot robot = new Robot(robotID, robotLogger);
            this.robots.add(robot);
        }

        logger.log("Created " + robots.size() + " robots, each with its own logger.");
    }

    private void createStations(int count) {
        for (int i = 0; i < count; i++) {
            String stationID = "CS-" + (char) ('A' + i);
            ChargingStation station = new ChargingStation(stationID);
            this.stations.add(station);
        }
        logger.log("Created " + stations.size() + " charging stations.");
    }

    // --- Internal Update Methods ---

    private void updateRobots() {
        for (Robot robot : robots) {
            robot.update();
        }
    }

    private void updateStations() {
        for (ChargingStation station : stations) {
            station.update();
        }
    }

    // --- Getters ---

    public List<Robot> getRobots() {
        return robots;
    }

    public List<ChargingStation> getStations() {
        return stations;
    }
}