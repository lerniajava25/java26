package org.example.mcp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Streamable HTTP transport (MCP 2026-07-28) using only the JDK's built-in HTTP server.
 * <p>
 * What this transport does, per the spec:
 * - One endpoint (/mcp) that accepts POST. GET/DELETE -> 405 (no GET stream, no sessions any more).
 * - Validates the Origin header (DNS-rebinding protection) -> 403 if present and not allowed.
 * - Binds to 127.0.0.1 only by default.
 * - Requires MCP-Protocol-Version, Mcp-Method (and Mcp-Name for tools/call) headers and checks that
 * they match the JSON body -> 400 + error -32020 (HeaderMismatch) otherwise.
 * - Notifications -> 202 Accepted with no body.
 * - Requests -> 200 with Content-Type: application/json (a single JSON object; no SSE needed here).
 * - Ignores Mcp-Session-Id / Last-Event-ID (removed from the protocol).
 */
public class McpHttpServer {

    static final String ENDPOINT = "/mcp";
    static final int MAX_BODY_BYTES = 1_048_576;   // 1 MiB: reject absurdly large bodies

    /**
     * Origins from these hosts are accepted. Extend via env MCP_ALLOWED_HOSTS="example.com,foo.org".
     */
    static final Set<String> ALLOWED_ORIGIN_HOSTS = new HashSet<>(List.of("localhost", "127.0.0.1", "[::1]"));

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        String extra = System.getenv("MCP_ALLOWED_HOSTS");
        if (extra != null) for (String h : extra.split(",")) if (!h.isBlank()) ALLOWED_ORIGIN_HOSTS.add(h.trim());

        // Bind to loopback only: a local server must not be reachable from the network.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext(ENDPOINT, McpHttpServer::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());   // Java 21
        server.start();
        log("MCP time server (protocol " + McpCore.PROTOCOL_VERSION + ") listening on http://127.0.0.1:" + port + ENDPOINT);
    }

    static void log(String msg) {
        System.err.println("[time-server] " + msg);
    }

    // ------------------------------------------------------------------ request handling

    static void handle(HttpExchange ex) throws IOException {
        Object id = null;
        try {
            // 1. Origin check (before anything else)
            if (!originAllowed(ex.getRequestHeaders().getFirst("Origin"))) {
                send(ex, 403, McpCore.errorResponse(null, McpCore.INVALID_REQUEST, "Origin not allowed", null));
                return;
            }

            // 2. Only POST. Older revisions used GET (SSE stream) and DELETE (end session).
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Allow", "POST");
                ex.sendResponseHeaders(405, -1);
                return;
            }

            // 3. Read body (bounded) and parse JSON
            byte[] raw = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) {
                send(ex, 413, McpCore.errorResponse(null, McpCore.INVALID_REQUEST, "Request body too large", null));
                return;
            }
            Object parsed;
            try {
                parsed = McpCore.fromJson(new String(raw, UTF_8));
            } catch (JacksonException e) {   // unchecked in Jackson 3
                send(ex, 400, McpCore.errorResponse(null, McpCore.PARSE_ERROR, "Parse error: " + e.getOriginalMessage(), null));
                return;
            }
            if (!(parsed instanceof Map<?, ?>) || !(((Map<?, ?>) parsed).get("method") instanceof String)) {
                send(ex, 400, McpCore.errorResponse(null, McpCore.INVALID_REQUEST, "Not a JSON-RPC request", null));
                return;
            }
            @SuppressWarnings("unchecked") Map<String, Object> msg = (Map<String, Object>) parsed;

            // 4. Notification (no id)? Accept and return 202 with no body.
            if (!msg.containsKey("id")) {
                log("accepted notification: " + msg.get("method"));
                ex.sendResponseHeaders(202, -1);
                return;
            }
            if (!(msg.get("id") instanceof String || msg.get("id") instanceof Integer || msg.get("id") instanceof Long)) {
                send(ex, 400, McpCore.errorResponse(null, McpCore.INVALID_REQUEST, "id must be a string or integer", null));
                return;
            }
            id = msg.get("id");

            // 5. Header <-> body validation (skipped for legacy "initialize": McpCore answers that one)
            if (!"initialize".equals(msg.get("method"))) validateHeaders(ex.getRequestHeaders(), msg);

            // 6. Hand over to the protocol layer
            log(msg.get("method") + (msg.get("params") instanceof Map<?, ?> p && p.get("name") != null
                    ? " " + p.get("name") : ""));
            send(ex, 200, McpCore.handle(msg));

        } catch (McpException e) {
            send(ex, e.httpStatus, McpCore.errorResponse(id, e.code, e.getMessage(), e.data));
        } catch (Exception e) {
            log("internal error: " + e);
            send(ex, 500, McpCore.errorResponse(id, McpCore.INTERNAL_ERROR, "Internal error", null));
        }
    }

    /**
     * The transport mirrors body fields into headers so gateways can route without parsing JSON.
     * A server that parses the body MUST verify that the mirrored values agree (spec: Server Validation),
     * otherwise a gateway and the server could disagree about what a request does.
     */
    @SuppressWarnings("unchecked")
    static void validateHeaders(Headers h, Map<String, Object> msg) {
        String method = (String) msg.get("method");
        Map<String, Object> params = msg.get("params") instanceof Map
                ? (Map<String, Object>) msg.get("params") : Map.of();
        Map<String, Object> meta = params.get("_meta") instanceof Map
                ? (Map<String, Object>) params.get("_meta") : Map.of();

        String hVersion = h.getFirst("MCP-Protocol-Version");
        if (hVersion == null) throw mismatch("Missing required header: MCP-Protocol-Version");
        // If the body has no version at all, McpCore reports the missing _meta field (-32602) afterwards.
        if (meta.get(McpCore.META_VERSION) instanceof String bVersion && !hVersion.equals(bVersion))
            throw mismatch("Header mismatch: MCP-Protocol-Version header value '" + hVersion
                    + "' does not match body value '" + bVersion + "'");

        String hMethod = h.getFirst("Mcp-Method");
        if (hMethod == null) throw mismatch("Missing required header: Mcp-Method");
        if (!hMethod.equals(method))
            throw mismatch("Header mismatch: Mcp-Method header value '" + hMethod
                    + "' does not match body value '" + method + "'");

        if (method.equals("tools/call")) {   // (also prompts/get and resources/read in servers that have them)
            String hName = h.getFirst("Mcp-Name");
            if (hName == null) throw mismatch("Missing required header: Mcp-Name");
            String decoded = decodeHeaderValue(hName);
            if (!decoded.equals(params.get("name")))
                throw mismatch("Header mismatch: Mcp-Name header value '" + decoded
                        + "' does not match body value '" + params.get("name") + "'");
        }
    }

    static McpException mismatch(String message) {
        return new McpException(400, McpCore.HEADER_MISMATCH, message);
    }

    /**
     * Values that aren't plain ASCII are sent as =?base64?<base64 of UTF-8>?= ; servers MUST decode them.
     */
    static String decodeHeaderValue(String v) {
        if (v.startsWith("=?base64?") && v.endsWith("?=") && v.length() >= 11) {
            try {
                return new String(Base64.getDecoder().decode(v.substring(9, v.length() - 2)), UTF_8);
            } catch (IllegalArgumentException e) {
                throw mismatch("Malformed base64 header value");
            }
        }
        return v;
    }

    static boolean originAllowed(String origin) {
        if (origin == null) return true;   // non-browser clients usually send no Origin
        try {
            String host = new URI(origin).getHost();
            return host != null && ALLOWED_ORIGIN_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return false;                  // includes the literal Origin: null
        }
    }

    static void send(HttpExchange ex, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = McpCore.toJson(body).getBytes(UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
