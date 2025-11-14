import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test class for the Robot.
 *
 * NOTE: The Robot's constructor requires a LoggerUtil, which writes log
 * files to a "Logs" directory. Running these tests WILL create a
 * "Logs" directory and "TestRobotLogger-..." files as a side effect.
 * This is a limitation of the current design, as the LoggerUtil
 * cannot be easily "mocked" or disabled.
 */
class RobotTest {

    private Robot robot;
    private LoggerUtil testLogger;
    private PartRequest sampleRequest;

    // This method runs before EACH test
    @BeforeEach
    void setUp() {
        // Create a logger for the test.
        testLogger = new LoggerUtil("TestRobotLogger");

        // Create a fresh robot instance for every test to ensure isolation
        robot = new Robot("R-TEST", testLogger);

        // Create a sample part and request to use in tests
        Part samplePart = new Part("P-TEST", "Test Part", "A part for testing");
        // PartRequest.create handles ID generation
        sampleRequest = PartRequest.create(samplePart, 5);
    }

    @Test
    @DisplayName("Robot should initialize with correct default values")
    void testRobotInitialization() {
        // A new robot should be IDLE and at MAX_BATTERY
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should start as IDLE");
        assertEquals(Robot.MAX_BATTERY, robot.getBatteryLevel(), "Robot should start at max battery");
        // A new robot has no task, which is checked in its private state (not directly testable
        // without a getter, but assignTask/performTask will fail if it's not idle).
    }

    @Test
    @DisplayName("Robot should accept task when IDLE")
    void testAssignTaskWhenIdle() {
        // Assign the task
        robot.assignTask(sampleRequest);

        // Verify the robot's status changed
        assertEquals(RobotStatus.WORKING, robot.getStatus(), "Robot should be WORKING after getting a task");
        // We can't directly check 'currentTask' as it's private,
        // but the status change is a reliable indicator.
    }

    @Test
    @DisplayName("Robot should drain battery and complete task when performing task")
    void testPerformTask() {
        // Setup: Robot must be WORKING to perform a task
        robot.assignTask(sampleRequest);
        assertEquals(RobotStatus.WORKING, robot.getStatus());

        // The action being tested
        PartRequest completedRequest = robot.performTask();

        // Calculate expected battery
        int expectedBattery = Robot.MAX_BATTERY - Robot.BATTERY_DRAIN_PER_TASK;

        // Verify the results
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should be IDLE after finishing a task");
        assertEquals(expectedBattery, robot.getBatteryLevel(), "Battery should be drained by " + Robot.BATTERY_DRAIN_PER_TASK);
        assertNotNull(completedRequest, "performTask should return the completed request");
        assertEquals(RequestStatus.COMPLETED, completedRequest.status(), "Returned request should be marked COMPLETED");
    }

    @Test
    @DisplayName("Robot should change status to LOW_BATTERY when battery is low")
    void testUpdateChangesStatusToLowBattery() {
        // Setup: Drain the battery to just above the threshold.
        // We can't set batteryLevel directly, so we simulate task performance.
        // Note: This is a bit of a workaround.
        robot.assignTask(sampleRequest); // Battery 100
        robot.performTask();             // Battery 60 (100 - 40)
        robot.assignTask(sampleRequest); // Battery 60
        robot.performTask();             // Battery 20 (60 - 40)

        // At this point, the robot is IDLE with 20% battery.
        assertEquals(RobotStatus.IDLE, robot.getStatus());
        assertTrue(robot.getBatteryLevel() <= Robot.LOW_BATTERY_THRESHOLD, "Robot battery should be at or below threshold");

        // The action being tested
        robot.update(); // The update method should detect the low battery

        // Verify the result
        assertEquals(RobotStatus.LOW_BATTERY, robot.getStatus(), "Robot status should change to LOW_BATTERY");
    }

    @Test
    @DisplayName("Robot should charge correctly and become IDLE when full")
    void testCharging() {
        // Setup: Put the robot in a state where it can charge (e.g., LOW_BATTERY)
        // and set its battery to a known value. We'll use the same setup as the previous test.
        robot.assignTask(sampleRequest);
        robot.performTask();             // Battery 60
        robot.assignTask(sampleRequest);
        robot.performTask();             // Battery 20, status IDLE
        robot.update();                  // Battery 20, status LOW_BATTERY
        assertEquals(RobotStatus.LOW_BATTERY, robot.getStatus());

        // The action being tested: start charging
        robot.startCharging();
        assertEquals(RobotStatus.CHARGING, robot.getStatus(), "Robot should be CHARGING");

        // Call update and check if battery increases
        int initialCharge = robot.getBatteryLevel(); // Should be 20
        robot.update();
        assertEquals(initialCharge + Robot.CHARGE_RATE_PER_TICK, robot.getBatteryLevel(), "Battery should increase by charge rate"); // Now 30

        // Call update several more times to reach 100%
        // We need 7 more ticks to go from 30 to 100
        for (int i = 0; i < 7; i++) {
            robot.update();
        }

        // Verify it's full and IDLE
        assertEquals(Robot.MAX_BATTERY, robot.getBatteryLevel(), "Battery should be at max");
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should be IDLE after reaching max charge");

        // Test that battery doesn't *over*charge
        robot.update(); // Call update one more time
        assertEquals(Robot.MAX_BATTERY, robot.getBatteryLevel(), "Battery should not exceed max");
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should remain IDLE");
    }
}