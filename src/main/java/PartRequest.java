public record PartRequest(
        String requestID,
        Part part,
        int neededQuantity,
        RequestStatus status
) {

    private static long requestCounter = 0;

    public static PartRequest create(Part part, int quantity) {
        if (part == null) {
            throw new IllegalArgumentException("Part cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        requestCounter++;
        String newID = "Task-" + requestCounter;

        return new PartRequest(newID, part, quantity, RequestStatus.PENDING);
    }

    public PartRequest withStatus(RequestStatus newStatus) {
        return new PartRequest(this.requestID, this.part, this.neededQuantity, newStatus);
    }
}