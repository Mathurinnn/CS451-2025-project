package cs451;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.LinkedBlockingQueue;

public class ReceiverThread extends Thread {
    
    private final DatagramSocket socket;
    private final LinkedBlockingQueue<DatagramPacket> packetQueue;

    public ReceiverThread(DatagramSocket socket, LinkedBlockingQueue<DatagramPacket> packetQueue) {
        this.socket = socket;
        this.packetQueue = packetQueue;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
            try {
                byte[] buffer = new byte[65535];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                packetQueue.put(packet);
            } catch (Exception e) {
                if (socket.isClosed() || Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }
}
