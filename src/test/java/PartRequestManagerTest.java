import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PartRequestManagerTest {

    private PartRequestManager requestManager;
    private static final String REQUEST_FILE_NAME = "pending_requests.txt";
    private final File requestFile = new File(REQUEST_FILE_NAME);

    static class MockInventory extends Inventory {
        private final Map<String, Part> partDatabase = new HashMap<>();

        public MockInventory() {
            // Must call super, but params don't matter for this mock
            super(0, new HashMap<>());
        }

        public void addMockPart(Part part) {
            partDatabase.put(part.partID(), part);
        }

        @Override
        public Part findPartById(String partID) {
            // This bypasses the real inventory's logger and map
            return partDatabase.get(partID);
        }
    }

    @BeforeEach
    void setUp() {
        // Create a mock inventory and add a part
        Part partOilFilter = new Part("P1001", "Oil Filter", "Test filter");
        MockInventory mockInventory = new MockInventory();
        mockInventory.addMockPart(partOilFilter);

        // Create the manager with the mock inventory
        requestManager = new PartRequestManager(mockInventory);

        // Clean up any old files before the test
        deleteRequestFile();
    }

    @AfterEach
    void tearDown() {
        // Clean up the file after each test
        deleteRequestFile();
    }

    private void deleteRequestFile() {
        if (requestFile.exists()) {
            requestFile.delete();
        }
    }

    /** Helper method to create the request file with specific content */
    private void createRequestFile(String content) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REQUEST_FILE_NAME))) {
            writer.print(content);
        }
    }

    @Test
    @DisplayName("Should read a valid request from the file")
    void testUpdateWithValidFile() throws IOException {
        // Arrange: Create a file with a valid request
        createRequestFile("P1001,5");

        // Act: Run the update
        assertDoesNotThrow(() -> requestManager.update(), "Should process file without error");

        // Assert: The request should be in the queue
        assertTrue(requestManager.hasRequests(), "Queue should have a request");
        PartRequest req = requestManager.getNextRequest();
        assertEquals("P1001", req.part().partID());
        assertEquals(5, req.neededQuantity());
    }

    @Test
    @DisplayName("Should clear the file after successful processing")
    void testUpdateClearsFile() throws IOException {
        // Arrange: Create a file
        createRequestFile("P1001,5");
        assertTrue(requestFile.length() > 0, "File should have content before update");

        // Act: Run the update
        assertDoesNotThrow(() -> requestManager.update());

        // Assert: The file should now be empty
        assertTrue(requestFile.exists(), "File should still exist");
        assertEquals(0, requestFile.length(), "File should be empty after update");
    }

    @Test
    @DisplayName("Should throw RequestProcessingException for bad quantity (NumberFormatException)")
    void testUpdateThrowsExceptionForBadData() throws IOException {
        // Arrange: Create a file with an invalid quantity
        createRequestFile("P1001,abc"); // "abc" is not a number

        // Act & Assert: This directly tests Requirement (a) and (d)
        RequestProcessingException exception = assertThrows(
                RequestProcessingException.class,
                () -> requestManager.update(),
                "Should throw RequestProcessingException"
        );

        // Check for chaining (Requirement d)
        Throwable cause = exception.getCause();
        assertNotNull(cause, "Exception should have a cause");
        assertTrue(cause instanceof NumberFormatException, "Cause should be NumberFormatException");
    }

    @Test
    @DisplayName("Should throw RequestProcessingException for missing file (IOException)")
    void testUpdateThrowsExceptionForMissingFile() {
        // Arrange: Ensure file does not exist (done in setUp/tearDown)

        // Act & Assert: This directly tests Requirement (a) and (d)
        RequestProcessingException exception = assertThrows(
                RequestProcessingException.class,
                () -> requestManager.update(),
                "Should throw RequestProcessingException"
        );

        // Check for chaining (Requirement d)
        Throwable cause = exception.getCause();
        assertNotNull(cause, "Exception should have a cause");
        assertInstanceOf(IOException.class, cause, "Cause should be IOException");
    }

    @Test
    @DisplayName("Should skip unknown part IDs and not queue them")
    void testUpdateWithUnknownPartID() throws IOException {
        // Arrange: Create a file with a part ID the mock inventory doesn't know
        createRequestFile("P9999,2");

        // Act: Run the update
        assertDoesNotThrow(() -> requestManager.update());

        // Assert: The queue should be empty because the part was unknown
        assertFalse(requestManager.hasRequests(), "Queue should be empty");
    }
}