public class Robot {

    // --- Constants ---
    public static final int MAX_BATTERY = 100;
    public static final int LOW_BATTERY_THRESHOLD = 25;
    public static final int BATTERY_DRAIN_PER_TASK = 40;
    public static final int CHARGE_RATE_PER_TICK = 10;

    // --- Attributes ---
    private final String robotID;
    private RobotStatus status;
    private int batteryLevel;
    private PartRequest currentTask;
    private final LoggerUtil logger;

    // --- Getters ---
    public String getRobotID() {
        return robotID;
    }

    public RobotStatus getStatus() {
        return status;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    // --- Constructor ---
    public Robot(String robotID, LoggerUtil logger) {
        this.robotID = robotID;
        this.status = RobotStatus.IDLE;
        this.batteryLevel = MAX_BATTERY;
        this.currentTask = null;
        this.logger = logger;

        logger.log("Robot " + robotID + " initialized. Battery: " + batteryLevel + "%, Status: " + status);
    }

    // --- Methods ---
    public void update() {
        switch (this.status) {
            case IDLE:
                if (this.batteryLevel <= LOW_BATTERY_THRESHOLD) {
                    logger.log("Robot " + robotID + " has low battery (" + batteryLevel + "%). Needs charging.");
                    this.status = RobotStatus.LOW_BATTERY;
                }
                break;

            case CHARGING:
                this.batteryLevel += CHARGE_RATE_PER_TICK;
                if (this.batteryLevel >= MAX_BATTERY) {
                    this.batteryLevel = MAX_BATTERY;
                    logger.log("Robot " + robotID + " is fully charged.");
                    goIdle();
                } else {
                    logger.log("Robot " + robotID + " is charging... (" + batteryLevel + "%)");
                }
                break;

            default:
                // Other statuses: WORKING, LOW_BATTERY, etc.
                break;
        }
    }

    public void assignTask(PartRequest request) {
        if (this.status == RobotStatus.IDLE) {
            this.currentTask = request;
            this.status = RobotStatus.WORKING;
            logger.log("Robot " + robotID + " assigned to task " + request.requestID()
                    + " (" + request.part().name() + ")");
        } else {
            logger.log("Robot " + robotID + " cannot accept task " + request.requestID()
                    + ". Current status: " + status);
        }
    }

    public PartRequest performTask() {
        logger.log("Robot " + robotID + " is performing task " + currentTask.requestID()
                + " for part " + currentTask.part().name());

        this.batteryLevel -= BATTERY_DRAIN_PER_TASK;
        if (this.batteryLevel < 0) this.batteryLevel = 0;

        logger.log("Robot " + robotID + " finished task. Battery now: " + batteryLevel + "%");

        PartRequest completedTask = this.currentTask.withStatus(RequestStatus.COMPLETED);

        goIdle();
        return completedTask;
    }

    public void startCharging() {
        if (this.status == RobotStatus.LOW_BATTERY || this.status == RobotStatus.IDLE) {
            this.status = RobotStatus.CHARGING;
            logger.log("Robot " + robotID + " is moving to charging station.");
        } else {
            logger.log("Robot " + robotID + " cannot charge right now. Status: " + status);
        }
    }

    private void goIdle() {
        this.status = RobotStatus.IDLE;
        this.currentTask = null;
        logger.log("Robot " + robotID + " is now IDLE. Battery: " + batteryLevel + "%");
    }
}