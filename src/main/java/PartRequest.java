import java.util.concurrent.atomic.AtomicLong;

/**
 * A data record for a PartRequest.
 * CONCURRENCY: This is now thread-safe using AtomicLong
 * for the request counter.
 */
public record PartRequest(
        String requestID,
        Part part,
        int neededQuantity,
        RequestStatus status
) {

    private static final AtomicLong requestCounter = new AtomicLong(0);

    public static PartRequest create(Part part, int quantity) {
        if (part == null) {
            throw new IllegalArgumentException("Part cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        long newCount = requestCounter.incrementAndGet();
        String newID = "Task-" + newCount;

        return new PartRequest(newID, part, quantity, RequestStatus.PENDING);
    }

    public PartRequest withStatus(RequestStatus newStatus) {
        return new PartRequest(this.requestID, this.part, this.neededQuantity, newStatus);
    }
}