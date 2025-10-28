package cs451;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class LoggingThread extends Thread {
    private final LinkedBlockingQueue<String> logQueue;
    private final FileWriter writer;
    private final List<String> buffer = new ArrayList<>();

    public LoggingThread(LinkedBlockingQueue<String> logQueue, FileWriter writer) {
        this.logQueue = logQueue;
        this.writer = writer;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (logQueue.remainingCapacity() == 0) {
                    // If the log queue is full, we can process the logs
                    logQueue.drainTo(buffer);
                    processLogs();
                    buffer.clear();
                }
            }
        } catch (Exception e) {
            System.err.println("Error in LoggingThread: " + e.getMessage());
        }
    }

    private void processLogs() {
        try {
            
            for (String logEntry : buffer) {
                writer.write(logEntry + "\n");
            }
            writer.flush();
        } catch (Exception e) {
            System.err.println("Error processing logs: " + e.getMessage());
        }
    }

}
