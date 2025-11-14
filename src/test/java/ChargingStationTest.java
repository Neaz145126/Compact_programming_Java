import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class ChargingStationTest {

    private ChargingStation station;
    private Robot robot;
    private LoggerUtil robotLogger;

    @BeforeEach
    void setUp() {
        // Create a new station for each test
        station = new ChargingStation("CS-TEST");

        // Create a logger and a robot for tests
        robotLogger = new LoggerUtil("TestRobotLogger-Station");
        robot = new Robot("R-TEST", robotLogger);
    }

    @Test
    @DisplayName("Station should be available when new")
    void testIsAvailableWhenNew() {
        assertTrue(station.isAvailable(), "A new station should be available");
    }

    @Test
    @DisplayName("Should successfully dock a robot when available")
    void testDockRobotSuccess() {
        // Check pre-conditions
        assertTrue(station.isAvailable(), "Station should be available before docking");
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should be IDLE");

        // Action
        station.dockRobot(robot);

        // Check post-conditions
        assertFalse(station.isAvailable(), "Station should be unavailable after docking");
        assertEquals(RobotStatus.CHARGING, robot.getStatus(), "Robot's status should be set to CHARGING");
    }

    @Test
    @DisplayName("Should not dock a new robot when station is busy")
    void testDockRobotFailsWhenBusy() {
        // Arrange: Dock the first robot
        station.dockRobot(robot);
        assertFalse(station.isAvailable());

        // Create a second robot
        Robot robot2 = new Robot("R-TEST-2", new LoggerUtil("TestRobotLogger-Station2"));
        assertEquals(RobotStatus.IDLE, robot2.getStatus());

        // Action: Try to dock the second robot
        station.dockRobot(robot2);

        // Assert: The second robot should not have been docked
        assertFalse(station.isAvailable(), "Station should still be busy");
        assertEquals(RobotStatus.IDLE, robot2.getStatus(), "Robot2's status should not have changed");
        // We can't check 'chargingRobot' directly, but we can infer it's still 'robot'
        // because robot2's status didn't change.
    }

    @Test
    @DisplayName("Update should do nothing if no robot is charging")
    void testUpdateWhenEmpty() {
        assertTrue(station.isAvailable());
        // Action: Call update on an empty station
        assertDoesNotThrow(() -> station.update(), "Update on empty station should not throw an error");
        // Assert: Station should still be available
        assertTrue(station.isAvailable(), "Station should remain available");
    }

    @Test
    @DisplayName("Update should release robot when charging is complete")
    void testUpdateReleasesChargedRobot() {
        // Arrange: Drain the robot's battery so it needs to charge
        // We'll set it to 80% battery (1 tick from full) for a faster test.
        // We do this by simulating one task (100 -> 60) and then manually
        // running robot's update 2 times (60 -> 70 -> 80)
        // This is complex, so let's try a different way:
        // Let's just set it to 90% (almost full)

        // Simulate one task to drain battery
        Part samplePart = new Part("P-TEST", "Test Part", "A part for testing");
        PartRequest sampleRequest = PartRequest.create(samplePart, 5);
        robot.assignTask(sampleRequest); // 100%
        robot.performTask();             // 60%
        assertEquals(60, robot.getBatteryLevel());
        assertEquals(RobotStatus.IDLE, robot.getStatus());

        // Manually call robot's update 3 times to get to 90%
        robot.startCharging(); // Status -> CHARGING
        robot.update(); // 70%
        robot.update(); // 80%
        robot.update(); // 90%
        assertEquals(90, robot.getBatteryLevel());
        assertEquals(RobotStatus.CHARGING, robot.getStatus());

        // Now, dock this 90% full, CHARGING robot
        station.dockRobot(robot);
        assertFalse(station.isAvailable());
        assertEquals(RobotStatus.CHARGING, robot.getStatus());

        // Action 1: Call station update. This calls robot.update().
        // Robot battery goes 90 -> 100.
        // Robot's update sets its status to IDLE.
        station.update();
        assertEquals(100, robot.getBatteryLevel());
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should be IDLE after its update");
        // But the station hasn't released it *yet*. That happens on the *next* update.

        // Action 2: Call station update again.
        // Station.update() now sees the robot's status is IDLE.
        // It should call releaseRobot().
        station.update();

        // Assert: The station should now be free
        assertTrue(station.isAvailable(), "Station should be available after robot is released");
        assertEquals(RobotStatus.IDLE, robot.getStatus(), "Robot should remain IDLE");
    }
}