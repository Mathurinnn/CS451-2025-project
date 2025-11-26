package cs451;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FifoBroadcast {
    private final UniformReliableBroadcast urb;
    private final LinkedBlockingQueue<String> logQueue;
    private final Map<Integer, Integer> nextSequence; 
    private final Map<Integer, Map<Integer, String>> pending; 
    private final AtomicInteger localSequence;

    public FifoBroadcast(int hostCount, LinkedBlockingQueue<String> logQueue) {
        this.logQueue = logQueue;
        this.nextSequence = new ConcurrentHashMap<>();
        this.pending = new ConcurrentHashMap<>();
        this.localSequence = new AtomicInteger(0);
        
        for (int i = 1; i <= hostCount; i++) {
            nextSequence.put(i, 1);
            pending.put(i, new ConcurrentHashMap<>());
        }
        this.urb = null;
    }

    public FifoBroadcast(UniformReliableBroadcast urb, int hostCount, LinkedBlockingQueue<String> logQueue) {
        this.urb = urb;
        this.logQueue = logQueue;
        this.nextSequence = new ConcurrentHashMap<>();
        this.pending = new ConcurrentHashMap<>();
        this.localSequence = new AtomicInteger(0);
        
        for (int i = 1; i <= hostCount; i++) {
            nextSequence.put(i, 1);
            pending.put(i, new ConcurrentHashMap<>());
        }
    }

    public void broadcast(String content) {
        int seq = localSequence.incrementAndGet();
        String fifoPayload = seq + "#" + content;
        urb.broadcast(fifoPayload);
    }

    public void deliver(int senderId, String payload) {
        String[] parts = payload.split("#", 2);
        int seq = Integer.parseInt(parts[0]);
        String content = parts[1];

        Map<Integer, String> senderPending = pending.get(senderId);
        if (senderPending == null) {
             return;
        }
        
        synchronized (senderPending) {
            senderPending.put(seq, content);
            checkDelivery(senderId);
        }
    }

    private void checkDelivery(int senderId) {
        Map<Integer, String> senderPending = pending.get(senderId);
        int next = nextSequence.get(senderId);

        while (senderPending.containsKey(next)) {
            String content = senderPending.remove(next);
            try {
                logQueue.put("d " + senderId + " " + content);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            next++;
        }
        nextSequence.put(senderId, next);
    }
}
