package org.example.mcp;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpClientTest {

    static final String BASE_URL = "http://127.0.0.1:3000/mcp";
    static final String V = "2026-07-28";
    static final String META = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"" + V + "\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"junit\",\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{}}";

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpRequest.Builder baseRequest(String json) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private HttpResponse<String> post(String json, Consumer<HttpRequest.Builder> headerFn) {
        var builder = baseRequest(json);
        headerFn.accept(builder);
        try {
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Order(1)
    void server_discover() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{" + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "server/discover"));
        assertEquals(200, resp.statusCode(), "Expected HTTP 200");
        assertNotNull(resp.body(), "Response body should not be null");
    }

    @Test
    @Order(2)
    void tools_list() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{" + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/list"));
        assertEquals(200, resp.statusCode(), "Expected HTTP 200");
        assertNotNull(resp.body());
    }

    @Test
    @Order(3)
    void tools_call_get_current_time_stockholm() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_current_time\",\"arguments\":{\"timezone\":\"Europe/Stockholm\"}," + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/call")
                        .header("Mcp-Name", "get_current_time"));
        assertEquals(200, resp.statusCode(), "Expected HTTP 200");
        assertNotNull(resp.body());
    }

    @Test
    @Order(4)
    void tools_call_bad_timezone() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"get_current_time\",\"arguments\":{\"timezone\":\"Mars/Olympus\"}," + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/call")
                        .header("Mcp-Name", "get_current_time"));
        assertEquals(200, resp.statusCode(), "Expected HTTP 200 (tool execution error)");
        assertNotNull(resp.body());
        assertTrue(resp.body().contains("-32603") || resp.body().contains("error"),
                "Expected error in response for bad timezone");
    }

    @Test
    @Order(5)
    void mcp_name_header_mismatch() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"get_current_time\",\"arguments\":{}," + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/call")
                        .header("Mcp-Name", "something_else"));
        assertEquals(400, resp.statusCode(), "Expected HTTP 400");
        assertTrue(resp.body().contains("-32020"), "Expected error code -32020");
    }

    @Test
    @Order(6)
    void missing_mcp_protocol_version_header() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\",\"params\":{" + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("Mcp-Method", "tools/list"));
        assertEquals(400, resp.statusCode(), "Expected HTTP 400");
        assertTrue(resp.body().contains("-32020"), "Expected error code -32020");
    }

    @Test
    @Order(7)
    void unsupported_version() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", "1900-01-01")
                        .header("Mcp-Method", "tools/list"));
        assertEquals(400, resp.statusCode(), "Expected HTTP 400");
        assertTrue(resp.body().contains("-32022"), "Expected error code -32022");
    }

    @Test
    @Order(8)
    void missing_meta_in_body() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/list\",\"params\":{}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/list"));
        assertEquals(400, resp.statusCode(), "Expected HTTP 400");
        assertTrue(resp.body().contains("-32602"), "Expected error code -32602");
    }

    @Test
    @Order(9)
    void unknown_method() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":{" + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "ping"));
        assertEquals(404, resp.statusCode(), "Expected HTTP 404");
        assertTrue(resp.body().contains("-32601"), "Expected error code -32601");
    }

    @Test
    @Order(10)
    void legacy_initialize() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}";
        HttpResponse<String> resp = post(body, b -> {
        });
        assertEquals(200, resp.statusCode(), "Expected HTTP 200");
        assertNotNull(resp.body());
    }

    @Test
    @Order(11)
    void notification() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/whatever\"}";
        HttpResponse<String> resp = post(body, b -> {
        });
        assertEquals(202, resp.statusCode(), "Expected HTTP 202");
        assertTrue(resp.body().isEmpty(), "Expected empty body for notification");
    }

    @Test
    @Order(12)
    void bad_origin() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/list\",\"params\":{" + META + "}}";
        HttpResponse<String> resp = post(body, b ->
                b.header("MCP-Protocol-Version", V)
                        .header("Mcp-Method", "tools/list")
                        .header("Origin", "https://evil.example"));
        assertEquals(403, resp.statusCode(), "Expected HTTP 403");
    }

    @Test
    @Order(13)
    void get_method_not_allowed() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode(), "Expected HTTP 405 Method Not Allowed");
    }
}
