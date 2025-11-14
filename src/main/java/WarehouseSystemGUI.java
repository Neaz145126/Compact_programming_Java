import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * This is the main GUI class and entry point for the application.
 * It provides a Swing interface to start/stop the simulation,
 * add tasks, and monitor robots, inventory, and logs in real-time.
 *
 * UPDATE: Includes a new panel to monitor Charging Stations.
 */
public class WarehouseSystemGUI extends JFrame {

    // --- Simulation Backend ---
    private Warehouse warehouse;
    private SwingWorker<Void, Void> simulationWorker;

    // --- UI Components ---
    private final JButton startButton;
    private final JButton stopButton;
    private final JComboBox<Part> partComboBox;
    private final JSpinner quantitySpinner;
    private final JButton addTaskButton;
    private final JComboBox<String> logFileComboBox;
    private final JTextArea logTextArea;
    private final JButton refreshLogButton;

    // --- Table Models ---
    private final DefaultTableModel robotTableModel;
    private final DefaultTableModel inventoryTableModel;
    private final DefaultTableModel stationTableModel; // <-- NEW
    private final JTable robotTable;
    private final JTable inventoryTable;
    private final JTable stationTable; // <-- NEW

    // --- UI Update Timer ---
    private final Timer updateTimer;

    // --- Column Names ---
    private final String[] robotColumnNames = {"Robot ID", "Status", "Task ID", "Battery"};
    private final String[] inventoryColumnNames = {"Part ID", "Part Name", "Stock"};
    private final String[] stationColumnNames = {"Station ID", "Status", "Charging Robot"}; // <-- NEW

    /**
     * This is the single, correct constructor.
     * It initializes all 'final' components and builds the UI.
     */
    public WarehouseSystemGUI() {
        setTitle("Warehouse Control System");
        setSize(1200, 800); // Made window wider
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- Initialize Models (must be done before panels) ---
        robotTableModel = new DefaultTableModel(robotColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        robotTable = new JTable(robotTableModel);

        inventoryTableModel = new DefaultTableModel(inventoryColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        inventoryTable = new JTable(inventoryTableModel);

        // --- NEW: Station Table Model ---
        stationTableModel = new DefaultTableModel(stationColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        stationTable = new JTable(stationTableModel);

        // --- Initialize Components for Panels ---
        startButton = new JButton("Start Simulation");
        stopButton = new JButton("Stop Simulation");
        partComboBox = new JComboBox<>(new Vector<>(PartDefinitions.createSampleParts()));
        quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        addTaskButton = new JButton("Add Task");
        logFileComboBox = new JComboBox<>();
        logTextArea = new JTextArea();
        refreshLogButton = new JButton("Refresh Log");

        // --- Build Panels ---
        JPanel controlPanel = initControlPanel();
        JPanel statusPanel = initStatusPanel();
        JPanel taskPanel = initTaskPanel();

        // --- Layout ---
        add(controlPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.CENTER);
        add(taskPanel, BorderLayout.EAST);

        // --- UI Update Timer (polls every 500ms) ---
        updateTimer = new Timer(500, e -> updateStatusPanels());

        // --- Initial Component State ---
        stopButton.setEnabled(false);
        addTaskButton.setEnabled(false);
    }

    /**
     * Top panel with Start/Stop buttons.
     */
    private JPanel initControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        return controlPanel;
    }

    /**
     * Center panel with Robot, Inventory, and Station status tables.
     */
    private JPanel initStatusPanel() {
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // --- Robot Panel ---
        statusPanel.add(new JLabel("Robot Status (Live)"));
        statusPanel.add(initTablePanel(robotTable, new int[]{80, 120, 80, 60}));

        // --- NEW: Station Panel ---
        statusPanel.add(Box.createVerticalStrut(10));
        statusPanel.add(new JLabel("Charging Station Status (Live)"));
        statusPanel.add(initTablePanel(stationTable, new int[]{80, 100, 120}));

        // --- Inventory Panel ---
        statusPanel.add(Box.createVerticalStrut(10));
        statusPanel.add(new JLabel("Inventory (Live)"));
        statusPanel.add(initTablePanel(inventoryTable, new int[]{80, 200, 60}));

        return statusPanel;
    }

    /**
     * Helper method to create a JScrollPane for a JTable with set column widths.
     */
    private JScrollPane initTablePanel(JTable table, int[] widths) {
        table.setFillsViewportHeight(true);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Monospaced", Font.BOLD, 12));

        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < widths.length; i++) {
            columnModel.getColumn(i).setPreferredWidth(widths[i]);
        }
        return new JScrollPane(table);
    }


    /**
     * Right-hand panel for adding tasks and viewing logs.
     */
    private JPanel initTaskPanel() {
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        sidePanel.setPreferredSize(new Dimension(350, 0));

        // --- Add Task Panel ---
        JPanel addTaskPanel = new JPanel(new GridBagLayout());
        addTaskPanel.setBorder(BorderFactory.createTitledBorder("Add New Task"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        addTaskPanel.add(new JLabel("Part:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        partComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Part) {
                    Part part = (Part) value;
                    setText(part.name() + " (" + part.partID() + ")");
                }
                return this;
            }
        });
        addTaskPanel.add(partComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        addTaskPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        addTaskPanel.add(quantitySpinner, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        addTaskPanel.add(addTaskButton, gbc);
        addTaskButton.addActionListener(e -> addNewTask());

        // --- Log Viewer Panel ---
        JPanel logViewerPanel = new JPanel(new BorderLayout(5, 5));
        logViewerPanel.setBorder(BorderFactory.createTitledBorder("Log Viewer"));

        JPanel logControlPanel = new JPanel(new BorderLayout(5, 0));
        logControlPanel.add(logFileComboBox, BorderLayout.CENTER);
        logControlPanel.add(refreshLogButton, BorderLayout.EAST);

        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScrollPane = new JScrollPane(logTextArea);

        logViewerPanel.add(logControlPanel, BorderLayout.NORTH);
        logViewerPanel.add(logScrollPane, BorderLayout.CENTER);

        refreshLogButton.addActionListener(e -> loadSelectedLog());

        // Add sub-panels to main side panel
        sidePanel.add(addTaskPanel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(logViewerPanel);

        return sidePanel;
    }

    // --- Simulation Control ---

    private void startSimulation() {
        warehouse = new Warehouse(10, 5); // 10 Robots, 5 Stations

        simulationWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                warehouse.startSimulation();
                return null;
            }
            @Override
            protected void done() {
                stopSimulation();
            }
        };
        simulationWorker.execute();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        addTaskButton.setEnabled(true);

        refreshLogFileList();
        updateTimer.start();
        System.out.println("Simulation started.");
    }

    private void stopSimulation() {
        if (warehouse != null) {
            warehouse.stopSimulation();
        }
        if (simulationWorker != null) {
            simulationWorker.cancel(true);
        }

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        addTaskButton.setEnabled(false);

        if (updateTimer.isRunning()) {
            updateTimer.stop();
        }
        System.out.println("Simulation stopped.");
    }

    // --- UI Update Methods ---

    private void updateStatusPanels() {
        if (warehouse == null) return;

        // --- Update Robot Table ---
        robotTableModel.setRowCount(0);
        List<Robot> robots = warehouse.getRobots();
        robots.stream()
                .sorted(Comparator.comparing(Robot::getRobotID))
                .forEach(robot -> {
                    Vector<Object> row = new Vector<>();
                    row.add(robot.getRobotID());
                    row.add(robot.getStatus());
                    PartRequest task = robot.getCurrentTask();
                    row.add((task == null) ? "---" : task.requestID());
                    row.add(robot.getBatteryLevel() + "%");
                    robotTableModel.addRow(row);
                });

        // --- NEW: Update Station Table ---
        stationTableModel.setRowCount(0);
        List<ChargingStation> stations = warehouse.getStations();
        stations.stream()
                .sorted(Comparator.comparing(ChargingStation::getStationID))
                .forEach(station -> {
                    Vector<Object> row = new Vector<>();
                    row.add(station.getStationID());
                    Robot chargingRobot = station.getCurrentRobot();
                    if (chargingRobot != null) {
                        row.add("CHARGING");
                        row.add(chargingRobot.getRobotID());
                    } else {
                        row.add("IDLE");
                        row.add("---");
                    }
                    stationTableModel.addRow(row);
                });


        // --- Update Inventory Table ---
        inventoryTableModel.setRowCount(0);
        Map<Part, Integer> stockMap = warehouse.getInventory().getStockMap();
        stockMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Part::partID)))
                .forEach(entry -> {
                    Vector<Object> row = new Vector<>();
                    row.add(entry.getKey().partID());
                    row.add(entry.getKey().name());
                    row.add(entry.getValue());
                    inventoryTableModel.addRow(row);
                });
    }

    private void addNewTask() {
        Part selectedPart = (Part) partComboBox.getSelectedItem();
        int quantity = (int) quantitySpinner.getValue();

        if (selectedPart != null && warehouse != null) {
            warehouse.getRequestManager().addNewRequest(selectedPart, quantity);
            JOptionPane.showMessageDialog(this,
                    "Task added to queue: " + quantity + "x " + selectedPart.name(),
                    "Task Added",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshLogFileList() {
        logFileComboBox.removeAllItems();
        try {
            List<String> logFiles = LoggerUtil.getLogFiles();
            for (String logFile : logFiles) {
                logFileComboBox.addItem(logFile);
            }
        } catch (Exception e) {
            logTextArea.setText("Error reading log directory: \n" + e.getMessage());
        }
    }

    private void loadSelectedLog() {
        String selectedFile = (String) logFileComboBox.getSelectedItem();
        if (selectedFile == null) {
            logTextArea.setText("No log file selected.");
            return;
        }
        try {
            String content = LoggerUtil.getLogContent(selectedFile);
            logTextArea.setText(content);
            logTextArea.setCaretPosition(0); // Scroll to top
        } catch (Exception e) {
            logTextArea.setText("Error reading log file: \n" + e.getMessage());
        }
    }


    // --- Main Method ---
    public static void main(String[] args) {
        // Run the GUI on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            WarehouseSystemGUI gui = new WarehouseSystemGUI();
            gui.setVisible(true);
        });
    }
}