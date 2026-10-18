package org.example.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.mcp.client.McpClient;
import org.example.mcp.server.tools.McpToolInfo;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

public class Chat {
    public static final String HOST = "https://openrouter.ai";
    public static final String MODEL = "google/gemini-3.8-flash";
    public static int totalPromptTokens = 0;
    public static int totalCompletionTokens = 0;

    // Active tools and MCP server mappings
    private static final List<Tool> allTools = new ArrayList<>();
    private static final List<McpClient> mcpClients = new ArrayList<>();
    private static final Map<String, McpClient> mcpToolMap = new HashMap<>();

    /**
     * Registers an MCP server by URL, fetches its exposed tools, and includes them in the agent's tools.
     *
     * @param url MCP server HTTP endpoint (e.g. "http://127.0.0.1:3000/mcp")
     * @return list of newly registered tools
     */
    public static List<Tool> addMcpServer(String url) {
        return addMcpServer(new McpClient(url));
    }

    /**
     * Registers an McpClient, fetches its exposed tools, and includes them in the agent's tools.
     *
     * @param client McpClient instance
     * @return list of newly registered tools
     */
    public static List<Tool> addMcpServer(McpClient client) {
        List<Tool> added = new ArrayList<>();
        try {
            List<McpToolInfo> toolInfos = client.listTools();
            mcpClients.add(client);
            for (McpToolInfo info : toolInfos) {
                Tool tool = new Tool("function", new ToolFunctionSpec(
                        info.name(),
                        info.effectiveDescription(),
                        info.inputSchema() != null && !info.inputSchema().isEmpty() ? info.inputSchema() : Map.of("type", "object")
                ));
                allTools.add(tool);
                mcpToolMap.put(info.name(), client);
                added.add(tool);
            }
            System.out.printf("[MCP] Ansluten till %s: laddade %d verktyg %s%n",
                    client.getEndpoint(),
                    toolInfos.size(),
                    toolInfos.stream().map(McpToolInfo::name).toList());
        } catch (Exception e) {
            System.err.printf("[MCP] Kunde inte läsa verktyg från %s: %s%n", client.getEndpoint(), e.getMessage());
        }
        return added;
    }

    private static void initLocalTools() {
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "get_current_datetime",
                        "Returns the current date and time for Stockholm, Sweden.",
                        Map.of("type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                )
        ));
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "run_powershell",
                        "Executes a PowerShell command on the local Windows system. Requires user confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "command", Map.of("type", "string", "description", "The PowerShell command to execute")
                                ),
                                "required", List.of("command")
                        )
                )
        ));
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "list_directory",
                        "Lists the contents of a directory starting from the current working directory.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "The directory path to list (default is current directory '.')")
                                ),
                                "required", List.of()
                        )
                )
        ));
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "read_file",
                        "Reads and returns the content of a file. Requires user confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "The file path to read")
                                ),
                                "required", List.of("path")
                        )
                )
        ));
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "write_file",
                        "Creates a new file or overwrites an existing file with the provided content. Requires user confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "The file path to write to"),
                                        "content", Map.of("type", "string", "description", "The full content to write into the file")
                                ),
                                "required", List.of("path", "content")
                        )
                )
        ));
        allTools.add(new Tool("function",
                new ToolFunctionSpec(
                        "patch_file",
                        "Updates a specific section of a file by finding a unique search block and replacing it with a replacement block. Requires user confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "The file path to update"),
                                        "search", Map.of("type", "string", "description", "The exact lines of code to find in the file"),
                                        "replace", Map.of("type", "string", "description", "The new lines of code to put in its place")
                                ),
                                "required", List.of("path", "search", "replace")
                        )
                )
        ));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final String API_KEY = System.getenv("OPENROUTER_API_KEY");
        final List<Message> history = new ArrayList<>();

        HttpClient httpClient = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        // Konfigurera mappen direkt när den skapas för att undvika att skicka null-fält till API:et
        ObjectMapper mapper = new ObjectMapper();

        // Återställd systeminstruktion
        history.add(new Message("system",
                """
                        Do not output chain-of-thought reasoning. Only provide the final answer.
                        Your answers should be brief and to the point. Use only max 2 sentences.
                        You have access to local tools for:
                        - Getting current date and time.
                        - Listing directories and reading files (requires user confirmation).
                        - Creating or overwriting files (requires user confirmation).
                        - Running PowerShell commands (requires user confirmation).
                        - Updating specific parts of files using search and replace (requires user confirmation).
                        - Browsing the web / fetching URLs using HTTP GET requests (requires user confirmation).
                        - Asking questions to the user (`ask_question`) to get more information about the task or let the user make choices.
                        When executing a multi-step task and you need clarification or choices from the user before proceeding, use the ask_question tool instead of stopping.
                        Additional tools may also be provided dynamically by connected MCP servers.
                        Use these tools proactively to solve programming and file-management tasks when requested.
                        """, ""));

        // Initiera lokala verktyg
        initLocalTools();

        // Anslut till MCP-server: via miljövariabler eller standard lokal URL
        String mcpServersEnv = System.getenv("MCP_SERVERS");
        if (mcpServersEnv != null && !mcpServersEnv.isBlank()) {
            for (String serverUrl : mcpServersEnv.split(",")) {
                if (!serverUrl.isBlank()) {
                    addMcpServer(serverUrl.trim());
                }
            }
        } else {
            String defaultServer = System.getenv().getOrDefault("MCP_SERVER_URL", "http://127.0.0.1:3000/mcp");
            addMcpServer(defaultServer);
        }

        while (true) {
            String userInput = IO.readln("You: ");
            if (userInput.equalsIgnoreCase("quit")) {
                System.out.println("\n--- Token-sammanfattning för sessionen ---");
                System.out.println("Totalt input (prompt): " + totalPromptTokens);
                System.out.println("Totalt output (completion): " + totalCompletionTokens);
                System.out.println("Total förbrukning: " + (totalPromptTokens + totalCompletionTokens));
                break;
            }

            if (userInput.startsWith("/mcp add ")) {
                String serverUrl = userInput.substring("/mcp add ".length()).trim();
                addMcpServer(serverUrl);
                continue;
            }

            if (userInput.equalsIgnoreCase("/mcp list")) {
                System.out.println("Anslutna MCP-servrar (" + mcpClients.size() + "):");
                for (McpClient c : mcpClients) {
                    System.out.println("  - " + c.getEndpoint());
                }
                System.out.println("Aktiva verktyg (" + allTools.size() + "): "
                        + allTools.stream().map(t -> t.function().name()).toList());
                continue;
            }

            history.add(new Message("user", userInput, ""));
            while (true) {
                OpenRouterResponse or = sendToModel(httpClient, mapper, history, allTools, API_KEY);
                // Håll koll på tokens om usage finns med i svaret
                if (or.usage() != null) {
                    totalPromptTokens += or.usage().prompt_tokens();
                    totalCompletionTokens += or.usage().completion_tokens();
                    System.out.printf("[Tokens] Denna vända: %d in, %d ut | Totalt hittills: %d tokens%n",
                            or.usage().prompt_tokens(),
                            or.usage().completion_tokens(),
                            (totalPromptTokens + totalCompletionTokens));
                }

                var choice = or.choices().get(0);
                MessageResponse msg = choice.message();
                var finish = choice.finish_reason();

                if ("stop".equals(finish)) {
                    String assistantReply = msg.content();
                    System.out.println("Assistant: " + assistantReply);
                    history.add(new Message("assistant", assistantReply, ""));
                    break;
                }
                if ("tool_calls".equals(finish)) {
                    System.out.println("Tool call requested: " + msg.tool_calls());

                    history.add(new Message("assistant", msg.content(), null, msg.tool_calls()));

                    for (ToolCall toolCall : msg.tool_calls()) {
                        String toolOutputJson = runTool(toolCall, mapper);
                        history.add(new Message("tool", toolOutputJson, toolCall.id()));
                    }
                    continue;
                }
                throw new IllegalStateException("Unexpected finish_reason: " + finish);
            }
        }
    }

    private static String runTool(ToolCall tc, ObjectMapper mapper) {
        String name = tc.function().name();
        String argumentsJson = tc.function().arguments();

        try {
            Map<String, Object> args = mapper.readValue(argumentsJson, Map.class);

            // Kontrollera om verktyget tillhandahålls av en ansluten MCP-server
            McpClient mcpClient = mcpToolMap.get(name);
            if (mcpClient != null) {
                System.out.println("\n[MCP] Kör verktyg '" + name + "' på " + mcpClient.getEndpoint() + " med argument: " + argumentsJson);
                return mcpClient.callToolAsString(name, args);
            }

            if ("get_current_datetime".equals(name)) {
                DateTimeResult result = getCurrentDateTime();
                return mapper.writeValueAsString(result);
            } else if ("run_powershell".equals(name)) {
                String command = (String) args.get("command");
                System.out.println("\n[SÄKERHET] Agenten vill köra ett PowerShell-kommando:");
                System.out.println("  > " + command);
                String answer = IO.readln("Tillåt körning? (ja/nej): ");

                if (!answer.equalsIgnoreCase("ja")) {
                    return "Error: User denied execution of the command.";
                }

                return executeProcess(List.of("powershell.exe", "-Command", command));
            } else if ("list_directory".equals(name)) {
                String pathStr = (String) args.getOrDefault("path", ".");
                java.nio.file.Path path = java.nio.file.Path.of(pathStr);

                if (!java.nio.file.Files.exists(path)) {
                    return "Error: Path does not exist: " + pathStr;
                }

                StringBuilder sb = new StringBuilder();
                try (var stream = java.nio.file.Files.list(path)) {
                    stream.forEach(p -> sb.append(p.getFileName())
                            .append(java.nio.file.Files.isDirectory(p) ? "/ (dir)\n" : "\n"));
                }
                return sb.toString();
            } else if ("read_file".equals(name)) {
                String pathStr = (String) args.get("path");
                java.nio.file.Path path = java.nio.file.Path.of(pathStr);

                if (!java.nio.file.Files.exists(path)) {
                    return "Error: File does not exist: " + pathStr;
                }

                // Hämta filstorleken i bytes
                long fileSize = java.nio.file.Files.size(path);
                String sizeFormatted = fileSize > 1024 ? (fileSize / 1024) + " KB" : fileSize + " bytes";

                System.out.println("\n[SÄKERHET] Agenten vill läsa filen:");
                System.out.println("  > " + pathStr + " (Storlek: " + sizeFormatted + ")");
                String answer = IO.readln("Tillåt läsning? (ja/nej): ");

                if (!answer.equalsIgnoreCase("ja")) {
                    return "Error: User denied reading the file.";
                }

                return java.nio.file.Files.readString(path);
            } else if ("write_file".equals(name)) {
                String pathStr = (String) args.get("path");
                String content = (String) args.get("content");

                java.nio.file.Path path = java.nio.file.Path.of(pathStr);
                boolean fileExists = java.nio.file.Files.exists(path);

                System.out.println("\n[SÄKERHET] Agenten vill " + (fileExists ? "skriva över/ändra" : "skapa") + " filen:");
                System.out.println("  > " + pathStr);
                System.out.println("--- Innehåll som ska skrivas ---");
                // Skriv gärna ut en förhandsvisning eller hela innehållet beroende på storlek
                System.out.println(content.length() > 500 ? content.substring(0, 500) + "\n...[trunkerat]..." : content);
                System.out.println("--------------------------------");

                String answer = IO.readln("Tillåt skrivning? (ja/nej): ");
                if (!answer.equalsIgnoreCase("ja")) {
                    return "Error: User denied writing to the file.";
                }

                // Skapa eventuella överliggande kataloger om de inte finns (t.ex. src/main/java/...)
                if (path.getParent() != null && !java.nio.file.Files.exists(path.getParent())) {
                    java.nio.file.Files.createDirectories(path.getParent());
                }

                java.nio.file.Files.writeString(path, content);
                return "Success: File successfully written to " + pathStr;
            } else if ("patch_file".equals(name)) {
                String pathStr = (String) args.get("path");
                String search = (String) args.get("search");
                String replace = (String) args.get("replace");

                java.nio.file.Path path = java.nio.file.Path.of(pathStr);
                if (!java.nio.file.Files.exists(path)) {
                    return "Error: File does not exist: " + pathStr;
                }

                String content = java.nio.file.Files.readString(path);
                // Räkna antal förekomster
                int occurrences = 0;
                int lastIndex = 0;
                while ((lastIndex = content.indexOf(search, lastIndex)) != -1) {
                    occurrences++;
                    lastIndex += search.length();
                }

                if (occurrences == 0) {
                    return "Error: The 'search' block was not found in the file. Make sure you match the exact content including whitespace.";
                } else if (occurrences > 1) {
                    return "Error: Multiple matches found (" + occurrences + "). Please provide more surrounding context to make the search block unique.";
                }

                System.out.println("\n[SÄKERHET] Agenten vill uppdatera filen (Patch):");
                System.out.println("  > " + pathStr);
                System.out.println("--- HITTAS/BYTS UT ---");
                System.out.println(search);
                System.out.println("--- NYTT INNEHÅLL ---");
                System.out.println(replace);
                System.out.println("----------------------");

                String answer = IO.readln("Tillåt ändring? (ja/nej): ");
                if (!answer.equalsIgnoreCase("ja")) {
                    return "Error: User denied patching the file.";
                }

                // Ersätt textblocket och spara filen
                String updatedContent = content.replace(search, replace);
                java.nio.file.Files.writeString(path, updatedContent);
                return "Success: File successfully patched.";
            } else if ("fetch_url".equals(name)) {
                String urlStr = (String) args.get("url");
                if (urlStr == null || urlStr.isBlank()) {
                    return "Error: URL parameter is missing.";
                }

                URI uri;
                try {
                    uri = URI.create(urlStr.trim());
                    String scheme = uri.getScheme();
                    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                        return "Error: Only 'http' and 'https' protocols are supported. Provided: " + urlStr;
                    }
                } catch (Exception e) {
                    return "Error: Invalid URL format: " + urlStr;
                }

                System.out.println("\n[SÄKERHET] Agenten vill hämta webbinnehåll från URL:");
                System.out.println("  > " + urlStr);
                String answer = IO.readln("Tillåt hämtning? (ja/nej): ");

                if (!answer.equalsIgnoreCase("ja")) {
                    return "Error: User denied fetching the URL.";
                }

                return fetchUrlContent(uri);
            } else if ("ask_question".equals(name)) {
                String prompt = (String) args.get("prompt");
                if (prompt == null || prompt.isBlank()) {
                    return "Error: 'prompt' parameter is missing.";
                }
                return askQuestion(prompt);
            } else if ("find_file".equals(name)) {
                String pattern = (String) args.get("pattern");
                if (pattern == null || pattern.isBlank()) {
                    return "Error: 'pattern' parameter is missing.";
                }
                String pathStr = (String) args.getOrDefault("path", ".");
                return findFiles(pathStr, pattern);
            } else if ("search_directory".equals(name)) {
                String query = (String) args.get("query");
                if (query == null || query.isBlank()) {
                    return "Error: 'query' parameter is missing.";
                }
                String pathStr = (String) args.getOrDefault("path", ".");
                String filePattern = (String) args.get("file_pattern");
                Boolean isRegex = (Boolean) args.get("is_regex");
                return searchDirectory(pathStr, query, filePattern, isRegex);
            }

            throw new IllegalArgumentException("Unknown tool call: " + name);

        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }

    private static String fetchUrlContent(URI uri) {
        try {
            HttpClient webClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) JavaChatAgent/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json,text/plain;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = webClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                return "HTTP Error " + statusCode + " when fetching " + uri;
            }

            // Cap raw stream read to prevent massive memory allocations
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int totalBytesRead = 0;
            boolean streamTruncated = false;

            try (java.io.InputStream in = response.body()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (totalBytesRead + read > MAX_WEB_DOWNLOAD_BYTES) {
                        int remaining = MAX_WEB_DOWNLOAD_BYTES - totalBytesRead;
                        if (remaining > 0) {
                            baos.write(buffer, 0, remaining);
                            totalBytesRead += remaining;
                        }
                        streamTruncated = true;
                        break;
                    }
                    baos.write(buffer, 0, read);
                    totalBytesRead += read;
                }
            }

            String content = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

            // Limit the text size sent to the LLM to protect the context window
            if (content.length() > MAX_WEB_DOWNLOAD_CHARS) {
                int originalLength = content.length();
                content = content.substring(0, MAX_WEB_DOWNLOAD_CHARS)
                        + "\n\n...[Innehållet trunkerades av storleksskäl. Visar " + MAX_WEB_DOWNLOAD_CHARS
                        + " av " + (streamTruncated ? (originalLength + "+") : originalLength) + " tecken]...";
            }

            return "HTTP " + statusCode + "\n" + content;

        } catch (Exception e) {
            return "Error fetching URL: " + e.getMessage();
        }
    }

    private static String executeProcess(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                return output.toString();
            }
        } catch (Exception e) {
            return "Error executing process: " + e.getMessage();
        }
    }

    private static DateTimeResult getCurrentDateTime() {
        var now = ZonedDateTime.now(ZoneId.of("Europe/Stockholm"));
        return new DateTimeResult(now.toString(), "Europe/Stockholm");
    }

    private static String askQuestion(String prompt) {
        System.out.println("\n[FRÅGA FRÅN AGENTEN]");
        System.out.println(prompt);
        String answer = IO.readln("Ditt svar: ");
        if (answer == null || answer.isBlank()) {
            return "User provided no answer (empty input).";
        }
        return answer;
    }

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", "target", "node_modules", ".gemini", "bin", "build", ".gradle", ".mvn"
    );

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".class", ".jar", ".zip", ".tar", ".gz", ".7z", ".exe", ".dll", ".so",
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".bmp", ".webp",
            ".pdf", ".bin", ".dat", ".pyc", ".o", ".a"
    );

    static String findFiles(String pathStr, String pattern) {
        Path root = Path.of(pathStr != null && !pathStr.isBlank() ? pathStr : ".");
        if (!Files.exists(root)) {
            return "Error: Path does not exist: " + pathStr;
        }
        if (!Files.isDirectory(root)) {
            return "Error: Path is not a directory: " + pathStr;
        }

        try {
            boolean hasSlash = pattern.contains("/") || pattern.contains("\\");
            String glob = pattern.replace('\\', '/');
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + (hasSlash ? glob : "**/" + glob));
            PathMatcher nameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);

            List<String> matches = new ArrayList<>();
            final int MAX_MATCHES = 100;
            boolean[] truncated = new boolean[]{false};

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path rel = root.relativize(file);
                    Path relNorm = Path.of(rel.toString().replace('\\', '/'));
                    if (nameMatcher.matches(file.getFileName()) || matcher.matches(rel) || matcher.matches(relNorm)) {
                        matches.add(rel.toString().replace('\\', '/'));
                        if (matches.size() >= MAX_MATCHES) {
                            truncated[0] = true;
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (matches.isEmpty()) {
                return "No files found matching pattern: " + pattern;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(truncated[0] ? "+ (truncated)" : "").append(" file(s):\n");
            for (String match : matches) {
                sb.append(match).append("\n");
            }
            if (truncated[0]) {
                sb.append("...[Truncated: limit of ").append(MAX_MATCHES).append(" matches reached]...\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error finding files: " + e.getMessage();
        }
    }

    static String searchDirectory(String pathStr, String query, String filePattern, Boolean isRegex) {
        Path root = Path.of(pathStr != null && !pathStr.isBlank() ? pathStr : ".");
        if (!Files.exists(root)) {
            return "Error: Path does not exist: " + pathStr;
        }
        if (!Files.isDirectory(root)) {
            return "Error: Path is not a directory: " + pathStr;
        }
        if (query == null || query.isEmpty()) {
            return "Error: 'query' parameter is missing.";
        }

        try {
            boolean regex = Boolean.TRUE.equals(isRegex);
            Pattern compiledPattern;
            if (regex) {
                compiledPattern = Pattern.compile(query);
            } else {
                compiledPattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
            }

            PathMatcher fileMatcher = null;
            if (filePattern != null && !filePattern.isBlank()) {
                String fp = filePattern.replace('\\', '/');
                fileMatcher = FileSystems.getDefault().getPathMatcher("glob:" + (fp.contains("/") ? fp : "**/" + fp));
            }

            final PathMatcher filterMatcher = fileMatcher;
            List<String> results = new ArrayList<>();
            final int MAX_MATCHES = 100;
            final int MAX_LINE_LEN = 300;
            boolean[] truncated = new boolean[]{false};

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString().toLowerCase();
                    for (String binExt : BINARY_EXTENSIONS) {
                        if (fileName.endsWith(binExt)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    if (attrs.size() > 5 * 1024 * 1024) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path rel = root.relativize(file);
                    String relPath = rel.toString().replace('\\', '/');
                    Path relNorm = Path.of(relPath);

                    if (filterMatcher != null) {
                        if (!filterMatcher.matches(rel) && !filterMatcher.matches(relNorm)
                                && !filterMatcher.matches(file.getFileName())) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    try (var reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                        String line;
                        int lineNum = 1;
                        while ((line = reader.readLine()) != null) {
                            if (compiledPattern.matcher(line).find()) {
                                String displayLine = line.trim();
                                if (displayLine.length() > MAX_LINE_LEN) {
                                    displayLine = displayLine.substring(0, MAX_LINE_LEN) + "...";
                                }
                                results.add(relPath + ":" + lineNum + ": " + displayLine);
                                if (results.size() >= MAX_MATCHES) {
                                    truncated[0] = true;
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                            lineNum++;
                        }
                    } catch (Exception ignored) {
                        // Skip files that cannot be decoded as UTF-8
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            if (results.isEmpty()) {
                return "No matches found for query: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(truncated[0] ? "+ (truncated)" : "").append(" match(es):\n");
            for (String res : results) {
                sb.append(res).append("\n");
            }
            if (truncated[0]) {
                sb.append("...[Truncated: limit of ").append(MAX_MATCHES).append(" matches reached]...\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error searching directory: " + e.getMessage();
        }
    }

    private static OpenRouterResponse sendToModel(HttpClient client, ObjectMapper mapper,
                                                  List<Message> history, List<Tool> tools,
                                                  String apiKey) throws IOException, InterruptedException {
        var requestBody = new Request(MODEL, history, tools);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .uri(URI.create(HOST + "/api/v1/chat/completions"))
                .build();
        var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), OpenRouterResponse.class);
    }
}

record Request(String model, List<Message> messages, List<Tool> tools) {
}

record Tool(String type, ToolFunctionSpec function) {
}

record ToolFunctionSpec(String name, String description, Map<String, Object> parameters) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record Message(String role, String content, String tool_call_id, List<ToolCall> tool_calls) {
    // Enkel konstruktor för vanliga meddelanden (user, system, assistant utan tool calls)
    public Message(String role, String content, String tool_call_id) {
        this(role, content, tool_call_id, null);
    }
}

record OpenRouterResponse(String id, String object, List<Choice> choices, Usage usage) {
}

record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {
}

record Choice(int index, MessageResponse message, String finish_reason) {
}

record MessageResponse(String role, String content, List<ToolCall> tool_calls) {
}

record ToolCall(String id, String type, ToolFunction function) {
}

record ToolFunction(String name, String arguments) {
}

record DateTimeResult(String now, String timezone) {
}
