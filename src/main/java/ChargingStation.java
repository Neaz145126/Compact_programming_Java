import java.util.concurrent.BlockingQueue;

public class ChargingStation implements Runnable {

    private final String stationID;
    private final LoggerUtil logger;
    private final BlockingQueue<Robot> chargingQueue;

    // --- GUI Status ---
    // This is 'volatile' so the GUI thread can safely read the
    // current robot's status even as this thread changes it.
    private volatile Robot currentRobot = null;

    public ChargingStation(String stationID, LoggerUtil logger, BlockingQueue<Robot> chargingQueue) {
        this.stationID = stationID;
        this.logger = logger;
        this.chargingQueue = chargingQueue;
        logger.log("Charging station " + stationID + " initialized and ready.");
    }

    // --- Getters for GUI ---
    public String getStationID() {
        return stationID;
    }

    public Robot getCurrentRobot() {
        return this.currentRobot;
    }

    /**
     * Main run loop for the station thread.
     * This loop waits for a robot, processes it, and repeats.
     */
    @Override
    public void run() {
        logger.log("Station thread " + stationID + " started. Waiting for robots.");
        try {
            // This loop continues until the thread is interrupted
            while (!Thread.currentThread().isInterrupted()) {

                // 1. Wait for a robot to appear in the queue.
                // This .take() blocks indefinitely until a robot is available.
                Robot robotToCharge = chargingQueue.take();
                this.currentRobot = robotToCharge; // Show robot in GUI

                // 2. Process the robot.
                // We use a try...finally block to GUARANTEE the robot
                // is released, even if the charging is interrupted.
                try {
                    logger.log("Docked Robot " + robotToCharge.getRobotID() + ". Starting charge.");
                    chargeRobot(robotToCharge);
                    logger.log("Charging complete for Robot " + robotToCharge.getRobotID());
                } finally {
                    // This *always* runs, whether chargeRobot() finished
                    // or threw an InterruptedException.
                    logger.log("Releasing Robot " + robotToCharge.getRobotID());
                    robotToCharge.finishCharging();
                    this.currentRobot = null; // Clear robot from GUI
                }
            }
        } catch (InterruptedException e) {
            // Simulation is shutting down.
            // We don't need to clean up 'currentRobot' here, because
            // the 'finally' block above already ran and did it for us.
            logger.log("Station thread " + stationID + " interrupted and shutting down.");
            Thread.currentThread().interrupt(); // Preserve the interrupt status
        }
    }

    /**
     * Simulates the process of charging a single robot.
     * This method will block the station thread while charging.
     *
     * @param robot The robot to charge
     * @throws InterruptedException if the thread is interrupted during charging
     */
    private void chargeRobot(Robot robot) throws InterruptedException {
        // 1. Tell the robot it's charging
        robot.startCharging();

        // 2. Charge the robot in a loop
        while (!robot.isFullyCharged()) {
            // Check if simulation was stopped mid-charge
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Charging was interrupted");
            }

            // Simulate one "tick" of charging
            Thread.sleep(Robot.CHARGE_TICK_MS);
            robot.charge(); // This is a thread-safe call
        }
    }
}