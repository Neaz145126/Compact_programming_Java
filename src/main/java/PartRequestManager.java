import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;

public class PartRequestManager {
    private final Queue<PartRequest> requestQueue;
    private final Inventory inventory;
    private static final String REQUEST_FILE = "pending_requests.txt";

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new LinkedList<>();
        this.inventory = inventory;
    }

    /**
     * HA2 UPDATE: This method now throws RequestProcessingException.
     * It uses a multi-catch block and exception chaining.
     *
     * @throws RequestProcessingException if reading or parsing the file fails.
     */
    public void update() throws RequestProcessingException {
        // REQUIREMENT C: "Resource Management"
        // This try-with-resources block automatically closes the 'reader'.
        try (BufferedReader reader = new BufferedReader(new FileReader(REQUEST_FILE))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.err.println("Invalid request format in file: " + line);
                    continue;
                }

                String partID = parts[0].trim();
                // This line can throw NumberFormatException
                int quantity = Integer.parseInt(parts[1].trim());

                Part part = inventory.findPartById(partID);

                if (part != null) {
                    PartRequest newRequest = PartRequest.create(part, quantity);
                    this.requestQueue.add(newRequest);
                    System.out.println("PartRequestManager: Read new request from file: " + newRequest);
                } else {
                    System.err.println("PartRequestManager: Unknown partID from file: " + partID);
                }
            }

        } catch (IOException | NumberFormatException e) {
            // REQUIREMENT A: "Handling Multiple Exceptions"
            // We catch two different exception types in one block.

            // REQUIREMENT D: "Chaining Exceptions"
            // We wrap the low-level I/O error in our custom, high-level exception.
            throw new RequestProcessingException("Error reading request file: " + REQUEST_FILE, e);
        }

        // Clear the file after successful processing
        try (PrintWriter writer = new PrintWriter(new FileWriter(REQUEST_FILE, false))) {
            writer.print("");
        } catch (IOException e) {
            // This is a new problem, so we chain it as well.
            throw new RequestProcessingException("Could not clear request file: " + REQUEST_FILE, e);
        }
    }

    public PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }
}