package cs451;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

enum OrderType {
    BROADCAST,
    LATTICE
}

class Order {

    public OrderType type;
    public int maxMessages;
    public boolean isCompleted = false;
    public int destId;

    public int proposalsCount;
    public int maxProposalSize;
    public int distinctValues;
    public java.util.List<java.util.List<Integer>> proposals;

    public Order(OrderType type, int maxMessages) {
        this.type = type;
        this.maxMessages = maxMessages;
        this.destId = -1;
    }

    public Order(OrderType type, int proposalsCount, int maxProposalSize, int distinctValues, java.util.List<java.util.List<Integer>> proposals) {
        this.type = type;
        this.proposalsCount = proposalsCount;
        this.maxProposalSize = maxProposalSize;
        this.distinctValues = distinctValues;
        this.proposals = proposals;
    }

}

public class ConfigParser {

    private String path;
    private Order order;

    public boolean populate(String value, int myId) {
        File file = new File(value);
        path = file.getPath();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line == null || line.isBlank()) {
                System.err.println("Invalid config format: empty file");
                return false;
            }

            String[] firstLine = line.trim().split("\\s+");
            if (firstLine.length == 1) {
                this.order = new Order(OrderType.BROADCAST, Integer.parseInt(firstLine[0]));
            } else if (firstLine.length == 3) {
                int proposalsCount = Integer.parseInt(firstLine[0]);
                int maxProposalSize = Integer.parseInt(firstLine[1]);
                int distinctValues = Integer.parseInt(firstLine[2]);

                List<List<Integer>> proposals = new ArrayList<>();
                String proposalLine;
                while ((proposalLine = br.readLine()) != null) {
                    proposalLine = proposalLine.trim();
                    if (proposalLine.isEmpty()) {
                        continue;
                    }

                    String[] parts = proposalLine.split("\\s+");
                    if (parts.length > maxProposalSize) {
                        System.err.println("Invalid config format: proposal exceeds max size");
                        return false;
                    }

                    Set<Integer> unique = new LinkedHashSet<>();
                    for (String p : parts) {
                        if (p.isEmpty()) continue;
                        int v = Integer.parseInt(p);
                        if (v <= 0) {
                            System.err.println("Invalid proposal value, must be positive");
                            return false;
                        }
                        unique.add(v);
                    }
                    proposals.add(new ArrayList<>(unique));
                }

                if (proposals.size() != proposalsCount) {
                    System.err.println("Invalid config format: expected " + proposalsCount + " proposals, found " + proposals.size());
                    return false;
                }

                this.order = new Order(OrderType.LATTICE, proposalsCount, maxProposalSize, distinctValues, proposals);
            } else {
                System.err.println("Invalid config format");
                return false;
            }
        } catch (Exception e) {
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
