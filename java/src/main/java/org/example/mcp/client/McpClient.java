package org.example.mcp.client;

import org.example.mcp.server.McpCore;
import org.example.mcp.server.McpException;
import org.example.mcp.server.tools.McpToolInfo;
import org.example.mcp.server.tools.ToolResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for an MCP 2.0 (stateless, specification version 2026-07-28) server over HTTP.
 */
public class McpClient {

    public static final String PROTOCOL_VERSION = "2026-07-28";
    public static final String DEFAULT_CLIENT_NAME = "java-mcp-client";
    public static final String DEFAULT_CLIENT_VERSION = "1.0.0";

    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String clientName;
    private final String clientVersion;
    private final AtomicLong requestId = new AtomicLong(1);

    public McpClient(String url) {
        this(URI.create(url));
    }

    public McpClient(URI endpoint) {
        this(endpoint, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public McpClient(URI endpoint, HttpClient httpClient) {
        this(endpoint, httpClient, DEFAULT_CLIENT_NAME, DEFAULT_CLIENT_VERSION);
    }

    public McpClient(URI endpoint, HttpClient httpClient, String clientName, String clientVersion) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clientName = Objects.requireNonNull(clientName, "clientName must not be null");
        this.clientVersion = Objects.requireNonNull(clientVersion, "clientVersion must not be null");
        this.mapper = new ObjectMapper();
    }

    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * Calls 'server/discover' to retrieve server capabilities and metadata.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> discover() throws IOException, InterruptedException {
        Map<String, Object> response = sendRequest("server/discover", Map.of(), null);
        return (Map<String, Object>) response.get("result");
    }

    /**
     * Calls 'tools/list' and returns descriptors for all tools available on the server.
     */
    @SuppressWarnings("unchecked")
    public List<McpToolInfo> listTools() throws IOException, InterruptedException {
        Map<String, Object> response = sendRequest("tools/list", Map.of(), null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null || !(result.get("tools") instanceof List<?> rawList)) {
            return List.of();
        }
        List<McpToolInfo> tools = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                tools.add(McpToolInfo.fromMap((Map<String, Object>) map));
            }
        }
        return Collections.unmodifiableList(tools);
    }

    /**
     * Calls 'tools/call' on the MCP server and returns the parsed {@link ToolResult}.
     */
    @SuppressWarnings("unchecked")
    public ToolResult callTool(String name, Map<String, Object> arguments) throws IOException, InterruptedException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments != null ? arguments : Map.of());

        Map<String, Object> response = sendRequest("tools/call", params, name);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            throw new McpException(500, McpCore.INTERNAL_ERROR, "Invalid server response: missing 'result'");
        }

        List<Map<String, Object>> content = result.get("content") instanceof List<?> c
                ? (List<Map<String, Object>>) c : List.of();
        Map<String, Object> structured = result.get("structuredContent") instanceof Map<?, ?> s
                ? (Map<String, Object>) s : null;
        boolean isError = Boolean.TRUE.equals(result.get("isError"));

        return new ToolResult(content, structured, isError);
    }

    /**
     * Calls a tool and formats its result as a string suitable for passing to LLM chat history.
     */
    public String callToolAsString(String name, Map<String, Object> arguments) {
        try {
            ToolResult result = callTool(name, arguments);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : result.content()) {
                Object text = block.get("text");
                if (text != null) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
            }
            if (sb.isEmpty() && result.structuredContent() != null) {
                sb.append(mapper.writeValueAsString(result.structuredContent()));
            }
            if (sb.isEmpty()) {
                return result.isError() ? "Error: Tool returned empty response." : "Success";
            }
            if (result.isError() && !sb.toString().toLowerCase().contains("error")) {
                return "Error: " + sb;
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error executing tool '" + name + "': " + e.getMessage();
        }
    }

    /**
     * Sends a JSON-RPC request adhering to MCP 2.0 stateless HTTP transport.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRequest(String method, Map<String, Object> params, String toolName)
            throws IOException, InterruptedException {
        long id = requestId.getAndIncrement();

        Map<String, Object> fullParams = new LinkedHashMap<>(params);
        fullParams.put("_meta", McpCore.obj(
                McpCore.META_VERSION, PROTOCOL_VERSION,
                "io.modelcontextprotocol/clientInfo", McpCore.obj("name", clientName, "version", clientVersion),
                McpCore.META_CLIENT_CAPS, McpCore.obj()
        ));

        Map<String, Object> jsonRpcBody = McpCore.obj(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", fullParams
        );

        String bodyJson = mapper.writeValueAsString(jsonRpcBody);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", method)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

        if ("tools/call".equals(method) && toolName != null) {
            reqBuilder.header("Mcp-Name", encodeHeaderValue(toolName));
        }

        HttpResponse<String> httpResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();

        Map<String, Object> responseMap;
        try {
            responseMap = mapper.readValue(responseBody, Map.class);
        } catch (Exception e) {
            throw new McpException(statusCode, McpCore.PARSE_ERROR,
                    "Failed to parse server response as JSON (HTTP " + statusCode + "): " + responseBody);
        }

        if (responseMap.containsKey("error")) {
            Map<String, Object> err = (Map<String, Object>) responseMap.get("error");
            int code = err.get("code") instanceof Number n ? n.intValue() : McpCore.INTERNAL_ERROR;
            String message = (String) err.getOrDefault("message", "Unknown error");
            Object data = err.get("data");
            throw new McpException(statusCode, code, message, data);
        }

        if (statusCode >= 400) {
            throw new McpException(statusCode, McpCore.INTERNAL_ERROR,
                    "Server returned HTTP " + statusCode + ": " + responseBody);
        }

        return responseMap;
    }

    /**
     * Encodes header values with non-ASCII characters per RFC / MCP spec.
     */
    static String encodeHeaderValue(String value) {
        if (value == null) return "";
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                String b64 = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                return "=?base64?" + b64 + "?=";
            }
        }
        return value;
    }
}
