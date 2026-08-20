package org.example.demo;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Demo {
    static void main() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://jsonplaceholder.typicode.com/todos/1"))
                .build();

        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        IO.println("HTTP GET: " + response.body());

        ObjectMapper mapper = new ObjectMapper();
        // Convert response.body() to POJO - Plain Old Java Object using Jackson
        Todo todo = mapper.readValue(response.body(), Todo.class);
        System.out.println(todo);
        //Back to json from POJO
        System.out.println(mapper.writeValueAsString(todo));
    }
}

record Todo(int userId, int id, String title, boolean completed) {
}
