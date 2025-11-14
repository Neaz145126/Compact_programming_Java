import java.util.ArrayList;
import java.util.List;

/**
 * HA2 Update: This class now handles the InsufficientStockException
 * thrown by the Inventory.
 */
public class Dispatcher {

    private final List<Robot> robots;
    private final List<ChargingStation> stations;
    private final Inventory inventory;
    private final PartRequestManager requestManager;

    private final List<PartRequest> completedRequests;

    public Dispatcher(List<Robot> robots, List<ChargingStation> stations,
                      Inventory inventory, PartRequestManager requestManager) {
        this.robots = robots;
        this.stations = stations;
        this.inventory = inventory;
        this.requestManager = requestManager;
        this.completedRequests = new ArrayList<>(); // Initialize the list here
    }

    public void dispatchAndManageTasks() {
        // 1. Manage charging
        dispatchRobotsToCharge();

        // 2. Process tasks
        processWorkingRobots();

        // 3. Assign new tasks
        dispatchNewTasks();
    }

    public List<PartRequest> getCompletedRequests() {
        return this.completedRequests;
    }

    private void dispatchRobotsToCharge() {
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotStatus.LOW_BATTERY) {
                ChargingStation availableStation = findAvailableStation();
                if (availableStation != null) {
                    System.out.println("Dispatcher: Sending Robot " + robot.getRobotID() + " to charge at " + availableStation.getStationID());
                    availableStation.dockRobot(robot);
                } else {
                    System.out.println("Dispatcher: Robot " + robot.getRobotID() + " needs charge, but no stations are free.");
                }
            }
        }
    }

    private void processWorkingRobots() {
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotStatus.WORKING) {
                PartRequest finishedTask = robot.performTask();
                if (finishedTask != null) {
                    System.out.println("Dispatcher: Robot " + robot.getRobotID() + " completed task " + finishedTask.requestID());
                    completedRequests.add(finishedTask.withStatus(RequestStatus.COMPLETED));
                }
            }
        }
    }

    /**
     * HA2 UPDATE: This method now uses a try-catch block to handle the
     * InsufficientStockException from the Inventory.
     */
    private void dispatchNewTasks() {
        if (requestManager.hasRequests()) {
            Robot idleRobot = findIdleRobot();
            if (idleRobot != null) {
                PartRequest request = requestManager.getNextRequest();

                try {
                    // This line now requires a try-catch block
                    inventory.removeStock(
                            request.part(),
                            request.neededQuantity()
                    );

                    // This code only runs if removeStock() succeeds
                    System.out.println("Dispatcher: Assigning request " + request.requestID() + " to Robot " + idleRobot.getRobotID());
                    idleRobot.assignTask(request);

                } catch (InsufficientStockException e) {
                    // This code runs if removeStock() throws the exception
                    System.out.println("Dispatcher: FAILED request " + request.requestID() + " (" + e.getMessage() + ").");
                    completedRequests.add(request.withStatus(RequestStatus.FAILED));
                }
            }
        }
    }

    private Robot findIdleRobot() {
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotStatus.IDLE &&
                    robot.getBatteryLevel() > Robot.LOW_BATTERY_THRESHOLD) {
                return robot;
            }
        }
        return null; // No idle robots
    }

    private ChargingStation findAvailableStation() {
        for (ChargingStation station : stations) {
            if (station.isAvailable()) {
                return station;
            }
        }
        return null; // No available stations
    }
}