package org.example.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Chat {
    public static final String HOST = "https://openrouter.ai";
    public static final String MODEL = "google/gemini-3.8-flash";
    public static int totalPromptTokens = 0;
    public static int totalCompletionTokens = 0;

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
                        Use these tools proactively to solve programming and file-management tasks when requested.
                        """, ""));

        // Definiera alla verktyg med beskrivningar och parametrar
        var tools = List.of(
                new Tool("function",
                        new ToolFunctionSpec(
                                "get_current_datetime",
                                "Returns the current date and time for Stockholm, Sweden.",
                                Map.of("type", "object",
                                        "properties", Map.of(),
                                        "required", List.of()
                                )
                        )
                ),
                new Tool("function",
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
                ),
                new Tool("function",
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
                ),
                new Tool("function",
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
                ),
                new Tool("function",
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
                ),
                new Tool("function",
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
                )
        );

        while (true) {
            String userInput = IO.readln("You: ");
            if (userInput.equalsIgnoreCase("quit")) {
                System.out.println("\n--- Token-sammanfattning för sessionen ---");
                System.out.println("Totalt input (prompt): " + totalPromptTokens);
                System.out.println("Totalt output (completion): " + totalCompletionTokens);
                System.out.println("Total förbrukning: " + (totalPromptTokens + totalCompletionTokens));
                break;
            }

            history.add(new Message("user", userInput, ""));
            while (true) {
                OpenRouterResponse or = sendToModel(httpClient, mapper, history, tools, API_KEY);
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

                    // HÄR ÄR ÄNDRINGEN: Skicka med msg.tool_calls() i historiken!
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
            }

            throw new IllegalArgumentException("Unknown tool call: " + name);

        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
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
