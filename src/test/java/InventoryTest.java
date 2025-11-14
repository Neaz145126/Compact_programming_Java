import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test class for the Inventory.
 *
 * NOTE: The Inventory constructor requires a LoggerUtil, which writes log
 * files to a "Logs" directory. Running these tests WILL create a
 * "Logs" directory and "InventoryLog-..." files as a side effect.
 */
class InventoryTest {

    private Inventory inventory;
    private Part partOilFilter;
    private Part partAirFilter;
    private Part partNotInStock;

    // This method runs before EACH test
    @BeforeEach
    void setUp() {
        // Create sample parts
        partOilFilter = new Part("P1001", "Oil Filter", "Standard oil filter");
        partAirFilter = new Part("P1002", "Air Filter", "Engine air filter");
        partNotInStock = new Part("P9999", "Flux Capacitor", "Not a real part");

        // Create initial stock
        Map<Part, Integer> initialStock = new HashMap<>();
        initialStock.put(partOilFilter, 25);
        initialStock.put(partAirFilter, 30);

        // Create a new inventory instance for each test
        // Use a capacity of 500
        inventory = new Inventory(500, initialStock);
    }

    @Test
    @DisplayName("Should find an existing part by its ID")
    void testFindPartByIdSuccess() {
        Part foundPart = inventory.findPartById("P1001");
        assertNotNull(foundPart, "Part should be found");
        assertEquals(partOilFilter, foundPart, "The correct part object should be returned");
    }

    @Test
    @DisplayName("Should return null when part ID does not exist")
    void testFindPartByIdFailure() {
        Part foundPart = inventory.findPartById("P-INVALID");
        assertNull(foundPart, "Part should not be found and null should be returned");
    }

    @Test
    @DisplayName("Should successfully remove stock when quantity is sufficient")
    void testRemoveStockSuccess() {
        int initialStock = inventory.getStockLevel(partOilFilter); // 25
        int quantityToRemove = 5;

        // The action being tested
        // We use assertDoesNotThrow to confirm no exception is thrown
        assertDoesNotThrow(() -> {
            inventory.removeStock(partOilFilter, quantityToRemove);
        }, "Should not throw an exception when removing valid stock");

        // Verify the stock level
        int expectedStock = initialStock - quantityToRemove; // 20
        assertEquals(expectedStock, inventory.getStockLevel(partOilFilter), "Stock level should be reduced");
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when removing too much stock")
    void testRemoveStockThrowsInsufficientStockException() {
        int initialStock = inventory.getStockLevel(partAirFilter); // 30
        int quantityToRemove = 31; // One more than available

        // The action being tested
        // We assert that the specific exception is thrown
        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> inventory.removeStock(partAirFilter, quantityToRemove),
                "Should throw InsufficientStockException"
        );

        // Optional: Check the exception message
        assertTrue(exception.getMessage().contains("Not enough stock"), "Exception message should be informative");

        // Verify that stock level did NOT change
        assertEquals(initialStock, inventory.getStockLevel(partAirFilter), "Stock level should not change on failure");
    }

    @Test
    @DisplayName("Should return false for non-positive removal quantity and not change stock")
    void testRemoveStockWithInvalidQuantity() {
        int initialStock = inventory.getStockLevel(partOilFilter); // 25
        boolean result = false;

        // The action being tested (with 0 quantity)
        try {
            result = inventory.removeStock(partOilFilter, 0);
        } catch (InsufficientStockException e) {
            // This shouldn't happen for this test case
            fail("Should not throw InsufficientStockException for 0 quantity", e);
        }

        // Verify results for 0 quantity
        assertFalse(result, "Removing 0 quantity should return false");
        assertEquals(initialStock, inventory.getStockLevel(partOilFilter), "Stock should not change for 0 quantity");

        // The action being tested (with negative quantity)
        try {
            result = inventory.removeStock(partOilFilter, -5);
        } catch (InsufficientStockException e) {
            fail("Should not throw InsufficientStockException for negative quantity", e);
        }

        // Verify results for negative quantity
        assertFalse(result, "Removing negative quantity should return false");
        assertEquals(initialStock, inventory.getStockLevel(partOilFilter), "Stock should not change for negative quantity");
    }
}