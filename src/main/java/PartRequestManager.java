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

    public void update() {
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

        } catch (IOException e) {

        } catch (NumberFormatException e) {
            System.err.println("PartRequestManager: Invalid quantity in request file.");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(REQUEST_FILE, false))) {
            writer.print("");
        } catch (IOException e) {
            System.err.println("PartRequestManager: Could not clear request file.");
        }
    }

    public PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }
}
