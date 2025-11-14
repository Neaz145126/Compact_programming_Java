import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CONCURRENCY UPDATE: This class is now thread-safe.
 * - Uses ConcurrentHashMap for the stock.
 * - Synchronizes the removeStock method.
 * - ADDED: A public getter for the GUI to read the stock levels.
 *
 * REFACTOR:
 * - Added a 'partIdLookup' map for O(1) part lookups by ID,
 * which is much faster than the previous O(N) scan.
 */
public class Inventory {

    private final int capacity;
    // This map stores the *quantity* of each part, keyed by the Part object.
    // ConcurrentHashMap is thread-safe for reads and single-item updates.
    private final Map<Part, Integer> stock;

    // --- REFACTOR: Added for fast part lookups ---
    // This map lets us find a Part *object* using its *String ID* instantly.
    private final Map<String, Part> partIdLookup;

    private final LoggerUtil logger;

    public Inventory(int capacity, Map<Part, Integer> initialStock) {
        this.capacity = capacity;
        this.stock = new ConcurrentHashMap<>(initialStock);
        this.logger = new LoggerUtil("InventoryLog");

        // --- REFACTOR: Build the fast lookup map ---
        // We create a new, immutable map that takes all the 'Part' objects
        // from the initial stock and indexes them by their 'partID' string.
        this.partIdLookup = initialStock.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(Part::partID, Function.identity()));
        // 'Function.identity()' is a short way of saying (part -> part)

        // --- End of Refactor ---

        int initialQuantity = this.stock.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (initialQuantity > this.capacity) {
            logger.log("CRITICAL ERROR: Initial stock " + initialQuantity
                    + " exceeds capacity " + this.capacity);
        } else {
            logger.log("Inventory initialized. Total stock: " + initialQuantity
                    + " / " + this.capacity);
            logger.log("Created fast lookup map with " + this.partIdLookup.size() + " parts.");
        }
    }

    /**
     * REFACTOR: This method is now O(1) (instant) instead of O(N) (slow scan).
     * It uses the 'partIdLookup' map.
     */
    public Part findPartById(String partID) {
        Part foundPart = this.partIdLookup.get(partID);
        if (foundPart == null) {
            logger.log("Part not found with ID: " + partID);
        }
        return foundPart;
    }

    /**
     * CONCURRENCY: This method is synchronized.
     * This acts as a lock, ensuring only one robot thread can
     * modify the stock at a time, preventing race conditions.
     */
    public synchronized boolean removeStock(Part part, int quantity) throws InsufficientStockException {
        if (quantity <= 0) {
            logger.log("Attempted to remove non-positive quantity: " + quantity);
            return false;
        }

        // 'getOrDefault' is thread-safe and fast because 'Part' is a record
        // (with a stable hashCode/equals).
        int currentQuantity = this.stock.getOrDefault(part, 0);

        if (quantity > currentQuantity) {
            String errorMsg = "Not enough stock of " + part.name()
                    + ". Requested: " + quantity + ", Available: " + currentQuantity;
            logger.log("Error: " + errorMsg);
            throw new InsufficientStockException(errorMsg);
        }

        // 'put' is also thread-safe, but we need the whole
        // 'check-then-set' operation to be atomic, which is why
        // the entire method is 'synchronized'.
        this.stock.put(part, currentQuantity - quantity);
        logger.log("Removed " + quantity + " units of " + part.name()
                + ". Remaining: " + (currentQuantity - quantity));
        return true;
    }

    // --- Getters ---

    /**
     * Returns a read-only view of the stock map for the GUI.
     */
    public Map<Part, Integer> getStockMap() {
        return Collections.unmodifiableMap(this.stock);
    }

    /**
     * Returns the current stock level for a specific part.
     * Fast O(1) lookup.
     */
    public int getStockLevel(Part part) {
        return this.stock.getOrDefault(part, 0);
    }

    /**
     * Logs the current inventory state.
     */
    public void printInventory() {
        logger.log("========== INVENTORY REPORT ==========");
        logger.log("Capacity: " + this.stock.values().stream().mapToInt(Integer::intValue).sum()
                + " / " + this.capacity);

        // We can use the partIdLookup to print in a sorted, predictable order.
        this.partIdLookup.keySet().stream()
                .sorted()
                .map(this.partIdLookup::get) // Get the Part object for the sorted ID
                .forEach(part -> {
                    int quantity = getStockLevel(part);
                    logger.log(String.format("- %-20s (ID: %s): %d units",
                            part.name(),
                            part.partID(),
                            quantity));
                });
        logger.log("=====================================");
    }
}