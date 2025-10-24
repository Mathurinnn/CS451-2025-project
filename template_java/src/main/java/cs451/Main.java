package cs451;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.DatagramPacket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;



public class Main {


    private static void handleSignal(DatagramSocket socket, FileWriter outputWriter, LinkedBlockingDeque<String> logQueue) {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        //write/flush output file if necessary

        try {
            List<String> remainingLogs = new ArrayList<>();
            logQueue.drainTo(remainingLogs);
            for (String log : remainingLogs) {
                outputWriter.write(log + "\n");
            }
        }
        catch (IOException e) {
            System.err.println("Error writing to output file during shutdown: " + e.getMessage());
        }
        try {
            outputWriter.close();
        } catch (IOException e) {
            System.err.println("Error closing output file: " + e.getMessage());
        }
        try {
            socket.close();
        } catch (Exception e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    private static void initSignalHandlers(DatagramSocket socket, FileWriter outputWriter, LinkedBlockingDeque<String> logQueue) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal(socket, outputWriter, logQueue);
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Main.java START ===");
        System.out.println("Arguments received: " + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("  Arg[" + i + "]: " + args[i]);
        }
        System.out.println("======================\n");
        
        Parser parser = new Parser(args);
        parser.parse();

        int port = parser.hosts().get(parser.myId() - 1).getPort();

        

        // example
        long pid = ProcessHandle.current().pid();
        System.out.println("My PID: " + pid + "\n");
        System.out.println("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to stop processing packets\n");

        System.out.println("My ID: " + parser.myId() + "\n");
        System.out.println("List of resolved hosts is:");
        System.out.println("==========================");
        for (Host host: parser.hosts()) {
            System.out.println(host.getId());
            System.out.println("Human-readable IP: " + host.getIp());
            System.out.println("Human-readable Port: " + host.getPort());
            System.out.println();
        }
        System.out.println();

        System.out.println("Path to output:");
        System.out.println("===============");
        System.out.println(parser.output() + "\n");

        System.out.println("Path to config:");
        System.out.println("===============");
        System.out.println(parser.configPath() + "\n");

        System.out.println("Doing some initialization\n");

        Order order = parser.order();

        try {  
        DatagramSocket socket = new DatagramSocket(port);

        LinkedBlockingDeque<String> logQueue = new LinkedBlockingDeque<>(200);

        FileWriter writer = new FileWriter(parser.output());
    
        initSignalHandlers(socket, writer, logQueue);

        if (order.nodeType == NodeType.SENDER) {
            
            LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<>();
            Map<String, Boolean> ackedMessages = new HashMap<>();
                
            ReceiverThread receiverThread = new ReceiverThread(socket, packetQueue);
            receiverThread.start();
            
            List<List<String>> batches = new ArrayList<>();
            int batchSize = 8;

            if (order.maxMessages > batchSize) {

                int fullBatches = order.maxMessages / batchSize;
                int remainingMessages = order.maxMessages % batchSize;

                for (int i = 0; i < fullBatches; i++) {
                    List<String> batch = new ArrayList<>();
                    for (int j = 0; j < batchSize; j++) {
                        batch.add("Message " + (i * batchSize + j + 1));
                    }
                    batches.add(batch);
                }

                if (remainingMessages > 0) {
                    List<String> lastBatch = new ArrayList<>();
                    for (int j = 0; j < remainingMessages; j++) {
                        lastBatch.add("Message " + (fullBatches * batchSize + j + 1));
                    }
                    batches.add(lastBatch);
                }
            } else {
                List<String> singleBatch = new ArrayList<>();
                for (int i = 0; i < order.maxMessages; i++) {
                    singleBatch.add("Message " + (i + 1));
                }
                batches.add(singleBatch);

            }

            for (int i = 1; i <= order.maxMessages; i++) {
                
            }




        }
        else if (order.nodeType == NodeType.RECEIVER) {

            LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<>();
            Set<String> deliveredMessages = new HashSet<>();
            ReceiverThread receiverThread = new ReceiverThread(socket, packetQueue);
            receiverThread.start();

            while (true) {
                DatagramPacket packet = packetQueue.take();

                byte[] data = ("ACK " + packet.getData().toString()).getBytes();

                DatagramPacket ackPacket = new DatagramPacket(
                    data,
                    data.length,
                    packet.getAddress(),
                    packet.getPort()
                );
                socket.send(ackPacket);


                if (deliveredMessages.contains(packet.getData().toString())) {
                    continue;
                }

                deliveredMessages.add(packet.getData().toString());
                String logEntry = "d " + packet.getData().toString();
                if (!logQueue.offer(logEntry)) {
                    List<String> logs = new ArrayList<>();
                    logQueue.drainTo(logs);
                    for (String log : logs) {
                        writer.write(log + "\n");
                    }
                    if (!logQueue.offer(logEntry)) {
                        System.err.println("Failed to log delivery: " + logEntry);
                    }
                }
            }
        }
        } catch (Exception e) {
            System.err.println("Error during execution: " + e.getMessage());
        }

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
