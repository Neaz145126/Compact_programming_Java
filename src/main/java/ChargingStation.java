import java.util.concurrent.BlockingQueue;

public class ChargingStation implements Runnable {

    private final String stationID;
    private final LoggerUtil logger;

    // CONCURRENCY: Shared queue of robots waiting to be charged
    private final BlockingQueue<Robot> chargingQueue;

    // --- Constructor ---
    public ChargingStation(String stationID, BlockingQueue<Robot> chargingQueue) {
        this.stationID = stationID;
        this.logger = new LoggerUtil("ChargingStation-" + stationID);
        this.chargingQueue = chargingQueue;

        logger.log("Charging station " + stationID + " initialized and ready.");
    }

    /**
     * This is the main life-cycle loop for the Charging Station thread.
     */
    @Override
    public void run() {
        logger.log("Station thread " + stationID + " started. Waiting for robots.");
        try {
            // Loop until the simulation is shut down
            while (true) {
                // 1. Wait for a robot to appear in the queue.
                // .take() will block this thread until a robot is available.
                Robot robotToCharge = chargingQueue.take();

                // 2. We have a robot! Dock it and start charging.
                logger.log("Station " + stationID + " docked Robot " + robotToCharge.getRobotID() + ". Starting charge.");
                robotToCharge.setStatus(RobotStatus.CHARGING);

                // 3. Simulate charging over time
                while (robotToCharge.getBatteryLevel() < Robot.MAX_BATTERY) {
                    // Wait for the defined "tick" time
                    Thread.sleep(Robot.CHARGE_TICK_MS);

                    // Add charge (this method is synchronized on the robot)
                    robotToCharge.chargeBattery(Robot.CHARGE_RATE_PER_TICK);

                    logger.log("Station " + stationID + " charging Robot " + robotToCharge.getRobotID()
                            + "... (" + robotToCharge.getBatteryLevel() + "%)");
                }

                // 4. Charging complete. Release the robot.
                logger.log("Station " + stationID + " charging complete for Robot " + robotToCharge.getRobotID());
                // Set robot status back to IDLE (this is synchronized)
                robotToCharge.setStatus(RobotStatus.IDLE);
            }
        } catch (InterruptedException e) {
            // Simulation is shutting down
            logger.log("Station thread " + stationID + " interrupted and shutting down.");
        }
    }

    // --- Getters ---
    public String getStationID() {
        return stationID;
    }
}