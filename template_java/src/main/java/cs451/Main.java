package cs451;

import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.util.List;
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

        String srcIp = parser.hosts().get(parser.myId() - 1).getIp();
        int srcPort = parser.hosts().get(parser.myId() - 1).getPort();

        

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
            DatagramSocket socket = new DatagramSocket(srcPort);

            LinkedBlockingDeque<String> logQueue = new LinkedBlockingDeque<>(200);

            FileWriter writer = new FileWriter(parser.output());
        
            initSignalHandlers(socket, writer, logQueue);


            if (order.nodeType == NodeType.SENDER) {
                
                LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<>();
                HashMap<String, Boolean> ackedPackets = new HashMap<>();
                    
                ReceiverThread receiverThread = new ReceiverThread(socket, packetQueue);
                receiverThread.start();
                
                int batchSize = 8;
                int fullBatches;
                int remainingMessages;

                if (order.maxMessages > batchSize) {

                    fullBatches = order.maxMessages / batchSize;
                    remainingMessages = order.maxMessages % batchSize;
                
                } else {
                    fullBatches = 0;
                    remainingMessages = order.maxMessages;
                }
            

                for (int i = 0; i < order.maxMessages; i++) {
                    String logEntry = "b " + String.valueOf(i + 1);
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


                List<List<Message>> batches = new ArrayList<>();

                for (int i = 0; i < fullBatches; i++) {
                    List<Message> messages = new ArrayList<>();
                    for (int j = 0; j < batchSize; j++) {
                        String messageContent = String.valueOf(i * batchSize + j + 1);
                        messages.add(new Message(messageContent));
                    }
                    batches.add(messages);
                }
                if (remainingMessages > 0) {
                    List<Message> messages = new ArrayList<>();
                    for (int j = 0; j < remainingMessages; j++) {
                        String messageContent = String.valueOf(fullBatches * batchSize + j + 1);
                        messages.add(new Message(messageContent));
                    }
                    batches.add(messages);
                }


                while (true) {

                    for (Host host : parser.hosts()) {

                        if (host.getId() != order.destId) {
                            continue;
                        }

                
                        for (List<Message> batch : batches) {
                        

                            if (ackedPackets.containsKey(String.valueOf(host.getIp()) + ":" + String.valueOf(host.getPort()) + ":" + String.valueOf(batch.get(0).payload)) &&
                                ackedPackets.get(String.valueOf(host.getIp()) + ":" + String.valueOf(host.getPort()) + ":" + String.valueOf(batch.get(0).payload)) == true) {
                                continue;
                            } 

                            System.out.println("Sending");

                            Packet packet = new Packet(
                                srcIp,
                                srcPort,
                                host.getIp(),
                                host.getPort(),
                                Integer.parseInt(batch.get(0).payload),
                                batch
                            );


                            ackedPackets.put(String.valueOf(packet.destIp) + ":" + String.valueOf(packet.destPort) + ":" + String.valueOf(packet.packetId), false);

                            byte[] data = packet.toString().getBytes();

                            DatagramPacket datagramPacket = new DatagramPacket(
                                data,
                                data.length,
                                InetAddress.getByName(host.getIp()),
                                host.getPort()
                            );

                            System.out.println(packet.toString());
                            socket.send(datagramPacket);

                            
                            
                        }
                    }
                    while (!packetQueue.isEmpty()) {
                        DatagramPacket receivedPacket = packetQueue.take();
                        String receivedData = new String(
                            receivedPacket.getData(),
                            0,
                            receivedPacket.getLength()
                        );
                        Packet receivedAck = Packet.fromString(receivedData);
                        List<Message> messages = receivedAck.messages;
                        if (messages.size() > 0 && messages.get(0).payload.startsWith("ACK")) {
                            ackedPackets.replace(String.valueOf(receivedAck.srcIp) + ":" + String.valueOf(receivedAck.srcPort) + ":" + String.valueOf(receivedAck.packetId), true);
                        }
                    }
                }
            }
        

            else if (order.nodeType == NodeType.RECEIVER) {

                LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<>();
                Set<String> deliveredMessages = new HashSet<>();
                ReceiverThread receiverThread = new ReceiverThread(socket, packetQueue);
                receiverThread.start();

                while (true) {
                    DatagramPacket udpPacket = packetQueue.take();
                    Packet packet = Packet.fromString(
                        new String(udpPacket.getData(), 0, udpPacket.getLength())
                    );

                    System.out.println(packet.toString());

                    List<Message> messages = new ArrayList<>();
                    for (Message msg : packet.messages) {
                        messages.add(new Message("ACK " + msg.payload));
                    }

                    Packet packetToSend = new Packet(
                        srcIp,
                        srcPort,
                        packet.srcIp,
                        packet.srcPort,
                        packet.packetId,
                        messages
                    );
        

                    byte[] data = packetToSend.toString().getBytes();

                    DatagramPacket ackPacket = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName(packet.srcIp),
                        packet.srcPort
                    );
                    socket.send(ackPacket);


                
                    for (Message message : packet.messages) {

                        if (deliveredMessages.contains(packet.srcIp + ":" + packet.srcPort + ":" + message.payload)) {
                            continue;
                        }

                        int processIndex = -1;
                        for (int i = 0; i < parser.hosts().size(); i++) {
                            if (parser.hosts().get(i).getIp().equals(packet.srcIp) && parser.hosts().get(i).getPort() == packet.srcPort) {
                                processIndex = i;
                                break;
                            }
                        }

                        System.err.println("Delivered message " + message.payload + " from process " + (processIndex + 1));

                        deliveredMessages.add(packet.srcIp + ":" + packet.srcPort + ":" + message.payload);
                        String logEntry = "d " + String.valueOf(processIndex + 1) + " " + message.payload;
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
            }
        }

        catch (Exception e) {
            // add full traceback print
            e.printStackTrace();
        }

            // After a process finishes broadcasting,
            // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
