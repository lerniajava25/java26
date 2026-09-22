package org.example.collector;

import org.example.model.ResultRecord;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;

public class ResultCollector implements Runnable {

    private final BlockingQueue<ResultRecord> resultQueue;
    private final Path csvPath;

    public ResultCollector(BlockingQueue<ResultRecord> resultQueue, Path csvPath) {
        this.resultQueue = resultQueue;
        this.csvPath = csvPath;
    }

    @Override
    public void run() {
        boolean fileExisted = Files.exists(csvPath) && getFileSizeSilently(csvPath) > 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvPath.toFile(), StandardCharsets.UTF_8, true))) {
            if (!fileExisted) {
                writer.write("github username,repourl,Pass/Fail compilation,Pass/Fail running,imagename found,Yes/NO if Material class exists");
                writer.newLine();
                writer.flush();
            }

            while (true) {
                ResultRecord record = resultQueue.take();
                if (record == ResultRecord.POISON_PILL) {
                    break;
                }

                String csvLine = record.toCsvLine();
                writer.write(csvLine);
                writer.newLine();
                writer.flush();

                System.out.printf("[COLLECTOR] Recorded result for username '%s' -> Compilation: %s | Running: %s | Image: %s | Material: %s%n",
                        record.username(), record.compilationStatus(), record.runningStatus(), record.imageNameFound(), record.materialExists());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("[COLLECTOR] Error writing CSV file: " + e.getMessage());
        }
    }

    private static long getFileSizeSilently(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0;
        }
    }
}
