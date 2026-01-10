package cs451;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class PerfectFailureDetector {

    private static final long DELTA = 1000; // milliseconds

    private final List<Host> hosts;
    private final int myId;
    private final DelayQueue<HeartbeatTimeout> timeouts;
    private final Set<Integer> suspected;
    private final ConcurrentHashMap<Integer, Long> lastHeartbeat;
    private final DatagramSocket socket;
    private volatile boolean running;
    private final List<java.util.function.Consumer<Integer>> crashListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<java.util.function.Consumer<Integer>> restoreListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public PerfectFailureDetector(List<Host> hosts, int myId, DatagramSocket socket) {
        this.hosts = hosts;
        this.myId = myId;
        this.socket = socket;
        this.timeouts = new DelayQueue<>();
        this.suspected = ConcurrentHashMap.newKeySet();
        this.lastHeartbeat = new ConcurrentHashMap<>();
        this.running = true;
    }

    public void addListener(java.util.function.Consumer<Integer> listener) {
        crashListeners.add(listener);
    }

    public void addRestoreListener(java.util.function.Consumer<Integer> listener) {
        restoreListeners.add(listener);
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Host host : hosts) {
            if (host.getId() != myId) {
                lastHeartbeat.put(host.getId(), now);
                timeouts.add(new HeartbeatTimeout(host.getId(), now + DELTA));
            }
        }

        new Thread(this::checkTimeouts).start();
        new Thread(this::sendHeartbeats).start();
    }

    public void stop() {
        running = false;
    }

    public boolean isSuspected(int hostId) {
        return suspected.contains(hostId);
    }

    public void registerHeartbeat(int hostId) {
        lastHeartbeat.put(hostId, System.currentTimeMillis());
        if (suspected.remove(hostId)) {
            for (java.util.function.Consumer<Integer> listener : restoreListeners) {
                listener.accept(hostId);
            }
        }
    }

    private void checkTimeouts() {
        while (running) {
            try {
                HeartbeatTimeout timeout = timeouts.poll(500, TimeUnit.MILLISECONDS);
                if (timeout != null) {
                    int hostId = timeout.hostId;
                    long last = lastHeartbeat.getOrDefault(hostId, 0L);
                    long now = System.currentTimeMillis();
                    
                    if (now - last > DELTA) {
                        if (suspected.add(hostId)) {
                            for (java.util.function.Consumer<Integer> listener : crashListeners) {
                                listener.accept(hostId);
                            }
                        }
                        timeouts.add(new HeartbeatTimeout(hostId, now + DELTA));
                    } else {
                        timeouts.add(new HeartbeatTimeout(hostId, last + DELTA));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendHeartbeats() {
        while (running) {
            try {
                Message m = Message.makeHeartbeat(myId);
                String msg = m.toNetworkString();
                byte[] data = msg.getBytes();
                for (Host host : hosts) {
                    if (host.getId() != myId) {
                        DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            InetAddress.getByName(host.getIp()),
                            host.getPort()
                        );
                        socket.send(packet);
                    }
                }
                Thread.sleep(DELTA / 2);
            } catch (IOException | InterruptedException e) {
                // Ignore
            }
        }
    }
    
    private static class HeartbeatTimeout implements Delayed {
        final int hostId;
        final long expiryTime;

        public HeartbeatTimeout(int hostId, long expiryTime) {
            this.hostId = hostId;
            this.expiryTime = expiryTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expiryTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expiryTime, ((HeartbeatTimeout) o).expiryTime);
        }
    }
}
