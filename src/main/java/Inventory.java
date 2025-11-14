import java.util.HashMap;
import java.util.Map;

public class Inventory {

    private final int capacity;
    private final Map<Part, Integer> stock;
    private final LoggerUtil logger;

    public Inventory(int capacity, Map<Part, Integer> initialStock) {
        this.capacity = capacity;
        this.stock = new HashMap<>(initialStock);

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
        for (Part part : this.stock.keySet()) {
            if (part.partID().equals(partID)) {
                logger.log("Part found: " + part.name() + " (ID: " + partID + ")");
                return part;
            }
        }
        logger.log("Part not found with ID: " + partID);
        return null; // Not found
    }

    public boolean removeStock(Part part, int quantity) {
        if (quantity <= 0) {
            logger.log("Attempted to remove non-positive quantity: " + quantity
                    + " for part " + part.name());
            return false;
        }

        int currentQuantity = this.stock.getOrDefault(part, 0);

        if (quantity > currentQuantity) {
            logger.log("Error: Not enough stock of " + part.name()
                    + ". Requested: " + quantity + ", Available: " + currentQuantity);
            return false;
        }

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
        for (Map.Entry<Part, Integer> entry : stock.entrySet()) {
            logger.log(String.format("- %-20s (ID: %s): %d units",
                    entry.getKey().name(),
                    entry.getKey().partID(),
                    entry.getValue()));
        }
        logger.log("=====================================");
    }
}
