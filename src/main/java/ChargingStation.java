public class ChargingStation {

    private final String stationID;
    private Robot chargingRobot;
    private final LoggerUtil logger;

    // --- Constructor ---
    public ChargingStation(String stationID) {
        this.stationID = stationID;
        this.chargingRobot = null;
        this.logger = new LoggerUtil("ChargingStation-" + stationID);

        logger.log("Charging station " + stationID + " initialized and ready.");
    }

    // --- Update the robot charging process ---
    public void update() {
        if (this.chargingRobot == null) {
            return;
        }

        // Update the robot
        this.chargingRobot.update();

        // If charging is complete, release robot
        if (this.chargingRobot.getStatus() == RobotStatus.IDLE) {
            logger.log("Charging complete at " + stationID + " for robot " + this.chargingRobot.getRobotID());
            releaseRobot();
        }
    }

    // --- Dock a robot for charging ---
    public void dockRobot(Robot robot) {
        if (isAvailable()) {
            this.chargingRobot = robot;
            this.chargingRobot.startCharging();
            logger.log("Robot " + robot.getRobotID() + " docked at station " + stationID);
        } else {
            logger.log("Station " + stationID + " is already in use. Cannot dock robot " + robot.getRobotID());
        }
    }

    // --- Release the robot from the station ---
    private void releaseRobot() {
        if (this.chargingRobot != null) {
            logger.log("Robot " + this.chargingRobot.getRobotID() + " released from " + stationID);
        }
        this.chargingRobot = null;
    }

    // --- Getters ---
    public String getStationID() {
        return stationID;
    }

    public boolean isAvailable() {
        return this.chargingRobot == null;
    }
}
