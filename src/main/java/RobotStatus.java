public enum RobotStatus {
    IDLE,       // Waiting for a task
    WORKING,    // Actively performing a task
    CHARGING,   // At a charging station
    LOW_BATTERY, // Needs to charge, heading to queue
    WAITING_FOR_CHARGE // In the queue, waiting for a free station
}