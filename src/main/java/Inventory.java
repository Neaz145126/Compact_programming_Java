import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Inventory {

    private final int capacity;
    // CONCURRENCY CHANGE: Use ConcurrentHashMap for thread-safe reads/writes.
    private Map<Part, Integer> stock;
    private final LoggerUtil logger;

    public Inventory(int capacity, Map<Part, Integer> initialStock) {
        this.capacity = capacity;
        // CONCURRENCY CHANGE: Initialize as ConcurrentHashMap
        this.stock = new ConcurrentHashMap<>(initialStock);

        // Initialize logger with inventory-specific file
        this.logger = new LoggerUtil("InventoryLog");

        int initialQuantity = this.stock.values().stream()
                .mapToInt(Integer::intValue) // Convert Integer to int
                .sum();                     // Sum them up

        if (initialQuantity > this.capacity) {
            logger.log("CRITICAL ERROR: Initial stock " + initialQuantity
                    + " exceeds capacity " + this.capacity);
        } else {
            logger.log("Inventory initialized. Total stock: " + initialQuantity
                    + " / " + this.capacity);
        }
    }

    public Part findPartById(String partID) {
        // This is a read-only operation, and ConcurrentHashMap is safe for
        // concurrent reads.
        for (Part part : this.stock.keySet()) {
            if (part.partID().equals(partID)) {
                // Logger is already synchronized, so this is safe.
                logger.log("Part found: " + part.name() + " (ID: " + partID + ")");
                return part;
            }
        }
        logger.log("Part not found with ID: " + partID);
        return null; // Not found
    }
    public synchronized boolean removeStock(Part part, int quantity) throws InsufficientStockException {
        if (quantity <= 0) {
            logger.log("Attempted to remove non-positive quantity: " + quantity
                    + " for part " + part.name());
            return false; // Still return false for invalid input
        }

        // Get the most up-to-date quantity
        int currentQuantity = this.stock.getOrDefault(part, 0);

        if (quantity > currentQuantity) {
            String errorMsg = "Not enough stock of " + part.name()
                    + ". Requested: " + quantity + ", Available: " + currentQuantity;
            logger.log("Error: " + errorMsg);
            throw new InsufficientStockException(errorMsg);
        }

        // This block is now safe from other threads.
        this.stock.put(part, currentQuantity - quantity);
        logger.log("Removed " + quantity + " units of " + part.name()
                + ". Remaining: " + (currentQuantity - quantity));
        return true;
    }

    // --- Getters ---

    public int getStockLevel(Part part) {
        return this.stock.getOrDefault(part, 0);
    }

    public int getTotalAvailableQuantity() {
        return this.stock.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public int getCapacity() {
        return this.capacity;
    }

    public void printInventory() {
        logger.log("========== INVENTORY REPORT ==========");
        logger.log("Capacity: " + this.getTotalAvailableQuantity() + " / " + this.capacity);
        // Iterating over ConcurrentHashMap is thread-safe.
        for (Map.Entry<Part, Integer> entry : stock.entrySet()) {
            logger.log(String.format("- %-20s (ID: %s): %d units",
                    entry.getKey().name(),
                    entry.getKey().partID(),
                    entry.getValue()));
        }
        logger.log("=====================================");
    }
}