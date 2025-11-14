import java.util.concurrent.atomic.AtomicLong;

public record PartRequest(
        String requestID,
        Part part,
        int neededQuantity,
        RequestStatus status
) {

    // CONCURRENCY CHANGE: Use AtomicLong for a thread-safe counter.
    // This prevents two threads from creating a request at the same
    // time and getting the same ID.
    private static final AtomicLong requestCounter = new AtomicLong(0);

    public static PartRequest create(Part part, int quantity) {
        if (part == null) {
            throw new IllegalArgumentException("Part cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // CONCURRENCY CHANGE: Atomically increment the counter and get the new value.
        long newCount = requestCounter.incrementAndGet();
        String newID = "Task-" + newCount;

        return new PartRequest(newID, part, quantity, RequestStatus.PENDING);
    }

    public PartRequest withStatus(RequestStatus newStatus) {
        return new PartRequest(this.requestID, this.part, this.neededQuantity, newStatus);
    }
}