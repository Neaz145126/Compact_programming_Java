import java.util.Random;

/**
 * CONCURRENCY UPDATE: This is now a Runnable, one per robot.
 * - Manages its own state machine in the run() method.
 * - Pulls tasks from the PartRequestManager (Consumer).
 * - Interacts with the Warehouse for charging.
 * - BUG FIX: batteryLevel is now volatile.
 */
public class Robot implements Runnable {

    // --- Constants ---
    public static final int MAX_BATTERY = 100;
    public static final int LOW_BATTERY_THRESHOLD = 25;
    // Randomize drain to simulate real-world variance
    private static final int AVG_BATTERY_DRAIN = 30;
    private static final int TASK_DURATION_MS = 3000; // 3 seconds
    private static final long IDLE_POLL_INTERVAL_MS = 1000; // 1 second
    public static final long CHARGE_TICK_MS = 1000; // 0.1 seconds
    private static final int CHARGE_PER_TICK = 2;
    private static final long CHARGING_TIMEOUT_MS = 15000; // 15 seconds

    // --- Attributes ---
    private final String robotID;
    private volatile RobotStatus status;
    private volatile int batteryLevel; // VOLATILE fix
    private PartRequest currentTask;
    private final LoggerUtil logger;
    private final Random random = new Random();

    // --- Conductor ---
    // A reference to the main warehouse to access shared resources
    private final Warehouse warehouse;

    // --- Constructor ---
    public Robot(String robotID, Warehouse warehouse) {
        this.robotID = robotID;
        this.warehouse = warehouse;
        this.logger = new LoggerUtil("Robot-" + robotID);
        this.status = RobotStatus.IDLE;
        this.batteryLevel = MAX_BATTERY;
        this.currentTask = null;
        logger.log("Robot " + robotID + " initialized. Battery: " + batteryLevel + "%");
    }

    // --- Getters (for GUI) ---
    public String getRobotID() { return robotID; }
    public RobotStatus getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }
    public PartRequest getCurrentTask() { return currentTask; }

    @Override
    public void run() {
        logger.log("Robot " + robotID + " thread started.");
        try {
            while (warehouse.isSimulationRunning()) {
                switch (status) {
                    case IDLE:
                        checkIdleState();
                        break;
                    case LOW_BATTERY:
                        requestChargingState();
                        break;
                    case WAITING_FOR_CHARGE:
                        // Do nothing, waiting to be picked up by a station
                        // The Warehouse.queueForCharging() handles the timeout
                        Thread.sleep(IDLE_POLL_INTERVAL_MS);
                        break;
                    case WORKING:
                        performTaskState();
                        break;
                    case CHARGING:
                        // Do nothing, the ChargingStation thread is in control
                        Thread.sleep(IDLE_POLL_INTERVAL_MS);
                        break;
                }
            }
        } catch (InterruptedException e) {
            // Simulation is shutting down
        }
        logger.log("Robot " + robotID + " thread stopped. Final status: " + status);
    }

    private void checkIdleState() throws InterruptedException {
        if (batteryLevel <= LOW_BATTERY_THRESHOLD) {
            status = RobotStatus.LOW_BATTERY;
            return;
        }

        // --- CONSUMER LOGIC ---
        // Wait on the manager for a new task
        synchronized (warehouse.getRequestManager()) {
            PartRequest newTask = warehouse.getRequestManager().getNextRequest();
            if (newTask == null) {
                // No task found, wait to be notified
                warehouse.getRequestManager().wait(IDLE_POLL_INTERVAL_MS);
            } else {
                // Task found!
                logger.log("Found task " + newTask.requestID() + ". Attempting to get stock.");
                try {
                    // Try to get stock *before* accepting the task
                    warehouse.getInventory().removeStock(newTask.part(), newTask.neededQuantity());

                    // Stock secured! Accept the task.
                    this.currentTask = newTask.withStatus(RequestStatus.IN_PROGRESS);
                    this.status = RobotStatus.WORKING;
                    logger.log("Stock secured. Starting work on task " + currentTask.requestID());
                    warehouse.addCompletedRequest(this.currentTask); // Add to master list as IN_PROGRESS

                } catch (InsufficientStockException e) {
                    // Stock failed
                    logger.log("FAILED task " + newTask.requestID() + ": " + e.getMessage());
                    warehouse.addCompletedRequest(newTask.withStatus(RequestStatus.FAILED));
                }
                // No need to notify, we just consumed
            }
        }
    }

    private void requestChargingState() {
        status = RobotStatus.WAITING_FOR_CHARGE;
        logger.log("Battery low (" + batteryLevel + "%). Queuing for charge.");

        boolean accepted = warehouse.queueForCharging(this, CHARGING_TIMEOUT_MS);

        if (!accepted) {
            // Timed out or was interrupted
            logger.log("Left charging queue (timeout or shutdown). Will try again.");
            // If still low, go to LOW_BATTERY, otherwise IDLE
            status = (batteryLevel <= LOW_BATTERY_THRESHOLD) ? RobotStatus.LOW_BATTERY : RobotStatus.IDLE;
        } else {
            // Accepted by queue, station will take it from here
            // The ChargingStation will set status to CHARGING
        }
    }

    private void performTaskState() throws InterruptedException {
        if (currentTask == null) {
            status = RobotStatus.IDLE;
            return;
        }

        logger.log("Performing task " + currentTask.requestID() + "...");
        Thread.sleep(TASK_DURATION_MS);

        // Task complete, drain battery
        int drain = AVG_BATTERY_DRAIN + random.nextInt(10) - 5; // e.g. 25-35
        this.batteryLevel -= drain;
        if (this.batteryLevel < 0) this.batteryLevel = 0;

        logger.log("Task " + currentTask.requestID() + " complete. Battery: " + batteryLevel + "%");

        // Update task status and add to report
        this.currentTask = this.currentTask.withStatus(RequestStatus.COMPLETED);
        warehouse.addCompletedRequest(this.currentTask);

        // Go idle
        this.currentTask = null;
        status = (batteryLevel <= LOW_BATTERY_THRESHOLD) ? RobotStatus.LOW_BATTERY : RobotStatus.IDLE;
    }


    // --- Methods called BY ChargingStation thread ---

    public void startCharging() {
        this.status = RobotStatus.CHARGING;
    }

    public boolean isFullyCharged() {
        return this.batteryLevel >= MAX_BATTERY;
    }

    public void charge() {
        this.batteryLevel += CHARGE_PER_TICK;
        if (this.batteryLevel > MAX_BATTERY) {
            this.batteryLevel = MAX_BATTERY;
        }
    }

    public void finishCharging() {
        this.status = RobotStatus.IDLE;
        this.currentTask = null;
    }
}