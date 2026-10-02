package org.example.ai;

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

    public static void main(String[] args) throws IOException, InterruptedException {
        final String API_KEY = System.getenv("OPENROUTER_API_KEY");
        final List<Message> history = new ArrayList<>();

        HttpClient httpClient = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        history.add(new Message("system",
                """
                        Do not output chain-of-thought reasoning. Only provide the final answer.
                        Your answers should be brief and to the point. Use only max 2 sentences.
                        """, ""));
        var tools = List.of(
                new Tool("function", new ToolFunctionSpec(
                        "get_current_datetime",
                        "Returns the current date and time for Stockholm, Sweden.",
                        Map.of("type", "object", "properties", Map.of(), "required", List.of())
                ))
        );

        while (true) {
            String userInput = IO.readln("You: ");
            if (userInput.equalsIgnoreCase("quit"))
                break;

            history.add(new Message("user",
                    userInput, ""));
            while (true) {
                OpenRouterResponse or = sendToModel(httpClient, mapper, history, tools, API_KEY);

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
        if ("get_current_datetime".equals(name)) {
            DateTimeResult result = getCurrentDateTime();
            String toolOutputJson = mapper.writeValueAsString(result);
            return toolOutputJson;
        }
        throw new IllegalArgumentException("Unknown tool call: " + name);
    }

    private static DateTimeResult getCurrentDateTime() {
        var now = ZonedDateTime.now(ZoneId.of("Europe/Stockholm"));
        return new DateTimeResult(now.toString(), "Europe/Stockholm");
    }

    private static OpenRouterResponse sendToModel(HttpClient client, ObjectMapper mapper,
                                                  List<Message> history, List<Tool> tools,
                                                  String apiKey) throws IOException, InterruptedException {
        var requestBody = new Request(MODEL,
                history, tools);
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

record Message(String role, String content, String tool_call_id) {
}

record OpenRouterResponse(String id, String object, List<Choice> choices) {
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
