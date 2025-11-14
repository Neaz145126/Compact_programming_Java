import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

// Import JUnit 5 assertions
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    private Inventory inventory;
    private Part part1;
    private Part part2;

    /**
     * This setup method runs before each @Test.
     * It creates a fresh Inventory instance with a known initial stock,
     * ensuring that tests do not interfere with each other.
     */
    @BeforeEach
    void setUp() {
        // 1. Define sample parts for testing
        part1 = new Part("P1001", "Oil Filter", "Standard oil filter");
        part2 = new Part("P1002", "Air Filter", "Engine air filter");

        // 2. Create the initial stock map
        Map<Part, Integer> initialStock = new HashMap<>();
        initialStock.put(part1, 25); // 25 units of Oil Filters
        initialStock.put(part2, 30); // 30 units of Air Filters

        // 3. Initialize the Inventory instance with a 500-item capacity
        inventory = new Inventory(500, initialStock);
    }

    /**
     * Tests that removing a valid quantity of stock succeeds.
     */
    @Test
    void testRemoveStockSuccess() throws InsufficientStockException {
        // Act: Remove 5 units of part1.
        boolean result = inventory.removeStock(part1, 5);

        // Assert: The operation should succeed and the stock level should be updated.
        assertTrue(result, "removeStock should return true on success");
        assertEquals(20, inventory.getStockLevel(part1),
                "Stock level should be 20 after removing 5 from 25");
    }

    /**
     * Tests that attempting to remove more stock than available
     * throws an InsufficientStockException.
     */
    @Test
    void testRemoveStockThrowsInsufficientStockException() {
        // Act & Assert:
        // Try to remove 100 units when only 25 are available.
        // Assert that the specific exception is thrown.
        Exception exception = assertThrows(InsufficientStockException.class, () -> {
            inventory.removeStock(part1, 100);
        });

        // Optional: Check the exception message
        String expectedMessage = "Not enough stock of " + part1.name();
        assertTrue(exception.getMessage().contains(expectedMessage),
                "Exception message should indicate not enough stock");

        // Assert: The stock level should not have changed after the failed attempt.
        assertEquals(25, inventory.getStockLevel(part1),
                "Stock level should remain 25 after a failed removal");
    }

    /**
     * Tests that removing the exact quantity of available stock succeeds.
     */
    @Test
    void testRemoveStockExactQuantity() throws InsufficientStockException {
        // Act: Remove all 25 units of part1.
        boolean result = inventory.removeStock(part1, 25);

        // Assert: The operation should succeed and the stock level should be 0.
        assertTrue(result, "removeStock should return true when removing exact amount");
        assertEquals(0, inventory.getStockLevel(part1),
                "Stock level should be 0 after removing all units");
    }

    /**
     * Tests that attempting to remove zero units fails gracefully.
     * The removeStock method should return false for non-positive quantities.
     */
    @Test
    void testRemoveStockZeroQuantity() throws InsufficientStockException {
        // Act: Attempt to remove 0 units.
        boolean result = inventory.removeStock(part1, 0);

        // Assert: The operation should fail (return false) and stock is unchanged.
        assertFalse(result, "removeStock should return false for 0 quantity");
        assertEquals(25, inventory.getStockLevel(part1),
                "Stock level should remain 25 after attempting to remove 0");
    }

    /**
     * Tests that attempting to remove a negative quantity fails gracefully.
     */
    @Test
    void testRemoveStockNegativeQuantity() throws InsufficientStockException {
        // Act: Attempt to remove -5 units.
        boolean result = inventory.removeStock(part1, -5);

        // Assert: The operation should fail (return false) and stock is unchanged.
        assertFalse(result, "removeStock should return false for negative quantity");
        assertEquals(25, inventory.getStockLevel(part1),
                "Stock level should remain 25 after attempting to remove -5");
    }

    /**
     * Tests that attempting to remove a part that is not in the inventory
     * throws an InsufficientStockException.
     */
    @Test
    void testRemoveStockPartNotAvailable() {
        // Arrange: Create a new part that is not in the initial stock.
        Part part3 = new Part("P9999", "Missing Part", "A part not in inventory");

        // Act & Assert:
        // Attempting to remove it will default to 0 stock, and 1 > 0.
        assertThrows(InsufficientStockException.class, () -> {
            inventory.removeStock(part3, 1);
        });

        // Assert: Stock level (default 0) remains 0.
        assertEquals(0, inventory.getStockLevel(part3),
                "Stock level for a non-existent part should be 0");
    }

    /**
     * Tests that findPartById correctly returns a part that exists.
     */
    @Test
    void testFindPartByIdSuccess() {
        // Act: Find part by its ID.
        Part foundPart = inventory.findPartById("P1002");

        // Assert: The correct Part object should be returned.
        assertNotNull(foundPart, "Should find an existing part");
        assertEquals(part2, foundPart, "The found part should be the same as part2");
        assertEquals("P1002", foundPart.partID());
    }

    /**
     * Tests that findPartById returns null for a part ID that does not exist.
     */
    @Test
    void testFindPartByIdNotFound() {
        // Act: Find a part ID that doesn't exist.
        Part foundPart = inventory.findPartById("P8888");

        // Assert: The result should be null.
        assertNull(foundPart, "Should return null for a non-existent part ID");
    }

    /**
     * Tests that getStockLevel returns the correct quantity for existing
     * and non-existing parts.
     */
    @Test
    void testGetStockLevel() {
        // Arrange: A new part not in inventory
        Part part3 = new Part("P9999", "Missing Part", "");

        // Act & Assert:
        assertEquals(25, inventory.getStockLevel(part1), "Should return 25 for part1");
        assertEquals(30, inventory.getStockLevel(part2), "Should return 30 for part2");
        assertEquals(0, inventory.getStockLevel(part3),
                "Should return 0 for a part not in inventory (getOrDefault)");
    }
}