import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public class PartRequestManager implements Runnable {
    private final Queue<PartRequest> requestQueue;
    private final Inventory inventory;
    private static final String REQUEST_FILE = "pending_requests.txt";
    private final LoggerUtil logger;

    private volatile boolean simulationIsRunning = true;

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.inventory = inventory;
        this.logger = new LoggerUtil("PartRequestManager");
        logger.log("PartRequestManager initialized.");
    }

    @Override
    public void run() {
        logger.log("PartRequestManager thread started.");
        while (simulationIsRunning) {
            try {
                // Run the logic to load requests from the file.
                loadRequestsFromFile();

                // Wait for 5 seconds before checking the file again.
                Thread.sleep(5000);
            } catch (RequestProcessingException e) {
                logger.log("CRITICAL: Failed to process part requests file. " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                // This happens when the simulation is told to shut down.
                simulationIsRunning = false;
                logger.log("PartRequestManager thread interrupted and shutting down.");
            }
        }
        logger.log("PartRequestManager thread stopped.");
    }

    public void loadRequestsFromFile() throws RequestProcessingException {
        // This try-with-resources block automatically closes the 'reader'.
        try (BufferedReader reader = new BufferedReader(new FileReader(REQUEST_FILE))) {

            String line;
            boolean requestsFound = false;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                requestsFound = true;

                String[] parts = line.split(",");
                if (parts.length != 2) {
                    logger.log("Invalid request format in file: " + line);
                    continue;
                }

                String partID = parts[0].trim();
                // This line can throw NumberFormatException
                int quantity = Integer.parseInt(parts[1].trim());

                Part part = inventory.findPartById(partID);

                if (part != null) {
                    PartRequest newRequest = PartRequest.create(part, quantity);
                    // CONCURRENCY CHANGE: Add to the thread-safe queue.
                    this.requestQueue.add(newRequest);
                    logger.log("Read new request from file: " + newRequest);
                } else {
                    logger.log("Unknown partID from file: " + partID);
                }
            }

            if (requestsFound) {
                // Clear the file only after successful processing
                try (PrintWriter writer = new PrintWriter(new FileWriter(REQUEST_FILE, false))) {
                    writer.print("");
                } catch (IOException e) {
                    // This is a new problem, so we chain it as well.
                    throw new RequestProcessingException("Could not clear request file: " + REQUEST_FILE, e);
                }
            }

        } catch (IOException | NumberFormatException e) {
            // We wrap the low-level I/O error in our custom, high-level exception.
            throw new RequestProcessingException("Error reading request file: " + REQUEST_FILE, e);
        }
    }


    public PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }

    public void stop() {
        this.simulationIsRunning = false;
    }
}