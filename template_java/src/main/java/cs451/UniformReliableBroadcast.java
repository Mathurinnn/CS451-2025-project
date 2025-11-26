package cs451;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class UniformReliableBroadcast {

    private final List<Host> hosts;
    private final int myId;
    private final DatagramSocket socket;
    private BiConsumer<Integer, String> deliverCallback;
    private volatile boolean running = true;
    
    private final Set<String> delivered = ConcurrentHashMap.newKeySet();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<Integer>> acks = new ConcurrentHashMap<>();
    private final Set<Integer> correct = ConcurrentHashMap.newKeySet();

    public UniformReliableBroadcast(List<Host> hosts, int myId, DatagramSocket socket) {
        this.hosts = hosts;
        this.myId = myId;
        this.socket = socket;
        for (Host h : hosts) {
            correct.add(h.getId());
        }
    }

    public void handleCrash(int hostId) {
        correct.remove(hostId);
        for (String msgId : pending) {
            checkDeliver(msgId);
        }
    }

    public void handleRestore(int hostId) {
        correct.add(hostId);
    }

    public void setDeliverCallback(BiConsumer<Integer, String> deliverCallback) {
        this.deliverCallback = deliverCallback;
    }

    public void start() {
        new Thread(this::retransmitLoop).start();
    }

    public void stop() {
        running = false;
    }

    private void retransmitLoop() {
        while (running) {
            try {
                for (String msgId : active) {
                    Set<Integer> msgAcks = acks.get(msgId);
                    boolean allAcked = true;
                    for (Host host : hosts) {
                        if (msgAcks == null || !msgAcks.contains(host.getId())) {
                            send(host, "URB " + myId + " " + msgId);
                            allAcked = false;
                        }
                    }
                    if (allAcked) {
                        active.remove(msgId);
                    }
                }
                Thread.sleep(100); // Retransmit every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void broadcast(String messageContent) {
        String msgId = myId + ":" + messageContent; // Unique ID
        if (!pending.contains(msgId)) {
            pending.add(msgId);
            active.add(msgId);
            bebBroadcast(msgId);
        }
    }

    public void receive(String msgId, int senderId) {
        acks.computeIfAbsent(msgId, k -> ConcurrentHashMap.newKeySet()).add(senderId);

        if (!pending.contains(msgId)) {
            pending.add(msgId);
            active.add(msgId);
            bebBroadcast(msgId);
        }

        checkDeliver(msgId);
    }

    private void bebBroadcast(String msgId) {
        for (Host host : hosts) {
             send(host, "URB " + myId + " " + msgId);
        }
    }

    private void send(Host host, String payload) {
        try {
            byte[] data = payload.getBytes();
            DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(host.getIp()),
                host.getPort()
            );
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkDeliver(String msgId) {
        if (delivered.contains(msgId)) return;

        Set<Integer> msgAcks = acks.get(msgId);
        if (msgAcks == null) return;

        if (msgAcks.containsAll(correct)) {
            if (delivered.add(msgId)) {
                String[] parts = msgId.split(":", 2);
                int originalSender = Integer.parseInt(parts[0]);
                String content = parts[1];
                
                if (deliverCallback != null) {
                    deliverCallback.accept(originalSender, content);
                }
            }
        }
    }
}
