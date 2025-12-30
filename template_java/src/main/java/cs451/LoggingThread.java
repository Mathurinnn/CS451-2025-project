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
    private volatile boolean running = true;

    public LoggingThread(LinkedBlockingQueue<String> logQueue, FileWriter writer) {
        this.logQueue = logQueue;
        this.writer = writer;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // Periodically flush whatever is available to keep outputs up to date.
                String logEntry = logQueue.poll();
                if (logEntry != null) {
                    buffer.add(logEntry);
                    logQueue.drainTo(buffer);
                    processLogs();
                    buffer.clear();
                } else {
                    // Avoid busy spinning.
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error in LoggingThread: " + e.getMessage());
        }
        // Final flush on exit
        try {
            buffer.addAll(logQueue);
            processLogs();
            buffer.clear();
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        running = false;
        interrupt();
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
