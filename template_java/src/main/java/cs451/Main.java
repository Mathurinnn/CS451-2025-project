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
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    private static final java.util.concurrent.atomic.AtomicBoolean shutdownInitiated = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void handleSignal(DatagramSocket socket, FileWriter outputWriter, LinkedBlockingQueue<String> logQueue, List<Thread> threadsToInterrupt) {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            return;
        }

        System.out.println("Immediately stopping network packet processing.");

        try {
            for (Thread thread : threadsToInterrupt) {
                thread.interrupt();
            }
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

    private static void initSignalHandlers(DatagramSocket socket, FileWriter outputWriter, LinkedBlockingQueue<String> logQueue, List<Thread> threadsToInterrupt) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal(socket, outputWriter, logQueue, threadsToInterrupt);
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
      
        Parser parser = new Parser(args);
        parser.parse();
        
        FileWriter writer;

        String srcIp = parser.hosts().get(parser.myId() - 1).getIp();
        int srcPort = parser.hosts().get(parser.myId() - 1).getPort();

        Order order = parser.order();

        try {

            writer = new FileWriter(parser.output());
            
            DatagramSocket socket = new DatagramSocket(srcPort);
            //socket.setReceiveBufferSize(10 * 1024 * 1024); // 10MB buffer
            //socket.setSendBufferSize(10 * 1024 * 1024); // 10MB buffer

            LinkedBlockingQueue<String> logQueue = new LinkedBlockingQueue<>(Constants.QUEUE_CAPACITY);

            LoggingThread loggingThread = new LoggingThread(logQueue, writer);
            loggingThread.start();

            LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<>();
            ReceiverThread receiverThread = new ReceiverThread(socket, packetQueue);
            receiverThread.start();

            PerfectFailureDetector failureDetector = new PerfectFailureDetector(parser.hosts(), parser.myId(), socket);
            
            UniformReliableBroadcast urb = new UniformReliableBroadcast(parser.hosts(), parser.myId(), socket);
            failureDetector.addListener(urb::handleCrash);
            failureDetector.addRestoreListener(urb::handleRestore);
            failureDetector.start();

            initSignalHandlers(socket, writer, logQueue, List.of(loggingThread, receiverThread));

            java.util.concurrent.atomic.AtomicBoolean dispatchRunning = new java.util.concurrent.atomic.AtomicBoolean(true);

            Thread dispatchThread = new Thread(() -> {
                while (dispatchRunning.get()) {
                    try {
                        DatagramPacket packet = packetQueue.take();
                        String content = new String(packet.getData(), 0, packet.getLength());
                        
                        Message m = Message.parse(content);
                        if (m != null) {
                            if (m.type == Message.Type.HEARTBEAT) {
                                failureDetector.registerHeartbeat(m.senderId);
                            } else if (m.type == Message.Type.URB) {
                                urb.receive(m);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        if (dispatchRunning.get()) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            dispatchThread.start();

            if (order.type == OrderType.BROADCAST) {
                FifoBroadcast fifo = new FifoBroadcast(urb, parser.hosts().size(), logQueue);
                urb.setDeliverCallback(fifo::deliver);
                urb.start();

                for (int i = 1; i <= order.maxMessages; i++) {
                    String msg = String.valueOf(i);
                    fifo.broadcast(msg);
                    logQueue.put("b " + msg);
                }

                while (true) {
                    Thread.sleep(60 * 60 * 1000);
                }
            } else {
                java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                LatticeAgreement la = new LatticeAgreement(urb, parser.myId(), parser.hosts().size(), order.proposals, logQueue, done::countDown);
                urb.setDeliverCallback(la::onDeliver);
                urb.start();
                la.start();

                try {
                    done.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                while (true) {
                    Thread.sleep(60_000);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}