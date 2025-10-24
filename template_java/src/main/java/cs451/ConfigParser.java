package cs451;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

enum NodeType {
    SENDER,
    RECEIVER
}

class Order {

    public NodeType nodeType;
    public int maxMessages;
    public boolean isCompleted = false;
    public int destId;

    public Order(NodeType nodeType, int maxMessages) {
        this.nodeType = nodeType;
        this.maxMessages = maxMessages;
        this.destId = -1;
    }
    public Order(NodeType nodeType, int maxMessages, int destId) {
        this.nodeType = nodeType;
        this.maxMessages = maxMessages;
        this.destId = destId;
    }

}

public class ConfigParser {

    private String path;
    private Order order;

    public boolean populate(String value, int myId) {
        File file = new File(value);
        path = file.getPath();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split("\\s");
                if (args.length != 2) {
                    System.err.println("Invalid config format");
                    return false;
                }
                if (!args[1].equals(String.valueOf(myId))) {
                    this.order = new Order(NodeType.SENDER, Integer.parseInt(args[0]), Integer.parseInt(args[1]));
                }
                else {
                    this.order = new Order(NodeType.RECEIVER, 0);
                }
            }
            br.close();
        }
        catch (Exception e) {
            System.err.println("Error reading config file: " + e.getMessage());
            return false;
        }

        return true;
    }

    public String getPath() {
        return path;
    }

    public Order getOrder() {
        return this.order;
    }

}
