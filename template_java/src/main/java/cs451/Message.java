package cs451;

public class Message {
    public enum Type {
        URB, HEARTBEAT
    }

    public final Type type;
    public final int senderId;
    public final int originalSenderId;
    public final String payload;

    private Message(Type type, int senderId, int originalSenderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.originalSenderId = originalSenderId;
        this.payload = payload;
    }

    public static Message makeUrb(int senderId, int originalSenderId, String payload) {
        return new Message(Type.URB, senderId, originalSenderId, payload);
    }

    public static Message makeHeartbeat(int senderId) {
        return new Message(Type.HEARTBEAT, senderId, -1, "");
    }

    public static Message parse(String content) {
        if (content == null || content.isEmpty()) return null;
        
        String[] parts = content.split(" ", 2);
        String typeStr = parts[0];
        
        try {
            if ("HEARTBEAT".equals(typeStr)) {
                int senderId = Integer.parseInt(parts[1]);
                return makeHeartbeat(senderId);
            } else if ("URB".equals(typeStr)) {
                // Format: URB senderId originalSenderId payload
                String[] args = parts[1].split(" ", 3);
                int senderId = Integer.parseInt(args[0]);
                int originalSenderId = Integer.parseInt(args[1]);
                String payload = args[2];
                return makeUrb(senderId, originalSenderId, payload);
            }
        } catch (Exception e) {
            // Malformed message
            return null;
        }
        return null;
    }

    public String toNetworkString() {
        if (type == Type.HEARTBEAT) {
            return "HEARTBEAT " + senderId;
        } else {
            return "URB " + senderId + " " + originalSenderId + " " + payload;
        }
    }
    
    public String getUniqueId() {
        return originalSenderId + ":" + payload;
    }
}