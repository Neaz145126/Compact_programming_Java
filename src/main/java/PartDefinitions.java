import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartDefinitions {

    public static List<Part> createSampleParts() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("P1001", "Oil Filter", "Standard oil filter"));
        parts.add(new Part("P1002", "Air Filter", "Engine air filter"));
        parts.add(new Part("P1003", "Spark Plug", "Iridium spark plug"));
        parts.add(new Part("P1004", "Brake Pad", "Front ceramic pads"));
        parts.add(new Part("P1005", "Brake Disc", "Vented front brake disc"));
        parts.add(new Part("P1006", "Wiper Blade", "22-inch all-weather"));
        parts.add(new Part("P1007", "Headlight Bulb", "H4 Halogen bulb"));
        parts.add(new Part("P1A008", "Taillight Bulb", "P21W bulb"));
        parts.add(new Part("P1009", "Battery", "12V 60Ah AGM battery"));
        parts.add(new Part("P1010", "Alternator", "120A alternator"));
        parts.add(new Part("P1S11", "Starter Motor", "1.4kW starter"));
        parts.add(new Part("P1012", "Timing Belt", "Rubber timing belt kit"));
        parts.add(new Part("P1013", "Water Pump", "Coolant water pump"));
        parts.add(new Part("P1014", "Radiator", "Aluminum core radiator"));
        parts.add(new Part("P1015", "Tire", "205/55R16 All-Season"));
        parts.add(new Part("P1016", "Wheel Rim", "16-inch alloy rim"));
        parts.add(new Part("P1017", "Shock Absorber", "Front gas shock"));
        parts.add(new Part("P1018", "Exhaust Muffler", "Stainless steel muffler"));
        parts.add(new Part("P1019", "Catalytic Converter", "OEM spec converter"));
        parts.add(new Part("P1020", "Fuel Injector", "Bosch fuel injector"));
        return parts;
    }

    public static Map<Part, Integer> getInitialStock(List<Part> partDefinitions) {
        Map<Part, Integer> initialStock = new HashMap<>();
        // Get parts by index (assuming order from createSampleParts)
        initialStock.put(partDefinitions.get(0), 25); // P1001
        initialStock.put(partDefinitions.get(1), 30); // P1002
        initialStock.put(partDefinitions.get(2), 50); // P1003
        initialStock.put(partDefinitions.get(3), 20); // P1004
        initialStock.put(partDefinitions.get(4), 50); // P1005
        initialStock.put(partDefinitions.get(5), 25); // P1006
        initialStock.put(partDefinitions.get(6), 30); // P1007
        initialStock.put(partDefinitions.get(7), 50); // P1008
        initialStock.put(partDefinitions.get(8), 20); // P1009
        initialStock.put(partDefinitions.get(9), 40); // P1010

        return initialStock;
    }
}