import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * CONCURRENCY UPDATE: This class now implements Runnable and runs on its own thread.
 * - It uses a thread-safe ConcurrentLinkedQueue.
 * - Its 'run' method periodically checks the request file for new tasks
 * - It now uses synchronized methods and notifyAll() to implement
 * an instant "Producer" for the "Consumer" (Robot) threads.
 */
public class PartRequestManager implements Runnable {
    private final Queue<PartRequest> requestQueue;
    private final Inventory inventory;
    private static final String REQUEST_FILE = "pending_requests.txt";
    private final File requestFileHandle;
    private final LoggerUtil logger;
    private volatile boolean simulationIsRunning = true;
    private static final long FILE_POLL_INTERVAL_MS = 5000; // 5 seconds

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.inventory = inventory;
        this.logger = new LoggerUtil("PartRequestManager");
        this.requestFileHandle = new File(REQUEST_FILE);
        // Create the file if it doesn't exist
        try {
            if (requestFileHandle.createNewFile()) {
                logger.log("Created new empty pending_requests.txt");
            }
        } catch (IOException e) {
            logger.log("CRITICAL: Could not create pending_requests.txt: " + e.getMessage());
        }
        logger.log("PartRequestManager initialized.");
    }

    /**
     * GUI INTEGRATION: New public method for the GUI to add a task.
     * This is now synchronized and notifies waiting robots.
     */
    public synchronized void addNewRequest(Part part, int quantity) {
        PartRequest newRequest = PartRequest.create(part, quantity);
        this.requestQueue.add(newRequest);
        logger.log("GUI added new request: " + newRequest);
        // Wake up any and all robot threads that are waiting for a task
        notifyAll();
    }

    @Override
    public void run() {
        logger.log("PartRequestManager thread started. Polling file every " + FILE_POLL_INTERVAL_MS + "ms.");
        while (simulationIsRunning) {
            try {
                // Periodically load new requests from the file
                loadRequestsFromFile();
                Thread.sleep(FILE_POLL_INTERVAL_MS);
            } catch (RequestProcessingException e) {
                // Log the failure but continue running
                logger.log("CRITICAL: Failed to process part requests file: " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                // This is the signal to shut down
                simulationIsRunning = false;
            }
        }
        logger.log("PartRequestManager thread stopped.");
    }

    /**
     * This method is now synchronized.
     * It also calls notifyAll() if it finds tasks, to wake up robots.
     */
    public synchronized void loadRequestsFromFile() throws RequestProcessingException {
        if (!requestFileHandle.exists()) {
            return; // File doesn't exist, nothing to do
        }

        boolean requestsFound = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(requestFileHandle))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                requestsFound = true; // Mark that we found something

                String[] parts = line.split(",");
                if (parts.length != 2) {
                    logger.log("Invalid request format in file: " + line);
                    continue;
                }

                String partID = parts[0].trim();
                int quantity = Integer.parseInt(parts[1].trim());
                Part part = inventory.findPartById(partID);

                if (part != null) {
                    PartRequest newRequest = PartRequest.create(part, quantity);
                    this.requestQueue.add(newRequest);
                    logger.log("Read new request from file: " + newRequest);
                } else {
                    logger.log("Unknown partID from file: " + partID);
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new RequestProcessingException("Error reading request file: " + REQUEST_FILE, e);
        }

        if (requestsFound) {
            // Clear the file only after successful processing
            try (PrintWriter writer = new PrintWriter(new FileWriter(requestFileHandle, false))) {
                writer.print("");
            } catch (IOException e) {
                throw new RequestProcessingException("Could not clear request file: " + REQUEST_FILE, e);
            }
            // Wake up waiting robots since we added tasks
            notifyAll();
        }
    }

    /**
     * This method is now synchronized.
     * Only one robot can check the queue at a time.
     */
    public synchronized PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }

    public void stop() {
        this.simulationIsRunning = false;
    }
}