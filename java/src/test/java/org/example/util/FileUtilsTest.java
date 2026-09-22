package org.example.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @Test
    void testExtractUsername() {
        assertEquals("octocat", FileUtils.extractUsername("https://github.com/octocat/Hello-World.git"));
        assertEquals("octocat", FileUtils.extractUsername("https://github.com/octocat/Hello-World"));
        assertEquals("octocat", FileUtils.extractUsername("https://github.com/octocat/Hello-World/"));
        assertEquals("octocat", FileUtils.extractUsername("git@github.com:octocat/Hello-World.git"));
        assertEquals("marti", FileUtils.extractUsername("https://github.com/marti/raytracer-demo"));
    }

    @Test
    void testExtractRepoName() {
        assertEquals("Hello-World", FileUtils.extractRepoName("https://github.com/octocat/Hello-World.git"));
        assertEquals("Hello-World", FileUtils.extractRepoName("https://github.com/octocat/Hello-World"));
        assertEquals("Hello-World", FileUtils.extractRepoName("https://github.com/octocat/Hello-World/"));
        assertEquals("Hello-World", FileUtils.extractRepoName("git@github.com:octocat/Hello-World.git"));
        assertEquals("raytracer-demo", FileUtils.extractRepoName("https://github.com/marti/raytracer-demo"));
    }

    @Test
    void testFindMainClass(@TempDir Path tempDir) throws IOException {
        Path srcMainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);

        Path mainFile = srcMainJava.resolve("App.java");
        Files.writeString(mainFile, """
                package com.example;
                
                public class App {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        String mainClass = FileUtils.findMainClass(tempDir);
        assertEquals("com.example.App", mainClass);
    }

    @Test
    void testHasMaterialClass(@TempDir Path tempDir) throws IOException {
        assertFalse(FileUtils.hasMaterialClass(tempDir));

        Path subDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("Material.java"), "package com.example; public class Material {}");

        assertTrue(FileUtils.hasMaterialClass(tempDir));
    }

    @Test
    void testImageSnapshotAndNewImageDetection(@TempDir Path tempDir) throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("existing.png"), "fake png data");
        Set<Path> snapshot = FileUtils.snapshotImages(tempDir);

        assertEquals(1, snapshot.size());

        long startTime = System.currentTimeMillis();
        Thread.sleep(10); // ensure timestamp diff

        Files.writeString(tempDir.resolve("output.ppm"), "P3\n1 1\n255 0 0");

        String newImage = FileUtils.findNewImage(tempDir, snapshot, startTime);
        assertEquals("output.ppm", newImage);
    }
}
