package cs451;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class UniformReliableBroadcast {

    public interface DeliverCallback {
        void deliver(int senderId, String content);
    }

    private final List<Host> hosts;
    private final int myId;
    private final DatagramSocket socket;
    private DeliverCallback deliverCallback;
    private volatile boolean running = true;
    
    private final Set<String> delivered = ConcurrentHashMap.newKeySet();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<Integer>> acks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> payloads = new ConcurrentHashMap<>();
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

    public void setDeliverCallback(DeliverCallback deliverCallback) {
        this.deliverCallback = deliverCallback;
    }

    public void start() {
        new Thread(this::retransmitLoop).start();
    }

    public void stop() {
        running = false;
        active.clear();
    }

    private void retransmitLoop() {
        while (running) {
            try {
                for (String uniqueId : active) {
                    Set<Integer> msgAcks = acks.get(uniqueId);
                    boolean allAcked = true;
                    
                    String payload = payloads.get(uniqueId);
                    if (payload == null) {
                        active.remove(uniqueId);
                        continue;
                    }
                    
                    String[] parts = uniqueId.split(":");
                    int originalSenderId = Integer.parseInt(parts[0]);
                    Message m = Message.makeUrb(myId, originalSenderId, payload);
                    String networkString = m.toNetworkString();

                    for (Host host : hosts) {
                        if (msgAcks == null || !msgAcks.contains(host.getId())) {
                            send(host, networkString);
                            allAcked = false;
                        }
                    }
                    if (allAcked) {
                        active.remove(uniqueId);
                    }
                }
                Thread.sleep(100); // Retransmit every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void broadcast(String content) {
        if (!running) return;
        Message m = Message.makeUrb(myId, myId, content);
        String uniqueId = m.getUniqueId();
        
        if (!pending.contains(uniqueId) && !delivered.contains(uniqueId)) {
            pending.add(uniqueId);
            active.add(uniqueId);
            payloads.put(uniqueId, content);
            bebBroadcast(m);
        }
    }

    public void receive(Message m) {
        if (!running) return;
        String uniqueId = m.getUniqueId();
        acks.computeIfAbsent(uniqueId, k -> ConcurrentHashMap.newKeySet()).add(m.senderId);

        if (!pending.contains(uniqueId) && !delivered.contains(uniqueId)) {
            pending.add(uniqueId);
            active.add(uniqueId);
            payloads.put(uniqueId, m.payload);
            
            Message relayMsg = Message.makeUrb(myId, m.originalSenderId, m.payload);
            bebBroadcast(relayMsg);
        }

        checkDeliver(uniqueId);
    }

    private void bebBroadcast(Message m) {
        String networkString = m.toNetworkString();
        for (Host host : hosts) {
             send(host, networkString);
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

    private void checkDeliver(String uniqueId) {
        if (delivered.contains(uniqueId)) return;

        Set<Integer> msgAcks = acks.get(uniqueId);
        if (msgAcks == null) return;

        if (msgAcks.containsAll(correct)) {
            if (delivered.add(uniqueId)) {
                acks.remove(uniqueId);
                pending.remove(uniqueId);
                String content = payloads.remove(uniqueId);
                
                if (content == null) return;

                String[] parts = uniqueId.split(":");
                int originalSender = Integer.parseInt(parts[0]);
                
                if (deliverCallback != null) {
                    deliverCallback.deliver(originalSender, content);
                }
            }
        }
    }
}
