package cs451;

import java.util.ArrayList;
import java.util.List;

public class Message {
    public final String payload;

    public Message(String payload) {
        this.payload = payload;
    }   

    public String toString() {
        return payload;
    }
}

class Packet {
    public final String srcIp;
    public final int srcPort;
    public final String destIp;
    public final int destPort;
    public final int packetId;
    public final List<Message> messages;

    public Packet(String srcIp, int srcPort, String destIp, int destPort, int packetId, List<Message> messages) {
        this.srcIp = srcIp;
        this.srcPort = srcPort;
        this.destIp = destIp;
        this.destPort = destPort;
        this.packetId = packetId;
        if (messages.size() > 8) {
            throw new IllegalArgumentException("A packet can contain at most 8 messages.");
        }
        this.messages = messages;
    }

    public static Packet fromString(String str) {
        try {
            String[] parts = str.split("; ");
            if (parts.length < 4) return null;

            int packetId = Integer.parseInt(parts[0].split("Packet ID: ")[1]);

            String srcPart = parts[1].split("Source: ")[1];
            String[] srcParts = srcPart.split(":");
            String srcIp = srcParts[0];
            int srcPort = Integer.parseInt(srcParts[1]);

            String destPart = parts[2].split("Destination: ")[1];
            String[] destParts = destPart.split(":");
            String destIp = destParts[0];
            int destPort = Integer.parseInt(destParts[1]);

            List<Message> messages = new ArrayList<>();
            String messagesStr = parts[3].split("Messages: ")[1];
            messagesStr = messagesStr.substring(1, messagesStr.length() - 1); // Remove [ and ]
            
            if (!messagesStr.isEmpty()) {
                String[] messageParts = messagesStr.split(", ");
                for (String message : messageParts) {
                    messages.add(new Message(message));
                }
            }
            return new Packet(srcIp, srcPort, destIp, destPort, packetId, messages);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Packet ID: ").append(packetId)
          .append("; Source: ").append(srcIp).append(":").append(srcPort)
          .append("; Destination: ").append(destIp).append(":").append(destPort)
          .append("; Messages: [");
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).payload);
            if (i < messages.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}