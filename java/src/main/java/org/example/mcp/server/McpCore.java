package org.example.mcp.server;

import org.example.mcp.server.tools.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * Transport-independent MCP logic for protocol version 2026-07-28:
 * per-request _meta validation, server/discover, tools/list, tools/call.
 * Knows nothing about HTTP (except the HTTP status it attaches to errors).
 */
public final class McpCore {

    /**
     * Shared default instance pre-configured with {@link CurrentTimeTool} and {@link MySqlReadOnlyQueryTool}.
     */
    private static final McpCore DEFAULT = new McpCore(
            /*new CurrentTimeTool(),*/
            new MySqlReadOnlyQueryTool(),
            new Context7ResolveLibraryTool(),
            new Context7QueryDocsTool());

    public static McpCore getDefault() {
        return DEFAULT;
    }

    /**
     * Registers a tool on the default shared instance.
     */
    public static void registerTool(McpTool tool) {
        DEFAULT.addTool(tool);
    }

    /**
     * Preserved for backward compatibility.
     */
    public static final Map<String, Object> CURRENT_TIME_TOOL = CurrentTimeTool.DEFINITION;

    /**
     * One shared mapper. In Jackson 3 a JsonMapper is immutable and thread-safe, and is configured
     * through a builder. STRICT_DUPLICATE_DETECTION rejects bodies like {"a":1,"a":2}, which would
     * otherwise be silently resolved "last one wins" - exactly the kind of ambiguity the header/body
     * validation in the HTTP transport is meant to prevent.
     */
    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /**
     * JSON text -> Map / List / String / Number / Boolean / null. Throws JacksonException (unchecked) on bad JSON.
     */
    static Object fromJson(String json) {
        return MAPPER.readValue(json, Object.class);
    }

    /**
     * Map / List / ... -> JSON text (compact, single line).
     */
    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    static final String PROTOCOL_VERSION = "2026-07-28";
    static final List<Object> SUPPORTED_VERSIONS = List.of(PROTOCOL_VERSION);

    // JSON-RPC standard codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    // MCP codes (-32020..-32099 are reserved for the specification)
    static final int HEADER_MISMATCH = -32020;
    static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    public static final String META_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String META_CLIENT_CAPS = "io.modelcontextprotocol/clientCapabilities";
    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private final Map<String, McpTool> tools = Collections.synchronizedMap(new LinkedHashMap<>());
    private String instructions;
    private String serverName = "java-mcp-server";
    private String serverVersion = "1.0.0";

    public McpCore() {
    }

    public McpCore(McpTool... initialTools) {
        for (McpTool tool : initialTools) {
            addTool(tool);
        }
    }

    public McpCore(Collection<McpTool> initialTools) {
        for (McpTool tool : initialTools) {
            addTool(tool);
        }
    }

    /**
     * Adds an {@link McpTool} to this core instance.
     */
    public McpCore addTool(McpTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * Removes a tool by name.
     */
    public McpCore removeTool(String name) {
        tools.remove(name);
        return this;
    }

    /**
     * Returns an unmodifiable view of registered tools.
     */
    public Map<String, McpTool> getTools() {
        synchronized (tools) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        }
    }

    /**
     * Sets custom discover instructions.
     */
    public McpCore setInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /**
     * Sets custom server metadata (reported in _meta).
     */
    public McpCore setServerInfo(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        return this;
    }

    /**
     * Handles one already-parsed JSON-RPC *request* using the default shared instance
     * and returns the full JSON-RPC response object.
     */
    static Map<String, Object> handle(Map<String, Object> msg) {
        return DEFAULT.handleRequest(msg);
    }

    /**
     * Handles one already-parsed JSON-RPC *request* and returns the full JSON-RPC response object.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> msg) {
        String method = (String) msg.get("method");
        Map<String, Object> params = msg.get("params") instanceof Map
                ? (Map<String, Object>) msg.get("params") : new LinkedHashMap<>();

        // Legacy clients start with "initialize". We have no handshake; per spec, name our versions.
        // (Unknown method on HTTP => 404 + -32601.)
        if ("initialize".equals(method)) {
            throw new McpException(404, METHOD_NOT_FOUND,
                    "This server only supports MCP protocol version(s) " + SUPPORTED_VERSIONS
                            + " and has no initialize handshake. Send _meta.\"" + META_VERSION + "\" on every request.");
        }

        // ---- Per-request metadata: the heart of the stateless design ----
        Map<String, Object> meta = params.get("_meta") instanceof Map
                ? (Map<String, Object>) params.get("_meta") : Map.of();
        validateMeta(meta);

        Map<String, Object> result = switch (method) {
            case "server/discover" -> discover();
            case "tools/list" -> toolsList();
            case "tools/call" -> toolsCall(params);
            default -> throw new McpException(404, METHOD_NOT_FOUND, "Method not found: " + method);
        };
        return response(msg.get("id"), result);
    }

    static void validateMeta(Map<String, Object> meta) {
        // Missing required fields => malformed => -32602 and HTTP 400
        if (!(meta.get(META_VERSION) instanceof String version))
            throw new McpException(400, INVALID_PARAMS, "Missing required _meta field: " + META_VERSION);
        if (!(meta.get(META_CLIENT_CAPS) instanceof Map))
            throw new McpException(400, INVALID_PARAMS, "Missing required _meta field: " + META_CLIENT_CAPS);
        if (!SUPPORTED_VERSIONS.contains(version))
            throw new McpException(400, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version",
                    obj("supported", SUPPORTED_VERSIONS, "requested", version));
    }

    // ------------------------------------------------------------------ methods

    public Map<String, Object> discover() {
        String inst = instructions;
        if (inst == null) {
            if (tools.containsKey(CurrentTimeTool.NAME) && tools.size() == 1) {
                inst = "Use get_current_time to find out the current date and time.";
            } else {
                inst = "Available tools: " + String.join(", ", tools.keySet());
            }
        }
        return obj(
                "supportedVersions", SUPPORTED_VERSIONS,
                "capabilities", obj("tools", obj()),   // tools only, no listChanged
                "instructions", inst,
                "ttlMs", 3_600_000,
                "cacheScope", "public");
    }

    public Map<String, Object> toolsList() {
        List<Map<String, Object>> toolDefs;
        synchronized (tools) {
            toolDefs = tools.values().stream()
                    .map(McpTool::definition)
                    .toList();
        }
        return obj(
                "tools", toolDefs,   // deterministic order
                "ttlMs", 300_000,    // required on tools/list in this revision
                "cacheScope", "public");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toolsCall(Map<String, Object> params) {
        Object name = params.get("name");
        McpTool tool;
        if (!(name instanceof String toolName) || (tool = tools.get(toolName)) == null) {
            // Unknown tool = protocol error (JSON-RPC error). The request itself was processed, so HTTP 200.
            throw new McpException(200, INVALID_PARAMS, "Unknown tool: " + name);
        }
        Map<String, Object> args = params.get("arguments") instanceof Map
                ? (Map<String, Object>) params.get("arguments") : Map.of();

        try {
            ToolResult result = tool.execute(args);
            return result.toMap();
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            return ToolResult.error("Tool execution failed: " + (e.getMessage() != null ? e.getMessage() : e.toString())).toMap();
        }
    }

    static Map<String, Object> toolError(String message) {
        return ToolResult.error(message).toMap();
    }

    // ------------------------------------------------------------------ response helpers

    /**
     * Wraps a result: adds the required resultType and the recommended serverInfo in _meta.
     */
    Map<String, Object> response(Object id, Map<String, Object> result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resultType", "complete");
        r.putAll(result);
        r.put("_meta", obj(META_SERVER_INFO, obj("name", serverName, "version", serverVersion)));
        return obj("jsonrpc", "2.0", "id", id, "result", r);
    }

    static Map<String, Object> errorResponse(Object id, int code, String message, Object data) {
        Map<String, Object> err = obj("code", code, "message", message);
        if (data != null) err.put("data", data);
        Map<String, Object> resp = obj("jsonrpc", "2.0");
        if (id != null) resp.put("id", id);   // omitted when the id could not be read
        resp.put("error", err);
        return resp;
    }

    /**
     * obj("a", 1, "b", 2) -> insertion-ordered map.
     */
    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
