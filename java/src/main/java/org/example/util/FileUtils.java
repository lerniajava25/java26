package org.example.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {

    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "(public\\s+|protected\\s+|private\\s+)?(static\\s+)?void\\s+main\\s*\\("
    );

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;",
            Pattern.MULTILINE
    );

    public static String extractUsername(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return "unknown";
        String url = repoUrl.trim();
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        int ghIndex = url.indexOf("github.com/");
        if (ghIndex != -1) {
            String path = url.substring(ghIndex + "github.com/".length());
            String[] parts = path.split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0];
            }
        }

        int sshIndex = url.indexOf("github.com:");
        if (sshIndex != -1) {
            String path = url.substring(sshIndex + "github.com:".length());
            String[] parts = path.split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0];
            }
        }

        return "unknown";
    }

    public static String extractRepoName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return "unknown_repo";
        String url = repoUrl.trim();
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        int ghIndex = url.indexOf("github.com/");
        if (ghIndex != -1) {
            String path = url.substring(ghIndex + "github.com/".length());
            String[] parts = path.split("/");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        }

        int sshIndex = url.indexOf("github.com:");
        if (sshIndex != -1) {
            String path = url.substring(sshIndex + "github.com:".length());
            String[] parts = path.split("/");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        }

        String[] parts = url.split("[/:]");
        if (parts.length >= 1 && !parts[parts.length - 1].isBlank()) {
            return parts[parts.length - 1];
        }

        return "unknown_repo";
    }

    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Files.setAttribute(file, "dos:readonly", false);
                } catch (Exception ignored) {
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path findPomDir(Path rootDir) throws IOException {
        if (!Files.exists(rootDir)) return null;
        if (Files.exists(rootDir.resolve("pom.xml"))) {
            return rootDir;
        }
        try (var stream = Files.walk(rootDir, 3)) {
            return stream
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static String findMainClass(Path rootDir) {
        if (!Files.exists(rootDir)) return null;

        List<MainCandidate> candidates = new ArrayList<>();

        try (var stream = Files.walk(rootDir)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();

            for (Path path : javaFiles) {
                try {
                    String content = Files.readString(path);
                    if (MAIN_METHOD_PATTERN.matcher(content).find()) {
                        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(content);
                        String pkg = pkgMatcher.find() ? pkgMatcher.group(1) : "";
                        String fileName = path.getFileName().toString();
                        String className = fileName.substring(0, fileName.lastIndexOf('.'));
                        String fqcn = pkg.isEmpty() ? className : pkg + "." + className;

                        boolean inSrcMainJava = path.toString().replace('\\', '/').contains("src/main/java");
                        boolean isStandardName = className.equalsIgnoreCase("Main") ||
                                className.equalsIgnoreCase("App") ||
                                className.equalsIgnoreCase("Application") ||
                                className.toLowerCase().contains("raytracer");

                        int score = 0;
                        if (inSrcMainJava) score += 10;
                        if (isStandardName) score += 5;

                        candidates.add(new MainCandidate(fqcn, score));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        if (candidates.isEmpty()) return null;

        candidates.sort((c1, c2) -> Integer.compare(c2.score(), c1.score()));
        return candidates.get(0).fqcn();
    }

    private record MainCandidate(String fqcn, int score) {
    }

    public static Set<Path> snapshotImages(Path rootDir) {
        Set<Path> set = new HashSet<>();
        if (!Files.exists(rootDir)) return set;
        try (var stream = Files.walk(rootDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(FileUtils::isImageFile)
                    .forEach(set::add);
        } catch (Exception ignored) {
        }
        return set;
    }

    public static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".ppm") || name.endsWith(".bmp") || name.endsWith(".png") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tga");
    }

    public static String findNewImage(Path rootDir, Set<Path> initialImages, long startTime) {
        if (!Files.exists(rootDir)) return "N/A";

        Path newlyCreated = null;
        long newestTime = 0;

        try (var stream = Files.walk(rootDir)) {
            List<Path> candidateImages = stream.filter(Files::isRegularFile)
                    .filter(FileUtils::isImageFile)
                    .toList();

            for (Path p : candidateImages) {
                long modTime = Files.getLastModifiedTime(p).toMillis();
                if (!initialImages.contains(p) || modTime >= startTime - 1000) {
                    if (modTime > newestTime) {
                        newestTime = modTime;
                        newlyCreated = p;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (newlyCreated != null) {
            return newlyCreated.getFileName().toString();
        }
        return "N/A";
    }

    public static boolean hasMaterialClass(Path rootDir) {
        if (!Files.exists(rootDir)) return false;
        try (var stream = Files.walk(rootDir)) {
            return stream.filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().equalsIgnoreCase("Material.java"));
        } catch (Exception e) {
            return false;
        }
    }

    public static String buildClasspath(Path mavenDir) {
        Path classesDir = mavenDir.resolve("target/classes").toAbsolutePath();
        Path depDir = mavenDir.resolve("target/dependency").toAbsolutePath();
        String sep = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";

        if (Files.exists(depDir)) {
            return classesDir + sep + depDir.resolve("*");
        }
        return classesDir.toString();
    }
}
