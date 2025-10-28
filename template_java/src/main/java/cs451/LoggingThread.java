package cs451;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class LoggingThread extends Thread {
    private final LinkedBlockingQueue<String> logQueue;
    private final FileWriter writer;
    private final List<String> buffer = new ArrayList<>(Constants.QUEUE_CAPACITY);

    public LoggingThread(LinkedBlockingQueue<String> logQueue, FileWriter writer) {
        this.logQueue = logQueue;
        this.writer = writer;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (logQueue.remainingCapacity() == 0) 
                {
                    String logEntry = logQueue.take();
                    buffer.add(logEntry);
                    logQueue.drainTo(buffer);
                    processLogs();
                    buffer.clear();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error in LoggingThread: " + e.getMessage());
        }
    }

    private void processLogs() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String logEntry : buffer) {
            sb.append(logEntry).append('\n');
        }
        writer.write(sb.toString());
        writer.flush();
    }

}
