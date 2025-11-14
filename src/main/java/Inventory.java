import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CONCURRENCY UPDATE: This class is now thread-safe.
 * - Uses ConcurrentHashMap for the stock.
 * - Synchronizes the removeStock method.
 * - ADDED: A public getter for the GUI to read the stock levels.
 */
public class Inventory {

    private final int capacity;
    private final Map<Part, Integer> stock;
    private final LoggerUtil logger;

    public Inventory(int capacity, Map<Part, Integer> initialStock) {
        this.capacity = capacity;
        this.stock = new ConcurrentHashMap<>(initialStock);
        this.logger = new LoggerUtil("InventoryLog");

        int initialQuantity = this.stock.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (initialQuantity > this.capacity) {
            logger.log("CRITICAL ERROR: Initial stock " + initialQuantity
                    + " exceeds capacity " + this.capacity);
        } else {
            logger.log("Inventory initialized. Total stock: " + initialQuantity
                    + " / " + this.capacity);
        }
    }

    public Part findPartById(String partID) {
        for (Part part : this.stock.keySet()) {
            if (part.partID().equals(partID)) {
                return part;
            }
        }
        logger.log("Part not found with ID: " + partID);
        return null;
    }

    /**
     * CONCURRENCY UPDATE: This method is now synchronized.
     */
    public synchronized boolean removeStock(Part part, int quantity) throws InsufficientStockException {
        if (quantity <= 0) {
            logger.log("Attempted to remove non-positive quantity: " + quantity);
            return false;
        }

        int currentQuantity = this.stock.getOrDefault(part, 0);

        if (quantity > currentQuantity) {
            String errorMsg = "Not enough stock of " + part.name()
                    + ". Requested: " + quantity + ", Available: " + currentQuantity;
            logger.log("Error: " + errorMsg);
            throw new InsufficientStockException(errorMsg);
        }

        this.stock.put(part, currentQuantity - quantity);
        logger.log("Removed " + quantity + " units of " + part.name()
                + ". Remaining: " + (currentQuantity - quantity));
        return true;
    }

    // --- Getters ---

    public Map<Part, Integer> getStockMap() {
        return Collections.unmodifiableMap(this.stock);
    }

    public int getStockLevel(Part part) {
        return this.stock.getOrDefault(part, 0);
    }

    public void printInventory() {
        logger.log("========== INVENTORY REPORT ==========");
        logger.log("Capacity: " + this.stock.values().stream().mapToInt(Integer::intValue).sum()
                + " / " + this.capacity);
        for (Map.Entry<Part, Integer> entry : stock.entrySet()) {
            logger.log(String.format("- %-20s (ID: %s): %d units",
                    entry.getKey().name(),
                    entry.getKey().partID(),
                    entry.getValue()));
        }
        logger.log("=====================================");
    }
}