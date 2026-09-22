package org.example.worker;

import org.example.model.ResultRecord;
import org.example.util.ProcessUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepoProcessorTest {

    @Test
    void testEndToEndRepoProcessor(@TempDir Path tempDir) throws IOException {
        Path mockGitRepo = tempDir.resolve("testuser-raytracer");
        Files.createDirectories(mockGitRepo);

        // Git init
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "init"), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "config", "user.name", "TestUser"), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "config", "user.email", "test@example.com"), mockGitRepo.toFile(), 10);

        // pom.xml
        Files.writeString(mockGitRepo.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.test</groupId>
                    <artifactId>test-repo</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <maven.compiler.release>26</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                </project>
                """);

        // Java source files
        Path srcDir = mockGitRepo.resolve("src/main/java/org/test");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("App.java"), """
                package org.test;
                import java.io.File;
                import java.io.FileWriter;
                
                public class App {
                    public static void main(String[] args) throws Exception {
                        File f = new File("output.ppm");
                        try (FileWriter writer = new FileWriter(f)) {
                            writer.write("P3\\n1 1\\n255 0 0");
                        }
                        System.out.println("Generated output.ppm successfully");
                    }
                }
                """);

        Files.writeString(srcDir.resolve("Material.java"), """
                package org.test;
                public class Material {
                }
                """);

        // Git commit
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "add", "."), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "commit", "-m", "Initial commit"), mockGitRepo.toFile(), 10);

        // Prepare target download directory
        Path downloads = tempDir.resolve("downloads");
        RepoProcessor processor = new RepoProcessor(downloads);

        String repoUrl = mockGitRepo.toUri().toString(); // file:///...
        ResultRecord result = processor.process(repoUrl);

        assertEquals("PASS", result.compilationStatus());
        assertEquals("PASS", result.runningStatus());
        assertEquals("output.ppm", result.imageNameFound());
        assertEquals("YES", result.materialExists());
        assertTrue(Files.exists(downloads.resolve("testuser-raytracer")), "Cloned folder should be named after the repository name");
    }

    @Test
    void testPackagePrivateMainMethod(@TempDir Path tempDir) throws IOException {
        Path mockGitRepo = tempDir.resolve("package-private-main-repo");
        Files.createDirectories(mockGitRepo);

        // Git init
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "init"), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "config", "user.name", "TestUser"), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "config", "user.email", "test@example.com"), mockGitRepo.toFile(), 10);

        Files.writeString(mockGitRepo.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.test</groupId>
                    <artifactId>pkg-private-test</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <maven.compiler.release>26</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                </project>
                """);

        Path srcDir = mockGitRepo.resolve("src/main/java/org/example");
        Files.createDirectories(srcDir);

        // Package-private static void main (Java 21+ style)
        Files.writeString(srcDir.resolve("Main.java"), """
                package org.example;
                import java.io.File;
                import java.io.FileWriter;
                
                public class Main {
                    static void main(String[] args) throws Exception {
                        File f = new File("raytracer.png");
                        try (FileWriter writer = new FileWriter(f)) {
                            writer.write("fake png data");
                        }
                    }
                }
                """);

        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "add", "."), mockGitRepo.toFile(), 10);
        ProcessUtils.runProcess(List.of(ProcessUtils.getGitCommand(), "commit", "-m", "Initial commit"), mockGitRepo.toFile(), 10);

        Path downloads = tempDir.resolve("downloads");
        RepoProcessor processor = new RepoProcessor(downloads);

        ResultRecord result = processor.process(mockGitRepo.toUri().toString());

        assertEquals("PASS", result.compilationStatus());
        assertEquals("PASS", result.runningStatus());
        assertEquals("raytracer.png", result.imageNameFound());
    }
}
