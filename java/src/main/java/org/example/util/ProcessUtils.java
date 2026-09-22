package org.example.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessUtils {

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static String getMvnCommand() {
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    public static String getGitCommand() {
        return isWindows() ? "git.exe" : "git";
    }

    public record ProcessResult(int exitCode, String output, String error) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static ProcessResult runProcess(List<String> command, File workingDir, long timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            Process process = pb.start();

            StringBuilder outBuilder = new StringBuilder();
            StringBuilder errBuilder = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(process.getInputStream(), outBuilder));
            Thread errThread = new Thread(() -> readStream(process.getErrorStream(), errBuilder));

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outThread.join(1000);
                errThread.join(1000);
                return new ProcessResult(-1, outBuilder.toString(), errBuilder.toString() + "\nProcess timed out after " + timeoutSeconds + " seconds.");
            }

            outThread.join(2000);
            errThread.join(2000);

            return new ProcessResult(process.exitValue(), outBuilder.toString(), errBuilder.toString());

        } catch (Exception e) {
            return new ProcessResult(-1, "", e.getMessage());
        }
    }

    private static void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception ignored) {
        }
    }
}
