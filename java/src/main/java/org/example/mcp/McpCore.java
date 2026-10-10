package org.example.mcp;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport-independent MCP logic for protocol version 2026-07-28:
 * per-request _meta validation, server/discover, tools/list, tools/call.
 * Knows nothing about HTTP (except the HTTP status it attaches to errors).
 */
public final class McpCore {
    private McpCore() {
    }

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
    static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    static final String PROTOCOL_VERSION = "2026-07-28";
    static final List<Object> SUPPORTED_VERSIONS = List.of(PROTOCOL_VERSION);

    // JSON-RPC standard codes
    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;
    // MCP codes (-32020..-32099 are reserved for the specification)
    static final int HEADER_MISMATCH = -32020;
    static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    static final String META_VERSION = "io.modelcontextprotocol/protocolVersion";
    static final String META_CLIENT_CAPS = "io.modelcontextprotocol/clientCapabilities";
    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    /**
     * Handles one already-parsed JSON-RPC *request* and returns the full JSON-RPC response object.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> handle(Map<String, Object> msg) {
        String method = (String) msg.get("method");
        Map<String, Object> params = msg.get("params") instanceof Map
                ? (Map<String, Object>) msg.get("params") : new LinkedHashMap<>();

        // Legacy clients start with "initialize". We have no handshake; per spec, name our versions.
        // (Unknown method on HTTP => 404 + -32601.)
        if (method.equals("initialize")) {
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

    static Map<String, Object> discover() {
        return obj(
                "supportedVersions", SUPPORTED_VERSIONS,
                "capabilities", obj("tools", obj()),   // tools only, no listChanged
                "instructions", "Use get_current_time to find out the current date and time.",
                "ttlMs", 3_600_000,
                "cacheScope", "public");
    }

    static Map<String, Object> toolsList() {
        return obj(
                "tools", List.of(CURRENT_TIME_TOOL),   // deterministic order
                "ttlMs", 300_000,                      // required on tools/list in this revision
                "cacheScope", "public");
    }

    /**
     * Tool definition: what the LLM sees.
     */
    static final Map<String, Object> CURRENT_TIME_TOOL = obj(
            "name", "get_current_time",
            "title", "Current date and time",
            "description", "Returns the current date and time. Optionally takes an IANA time zone "
                    + "such as 'Europe/Stockholm' or 'America/New_York'. Defaults to UTC.",
            "inputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "timezone", obj(
                                    "type", "string",
                                    "description", "IANA time zone ID, e.g. Europe/Stockholm. Defaults to UTC.")),
                    "additionalProperties", false),
            "outputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "iso8601", obj("type", "string"),
                            "date", obj("type", "string"),
                            "time", obj("type", "string"),
                            "timezone", obj("type", "string"),
                            "utcOffset", obj("type", "string"),
                            "epochMillis", obj("type", "integer")),
                    "required", List.of("iso8601", "date", "time", "timezone", "utcOffset", "epochMillis")),
            "annotations", obj("readOnlyHint", true, "idempotentHint", true, "openWorldHint", false));

    @SuppressWarnings("unchecked")
    static Map<String, Object> toolsCall(Map<String, Object> params) {
        Object name = params.get("name");
        if (!"get_current_time".equals(name)) {
            // Unknown tool = protocol error (JSON-RPC error). The request itself was processed, so HTTP 200.
            throw new McpException(200, INVALID_PARAMS, "Unknown tool: " + name);
        }
        Map<String, Object> args = params.get("arguments") instanceof Map
                ? (Map<String, Object>) params.get("arguments") : Map.of();

        // Bad *input values* are tool execution errors (isError: true) so the LLM can self-correct.
        ZoneId zone;
        Object tz = args.get("timezone");
        try {
            if (tz == null) zone = ZoneOffset.UTC;
            else if (tz instanceof String s) zone = ZoneId.of(s);
            else return toolError("'timezone' must be a string, e.g. \"Europe/Stockholm\".");
        } catch (DateTimeException e) {
            return toolError("Unknown time zone '" + tz
                    + "'. Use an IANA ID such as \"Europe/Stockholm\" or \"UTC\".");
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> structured = obj(
                "iso8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "date", now.toLocalDate().toString(),
                "time", now.toLocalTime().withNano(0).toString(),
                "timezone", zone.getId(),
                "utcOffset", now.getOffset().getId(),
                "epochMillis", now.toInstant().toEpochMilli());

        return obj(
                // Backwards-compat: also serialize structured output as text
                "content", List.of(obj("type", "text", "text", toJson(structured))),
                "structuredContent", structured,
                "isError", false);
    }

    static Map<String, Object> toolError(String message) {
        return obj("content", List.of(obj("type", "text", "text", message)), "isError", true);
    }

    // ------------------------------------------------------------------ response helpers

    /**
     * Wraps a result: adds the required resultType and the recommended serverInfo in _meta.
     */
    static Map<String, Object> response(Object id, Map<String, Object> result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resultType", "complete");
        r.putAll(result);
        r.put("_meta", obj(META_SERVER_INFO, obj("name", "java-time-server", "version", "1.0.0")));
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
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
