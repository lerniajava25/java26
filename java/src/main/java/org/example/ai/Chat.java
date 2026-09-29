package org.example.ai;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class Chat {
    public static final String HOST = "https://openrouter.ai";

    static void main() throws IOException, InterruptedException {
        final String API_KEY = System.getenv("OPENROUTER_API_KEY");

        HttpClient httpClient = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        var requestBody = new Request("openrouter/free",
                List.of(
                        new Message("user",
                                "What is the meaning of life?")
                ));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .uri(URI.create(HOST + "/api/v1/chat/completions"))
                .build();
        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());

    }
}

record Request(String model, List<Message> messages) {
}

record Message(String role, String content) {
}
