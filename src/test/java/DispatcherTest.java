import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test class for the Dispatcher.
 *
 * This class uses a "Mock" inner class for PartRequestManager
 * to avoid real file I/O, allowing for true unit testing.
 */
class DispatcherTest {

    private Dispatcher dispatcher;
    private Inventory inventory;
    private MockPartRequestManager mockRequestManager;
    private Robot idleRobot;
    private ChargingStation station;

    private Part partOilFilter;
    private PartRequest oilRequest;

    /**
     * This is a "mock" or "stub" version of PartRequestManager.
     * It overrides the file-reading `update` method to do nothing
     * and overrides the queue methods to use a local queue
     * that we can control in our tests.
     */
    static class MockPartRequestManager extends PartRequestManager {
        private final Queue<PartRequest> internalQueue = new LinkedList<>();

        public MockPartRequestManager(Inventory inventory) {
            super(inventory); // Must call parent constructor
        }

        @Override
        public void update() {
            // Do nothing. We don't want to read from a file.
        }

        @Override
        public PartRequest getNextRequest() {
            return internalQueue.poll();
        }

        @Override
        public boolean hasRequests() {
            return !internalQueue.isEmpty();
        }

        // Helper method for our tests
        public void addRequest(PartRequest request) {
            internalQueue.add(request);
        }
    }


    // This method runs before EACH test
    @BeforeEach
    void setUp() {
        // 1. Create Parts
        partOilFilter = new Part("P1001", "Oil Filter", "Standard oil filter");
        oilRequest = PartRequest.create(partOilFilter, 5); // Request 5 units

        // 2. Create Inventory
        Map<Part, Integer> initialStock = new HashMap<>();
        initialStock.put(partOilFilter, 10); // We have 10 in stock
        inventory = new Inventory(100, initialStock);

        // 3. Create Robots and Stations
        // The robot's logger will create log files, this is an expected side effect
        LoggerUtil robotLogger = new LoggerUtil("TestRobotLogger-Dispatcher");
        idleRobot = new Robot("R-001", robotLogger);

        station = new ChargingStation("CS-A");

        List<Robot> robotList = new ArrayList<>();
        robotList.add(idleRobot);
        List<ChargingStation> stationList = new ArrayList<>();
        stationList.add(station);

        // 4. Create Mock Manager
        mockRequestManager = new MockPartRequestManager(inventory);

        // 5. Create Dispatcher (the class under test)
        dispatcher = new Dispatcher(robotList, stationList, inventory, mockRequestManager);
    }

    @Test
    @DisplayName("Should assign a new task to an idle robot")
    void testDispatchNewTaskSuccess() {
        // Setup: Add a request to the queue
        mockRequestManager.addRequest(oilRequest);
        assertEquals(10, inventory.getStockLevel(partOilFilter));
        assertEquals(RobotStatus.IDLE, idleRobot.getStatus());

        // Action: Run the dispatcher
        dispatcher.dispatchAndManageTasks();

        // Verify:
        // 1. Robot is now working
        assertEquals(RobotStatus.WORKING, idleRobot.getStatus(), "Robot should be working");
        // 2. Stock was removed
        assertEquals(5, inventory.getStockLevel(partOilFilter), "Inventory should be reduced");
        // 3. Request queue is empty
        assertFalse(mockRequestManager.hasRequests(), "Request queue should be empty");
        // 4. Completed list is still empty (task is in progress)
        assertTrue(dispatcher.getCompletedRequests().isEmpty(), "No requests should be completed yet");
    }

    @Test
    @DisplayName("Should fail a task if stock is insufficient")
    void testDispatchNewTaskFailsNoStock() {
        // Setup: Create a request for more than we have
        PartRequest largeRequest = PartRequest.create(partOilFilter, 20); // We only have 10
        mockRequestManager.addRequest(largeRequest);

        assertEquals(10, inventory.getStockLevel(partOilFilter));
        assertEquals(RobotStatus.IDLE, idleRobot.getStatus());

        // Action: Run the dispatcher
        dispatcher.dispatchAndManageTasks();

        // Verify:
        // 1. This is the test for the `catch (InsufficientStockException e)` block
        // 2. Robot is still IDLE
        assertEquals(RobotStatus.IDLE, idleRobot.getStatus(), "Robot should remain idle");
        // 3. Stock was NOT removed
        assertEquals(10, inventory.getStockLevel(partOilFilter), "Inventory should not change");
        // 4. Request queue is empty
        assertFalse(mockRequestManager.hasRequests(), "Request queue should be empty");
        // 5. The request was moved to "completed" with status FAILED
        assertEquals(1, dispatcher.getCompletedRequests().size(), "Completed list should have one request");
        assertEquals(RequestStatus.FAILED, dispatcher.getCompletedRequests().get(0).status(), "Request status should be FAILED");
    }

    @Test
    @DisplayName("Should process and complete a task from a working robot")
    void testProcessWorkingRobots() {
        // Setup: Manually assign a task to the robot to make it "WORKING"
        idleRobot.assignTask(oilRequest);
        assertEquals(RobotStatus.WORKING, idleRobot.getStatus());
        assertTrue(dispatcher.getCompletedRequests().isEmpty());

        // Action: Run the dispatcher. This will call `processWorkingRobots`
        dispatcher.dispatchAndManageTasks();

        // Verify:
        // 1. Robot finishes task and becomes IDLE
        assertEquals(RobotStatus.IDLE, idleRobot.getStatus(), "Robot should be idle after finishing task");
        // 2. The task is added to the completed list with status COMPLETED
        assertEquals(1, dispatcher.getCompletedRequests().size(), "Completed list should have one request");
        assertEquals(RequestStatus.COMPLETED, dispatcher.getCompletedRequests().get(0).status(), "Request status should be COMPLETED");
    }

    @Test
    @DisplayName("Should send a low-battery robot to charge")
    void testDispatchRobotsToCharge() {
        // Setup: Manually drain the robot's battery to LOW_BATTERY
        // This is a "cheat" to avoid running a full simulation in the test
        // We simulate two tasks to get battery to 20%
        idleRobot.assignTask(oilRequest); // 100
        idleRobot.performTask();          // 60
        idleRobot.assignTask(oilRequest); // 60
        idleRobot.performTask();          // 20
        // Now call update() to trigger the status change from IDLE -> LOW_BATTERY
        idleRobot.update();
        assertEquals(RobotStatus.LOW_BATTERY, idleRobot.getStatus(), "Robot should have low battery");
        assertTrue(station.isAvailable(), "Station should be free");

        // Action: Run the dispatcher
        dispatcher.dispatchAndManageTasks();

        // Verify:
        // 1. Robot is now CHARGING
        assertEquals(RobotStatus.CHARGING, idleRobot.getStatus(), "Robot should be charging");
        // 2. Station is now in use
        assertFalse(station.isAvailable(), "Station should be in use");
    }

    @Test
    @DisplayName("Should not assign task if no robots are idle")
    void testDispatchNewTaskFailsNoIdleRobots() {
        // Setup: Make the only robot busy by sending it to charge
        station.dockRobot(idleRobot);
        assertEquals(RobotStatus.CHARGING, idleRobot.getStatus());
        assertFalse(station.isAvailable());

        // Add a request to the queue
        mockRequestManager.addRequest(oilRequest);
        assertEquals(10, inventory.getStockLevel(partOilFilter));

        // Action: Run the dispatcher
        dispatcher.dispatchAndManageTasks();

        // Verify:
        // 1. Robot is still charging (its own update() runs)
        assertEquals(RobotStatus.CHARGING, idleRobot.getStatus());
        // 2. The request is still in the queue (it was not assigned)
        assertTrue(mockRequestManager.hasRequests(), "Request should still be in queue");
        // 3. Stock was not removed
        assertEquals(10, inventory.getStockLevel(partOilFilter), "Inventory should not change");
        // 4. Completed list is empty
        assertTrue(dispatcher.getCompletedRequests().isEmpty());
    }
}