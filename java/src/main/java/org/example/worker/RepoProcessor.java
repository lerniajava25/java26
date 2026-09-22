package org.example.worker;

import org.example.model.ResultRecord;
import org.example.util.FileUtils;
import org.example.util.ProcessUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RepoProcessor {

    private final Path baseDownloadDir;

    public RepoProcessor(Path baseDownloadDir) {
        this.baseDownloadDir = baseDownloadDir;
    }

    public ResultRecord process(String repoUrl) {
        String username = FileUtils.extractUsername(repoUrl);
        String repoName = FileUtils.extractRepoName(repoUrl);
        Path targetDir = baseDownloadDir.resolve(repoName);

        System.out.printf("[%s] Target directory (repo root): %s%n", repoName, targetDir.toAbsolutePath());

        try {
            // Clean up existing directory if present to allow fresh git clone
            if (Files.exists(targetDir)) {
                FileUtils.deleteDirectoryRecursively(targetDir);
            }
            Files.createDirectories(baseDownloadDir);

            // 1. Git Clone
            System.out.printf("[%s] Cloning %s...%n", repoName, repoUrl);
            List<String> cloneCmd = List.of(ProcessUtils.getGitCommand(), "clone", repoUrl, targetDir.toAbsolutePath().toString());
            ProcessUtils.ProcessResult cloneResult = ProcessUtils.runProcess(cloneCmd, baseDownloadDir.toFile(), 120);

            if (!cloneResult.isSuccess() || !Files.exists(targetDir)) {
                System.err.printf("[%s] Git clone failed: %s%n", repoName, cloneResult.error());
                return new ResultRecord(username, repoUrl, "FAIL", "FAIL", "N/A", "NO");
            }

            // 2. Locate pom.xml directory
            Path mavenDir = FileUtils.findPomDir(targetDir);
            if (mavenDir == null) {
                System.err.printf("[%s] No pom.xml found in project.%n", repoName);
                boolean materialExists = FileUtils.hasMaterialClass(targetDir);
                return new ResultRecord(username, repoUrl, "FAIL", "FAIL", "N/A", materialExists ? "YES" : "NO");
            }

            // 3. Maven Compile with process working directory set to root of downloaded repo (targetDir)
            System.out.printf("[%s] Running mvn compile with working directory %s...%n", repoName, targetDir.toAbsolutePath());
            List<String> compileCmd = new ArrayList<>();
            compileCmd.add(ProcessUtils.getMvnCommand());
            if (!mavenDir.equals(targetDir)) {
                compileCmd.add("-f");
                compileCmd.add(mavenDir.resolve("pom.xml").toAbsolutePath().toString());
            }
            compileCmd.add("compile");

            ProcessUtils.ProcessResult compileResult = ProcessUtils.runProcess(compileCmd, targetDir.toFile(), 180);

            if (!compileResult.isSuccess()) {
                System.err.printf("[%s] Compilation failed.%n", repoName);
                boolean materialExists = FileUtils.hasMaterialClass(targetDir);
                return new ResultRecord(username, repoUrl, "FAIL", "FAIL", "N/A", materialExists ? "YES" : "NO");
            }

            System.out.printf("[%s] Compilation PASSED.%n", repoName);

            // 4. Find Main Class
            String mainClass = FileUtils.findMainClass(targetDir);
            if (mainClass == null) {
                System.err.printf("[%s] No main method class found.%n", repoName);
                boolean materialExists = FileUtils.hasMaterialClass(targetDir);
                return new ResultRecord(username, repoUrl, "PASS", "FAIL", "N/A", materialExists ? "YES" : "NO");
            }

            System.out.printf("[%s] Main class found: %s%n", repoName, mainClass);

            // 5. Snapshot images before running
            Set<Path> initialImages = FileUtils.snapshotImages(targetDir);
            long startTime = System.currentTimeMillis();

            // 6. Run Main Class
            // Primary method: Run via standard 'java -cp <classpath> <mainClass>' in targetDir to support all Java 21+ main methods (including package-private / instance main)
            System.out.printf("[%s] Executing main class %s via java launcher (working dir: %s)...%n", repoName, mainClass, targetDir.toAbsolutePath());
            String classpath = FileUtils.buildClasspath(mavenDir);
            List<String> javaCmd = List.of("java", "-cp", classpath, mainClass);
            ProcessUtils.ProcessResult execResult = ProcessUtils.runProcess(javaCmd, targetDir.toFile(), 60);

            // Fallback: If direct java invocation fails and no image created, attempt exec:java
            if (!execResult.isSuccess()) {
                String imageCheck = FileUtils.findNewImage(targetDir, initialImages, startTime);
                if (imageCheck.equals("N/A")) {
                    System.out.printf("[%s] Direct java execution exited with %d. Attempting exec-maven-plugin fallback...%n", repoName, execResult.exitCode());
                    List<String> execCmd = new ArrayList<>();
                    execCmd.add(ProcessUtils.getMvnCommand());
                    if (!mavenDir.equals(targetDir)) {
                        execCmd.add("-f");
                        execCmd.add(mavenDir.resolve("pom.xml").toAbsolutePath().toString());
                    }
                    execCmd.add("org.codehaus.mojo:exec-maven-plugin:3.5.0:java");
                    execCmd.add("-Dexec.mainClass=" + mainClass);
                    execCmd.add("-Dexec.workingdir=" + targetDir.toAbsolutePath());
                    execResult = ProcessUtils.runProcess(execCmd, targetDir.toFile(), 60);
                }
            }

            String runStatus = execResult.isSuccess() ? "PASS" : "FAIL";

            // 7. Find created image file
            String newImage = FileUtils.findNewImage(targetDir, initialImages, startTime);
            if (!newImage.equals("N/A")) {
                System.out.printf("[%s] Image file detected: %s%n", repoName, newImage);
                runStatus = "PASS";
            } else {
                System.out.printf("[%s] Execution finished with status: %s (No new image found)%n", repoName, runStatus);
            }

            // 8. Check for Material class
            String materialExists = FileUtils.hasMaterialClass(targetDir) ? "YES" : "NO";

            return new ResultRecord(username, repoUrl, "PASS", runStatus, newImage, materialExists);

        } catch (Exception e) {
            System.err.printf("[%s] Error processing repo: %s%n", repoName, e.getMessage());
            return new ResultRecord(username, repoUrl, "FAIL", "FAIL", "N/A", "NO");
        }
    }
}
