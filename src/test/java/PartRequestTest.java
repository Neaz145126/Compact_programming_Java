import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Import JUnit 5 assertions
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PartRequest record.
 *
 * This test class verifies the logic in the static 'create' factory method,
 * including its argument validation and the thread-safe, sequential ID generation.
 * It also tests the 'withStatus' method to ensure it returns a new
 * record with the updated status.
 */
class PartRequestTest {

    private Part samplePart;

    /**
     * Set up a sample Part object to be used in all tests.
     * This runs once before any tests.
     */
    @BeforeEach
    void setUp() {
        samplePart = new Part("P1001", "Oil Filter", "Standard oil filter");
    }

    /**
     * Tests the successful creation of a valid PartRequest.
     */
    @Test
    void testCreateSuccess() {
        // Act: Create a new part request
        PartRequest request = PartRequest.create(samplePart, 10);

        // Assert: Check that all fields are set correctly
        assertNotNull(request, "Request should not be null");
        assertNotNull(request.requestID(), "requestID should not be null");
        assertTrue(request.requestID().startsWith("Task-"), "requestID should start with 'Task-'");
        assertEquals(samplePart, request.part(), "Part object should match");
        assertEquals(10, request.neededQuantity(), "Quantity should be 10");
        assertEquals(RequestStatus.PENDING, request.status(), "Initial status should be PENDING");
    }

    /**
     * Tests that creating a request with a null Part throws IllegalArgumentException.
     */
    @Test
    void testCreateThrowsExceptionForNullPart() {
        // Act & Assert:
        // Attempt to create a request with a null part.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            PartRequest.create(null, 5);
        });

        // Optional: Check the exception message
        assertEquals("Part cannot be null", exception.getMessage(),
                "Exception message should be specific to the null part");
    }

    /**
     * Tests that creating a request with zero quantity throws IllegalArgumentException.
     */
    @Test
    void testCreateThrowsExceptionForZeroQuantity() {
        // Act & Assert:
        // Attempt to create a request with 0 quantity.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            PartRequest.create(samplePart, 0);
        });

        // Optional: Check the exception message
        assertEquals("Quantity must be positive", exception.getMessage(),
                "Exception message should be specific to the non-positive quantity");
    }

    /**
     * Tests that creating a request with a negative quantity throws IllegalArgumentException.
     */
    @Test
    void testCreateThrowsExceptionForNegativeQuantity() {
        // Act & Assert:
        // Attempt to create a request with -5 quantity.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            PartRequest.create(samplePart, -5);
        });

        // Optional: Check the exception message
        assertEquals("Quantity must be positive", exception.getMessage(),
                "Exception message should be specific to the non-positive quantity");
    }

    /**
     * Tests that sequential calls to 'create' produce unique, sequential request IDs.
     * This relies on the static AtomicLong counter.
     */
    @Test
    void testCreateGeneratesUniqueIDs() {
        // Act: Create two requests back-to-back.
        PartRequest request1 = PartRequest.create(samplePart, 1);
        PartRequest request2 = PartRequest.create(samplePart, 2);

        // Assert: The IDs should not be null and should not be equal.
        assertNotNull(request1.requestID());
        assertNotNull(request2.requestID());
        assertNotEquals(request1.requestID(), request2.requestID(),
                "Sequential request IDs should be unique");

        // Optional: Verify that they are sequential (e.g., "Task-1" and "Task-2")
        // Note: This part is fragile if other tests run in parallel.
        // A safer test is just to check for non-equality.
        try {
            long id1 = Long.parseLong(request1.requestID().split("-")[1]);
            long id2 = Long.parseLong(request2.requestID().split("-")[1]);
            assertEquals(id1 + 1, id2, "Request IDs should be sequential");
        } catch (NumberFormatException e) {
            fail("requestID format was not 'Task-N' as expected.");
        }
    }

    /**
     * Tests the 'withStatus' method, which is part of the 'record' immutability.
     * It should return a new object with only the status changed.
     */
    @Test
    void testWithStatus() {
        // Arrange: Create an initial request.
        PartRequest initialRequest = PartRequest.create(samplePart, 7);

        // Act: Create a new request with an updated status.
        PartRequest inProgressRequest = initialRequest.withStatus(RequestStatus.IN_PROGRESS);

        // Assert:
        // 1. The objects are different instances.
        assertNotSame(initialRequest, inProgressRequest, "Should be a new object instance");

        // 2. The new object has the new status.
        assertEquals(RequestStatus.IN_PROGRESS, inProgressRequest.status(),
                "New object should have status IN_PROGRESS");

        // 3. The original object's status is unchanged.
        assertEquals(RequestStatus.PENDING, initialRequest.status(),
                "Original object status should remain PENDING");

        // 4. All other properties are identical.
        assertEquals(initialRequest.requestID(), inProgressRequest.requestID(),
                "requestID should be identical");
        assertEquals(initialRequest.part(), inProgressRequest.part(),
                "part should be identical");
        assertEquals(initialRequest.neededQuantity(), inProgressRequest.neededQuantity(),
                "neededQuantity should be identical");
    }
}