import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CONCURRENCY UPDATE: This is now a Runnable, one per station.
 * - Its 'run' method blocks until a robot enters the queue.
 * - It "consumes" robots from the shared chargingQueue.
 * - ADDED: Status getter for the GUI.
 */
public class ChargingStation implements Runnable {

    private final String stationID;
    private final LoggerUtil logger;
    private final BlockingQueue<Robot> chargingQueue;

    // --- GUI Status ---
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

    @Override
    public void run() {
        logger.log("Station thread " + stationID + " started. Waiting for robots.");
        try {
            // This loop continues until the thread is interrupted
            while (!Thread.currentThread().isInterrupted()) {
                // 1. Wait for a robot to appear in the queue
                // This .take() blocks indefinitely until a robot is available
                this.currentRobot = chargingQueue.take();
                logger.log("Docked Robot " + currentRobot.getRobotID() + ". Starting charge.");

                // 2. Tell the robot it's charging
                currentRobot.startCharging();

                // 3. Charge the robot in a loop
                while (!currentRobot.isFullyCharged()) {
                    // Check if simulation was stopped mid-charge
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

                    Thread.sleep(Robot.CHARGE_TICK_MS);
                    currentRobot.charge(); // This is a thread-safe call
                }

                // 4. Release the robot
                logger.log("Charging complete for Robot " + currentRobot.getRobotID());
                currentRobot.finishCharging();
                this.currentRobot = null;
            }
        } catch (InterruptedException e) {
            // Simulation is shutting down
            logger.log("Station thread " + stationID + " interrupted and shutting down.");
            if (this.currentRobot != null) {
                // If a robot was charging, release it
                currentRobot.finishCharging();
            }
        }
    }
}