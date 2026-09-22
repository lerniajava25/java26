package org.example.collector;

import org.example.model.ResultRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class ResultCollectorTest {

    @Test
    void testResultCollectorWritesCsv(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path csvFile = tempDir.resolve("test_results.csv");
        BlockingQueue<ResultRecord> queue = new LinkedBlockingQueue<>();

        ResultCollector collector = new ResultCollector(queue, csvFile);
        Thread thread = new Thread(collector);
        thread.start();

        queue.put(new ResultRecord("john", "https://github.com/john/repo1", "PASS", "PASS", "image.png", "YES"));
        queue.put(new ResultRecord("jane", "https://github.com/jane/repo2", "FAIL", "FAIL", "N/A", "NO"));
        queue.put(ResultRecord.POISON_PILL);

        thread.join(5000);

        assertTrue(Files.exists(csvFile));
        List<String> lines = Files.readAllLines(csvFile);

        assertEquals(3, lines.size());
        assertEquals("github username,repourl,Pass/Fail compilation,Pass/Fail running,imagename found,Yes/NO if Material class exists", lines.get(0));
        assertEquals("john,https://github.com/john/repo1,PASS,PASS,image.png,YES", lines.get(1));
        assertEquals("jane,https://github.com/jane/repo2,FAIL,FAIL,N/A,NO", lines.get(2));
    }
}
