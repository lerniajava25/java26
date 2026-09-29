package org.example.ai;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class Chat {
    public static final String HOST = "https://openrouter.ai";
    public static final String MODEL = "google/gemini-3.8-flash";

    static void main() throws IOException, InterruptedException {
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
                        """));
        history.add(new Message("user",
                "What is the meaning of life?"));

        var requestBody = new Request(MODEL,
                history);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .uri(URI.create(HOST + "/api/v1/chat/completions"))
                .build();
        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        OpenRouterResponse or = mapper.readValue(response.body(), OpenRouterResponse.class);

        MessageResponse msg = or.choices().get(0).message();
        System.out.println("Assistant: " + msg.content());
    }
}

record Request(String model, List<Message> messages) {
}

record Message(String role, String content) {
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
