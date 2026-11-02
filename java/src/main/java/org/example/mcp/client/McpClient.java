package org.example.mcp.client;

import org.example.mcp.server.McpCore;
import org.example.mcp.server.McpException;
import org.example.mcp.server.tools.McpToolInfo;
import org.example.mcp.server.tools.ToolResult;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client for an MCP 2.0 (stateless, specification version 2026-07-28) server over HTTP.
 * <p>
 * Authentication: pass an {@link McpAuth}, e.g. {@code new McpClient(url, McpAuth.bearer(token))}.
 * Responses may be plain JSON or a Server-Sent Events stream; both are handled.
 */
public class McpClient {

    public static final String PROTOCOL_VERSION = "2026-07-28";
    public static final String DEFAULT_CLIENT_NAME = "java-mcp-client";
    public static final String DEFAULT_CLIENT_VERSION = "1.0.0";

    private static final int HEADER_MISMATCH = -32020;
    private static final System.Logger LOG = System.getLogger(McpClient.class.getName());

    /**
     * Kills reads that hang: closes the stream, which makes a blocked read fail.
     */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mcp-client-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String clientName;
    private final String clientVersion;
    private final McpAuth auth;
    private final AtomicLong requestId = new AtomicLong(1);

    private volatile Duration responseTimeout = Duration.ofSeconds(60);
    private volatile int maxResponseBytes = 32 * 1024 * 1024;

    /**
     * The x-mcp-header annotations of the tools seen in the last tools/list (null = never listed).
     */
    private volatile Map<String, List<ToolHeaders.Param>> toolHeaderParams;

    public McpClient(String url) {
        this(URI.create(url));
    }

    public McpClient(String url, McpAuth auth) {
        this(URI.create(url), auth);
    }

    public McpClient(URI endpoint) {
        this(endpoint, McpAuth.NONE);
    }

    public McpClient(URI endpoint, McpAuth auth) {
        this(endpoint, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Never follow redirects: a redirect must not carry the credentials to another host.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), DEFAULT_CLIENT_NAME, DEFAULT_CLIENT_VERSION, auth);
    }

    public McpClient(URI endpoint, HttpClient httpClient) {
        this(endpoint, httpClient, DEFAULT_CLIENT_NAME, DEFAULT_CLIENT_VERSION);
    }

    public McpClient(URI endpoint, HttpClient httpClient, String clientName, String clientVersion) {
        this(endpoint, httpClient, clientName, clientVersion, McpAuth.NONE);
    }

    public McpClient(URI endpoint, HttpClient httpClient, String clientName, String clientVersion, McpAuth auth) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clientName = Objects.requireNonNull(clientName, "clientName must not be null");
        this.clientVersion = Objects.requireNonNull(clientVersion, "clientVersion must not be null");
        this.auth = Objects.requireNonNull(auth, "auth must not be null");
        if (auth != McpAuth.NONE) requireSecureTransport(endpoint);
        this.mapper = new ObjectMapper();
    }

    /**
     * How long a response (including a whole event stream) may take to arrive. Default 60 seconds.
     */
    public McpClient responseTimeout(Duration timeout) {
        this.responseTimeout = Objects.requireNonNull(timeout);
        return this;
    }

    /**
     * Upper limit for the size of one response. Default 32 MiB (tool lists with icons can be large).
     */
    public McpClient maxResponseBytes(int bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("bytes must be positive");
        this.maxResponseBytes = bytes;
        return this;
    }

    /**
     * Safeguard (not a rule from the MCP spec): never send credentials in clear text over a network.
     * Plain http is only accepted for loopback, which is handy for local development.
     */
    private static void requireSecureTransport(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean loopback = host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"));
        if (!https && !(loopback && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Refusing to send credentials to " + uri
                    + ": use https (plain http is only allowed for localhost)");
        }
    }

    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * Calls 'server/discover' to retrieve server capabilities and metadata.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> discover() throws IOException, InterruptedException {
        Map<String, Object> response = sendRequest("server/discover", Map.of(), null, Map.of());
        return (Map<String, Object>) response.get("result");
    }

    /**
     * Calls 'tools/list' and returns descriptors for all tools available on the server.
     * Tools whose x-mcp-header annotations break the spec are left out (and logged), as the spec requires.
     */
    @SuppressWarnings("unchecked")
    public List<McpToolInfo> listTools() throws IOException, InterruptedException {
        Map<String, Object> response = sendRequest("tools/list", Map.of(), null, Map.of());
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null || !(result.get("tools") instanceof List<?> rawList)) {
            toolHeaderParams = Map.of();
            return List.of();
        }
        List<McpToolInfo> tools = new ArrayList<>();
        Map<String, List<ToolHeaders.Param>> annotations = new HashMap<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> map = (Map<String, Object>) raw;
                Object name = map.get("name");
                try {
                    List<ToolHeaders.Param> params = ToolHeaders.parse(map.get("inputSchema"));
                    if (name instanceof String n) annotations.put(n, params);
                } catch (IllegalArgumentException e) {
                    LOG.log(System.Logger.Level.WARNING, "Ignoring tool ''{0}'': invalid x-mcp-header annotation ({1})", name, e.getMessage());
                    continue;
                }
                tools.add(McpToolInfo.fromMap(map));
            }
        }
        toolHeaderParams = Map.copyOf(annotations);
        return Collections.unmodifiableList(tools);
    }

    /**
     * Calls 'tools/call' on the MCP server and returns the parsed {@link ToolResult}.
     * Parameters the server marked with x-mcp-header are mirrored into Mcp-Param-* headers.
     */
    public ToolResult callTool(String name, Map<String, Object> arguments) throws IOException, InterruptedException {
        Map<String, Object> args = arguments != null ? arguments : Map.of();

        if (toolHeaderParams == null) listTools();   // never listed: learn the tools' header annotations first

        try {
            return doCallTool(name, args);
        } catch (McpException e) {
            if (e.code != HEADER_MISMATCH) throw e;
            // The server expects other headers than we sent: its tool definition probably changed. Look again, retry once.
            listTools();
            return doCallTool(name, args);
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult doCallTool(String name, Map<String, Object> args) throws IOException, InterruptedException {
        List<ToolHeaders.Param> params = toolHeaderParams.getOrDefault(name, List.of());
        Map<String, String> extraHeaders = ToolHeaders.headersFor(params, args);

        Map<String, Object> callParams = new LinkedHashMap<>();
        callParams.put("name", name);
        callParams.put("arguments", args);

        Map<String, Object> response = sendRequest("tools/call", callParams, name, extraHeaders);
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
     * All content block types are rendered (see {@link #renderContent}).
     */
    public String callToolAsString(String name, Map<String, Object> arguments) {
        try {
            ToolResult result = callTool(name, arguments);
            StringBuilder sb = new StringBuilder(renderContent(result.content()));
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
     * Turns the content blocks of a tool result into text for a language model.
     * <ul>
     *   <li>{@code text}: the text</li>
     *   <li>{@code resource} (embedded resource, e.g. the file GitHub's get_file_contents returns): the resource's
     *       text under a header with its URI; binary data is not dumped into the text, only described</li>
     *   <li>{@code resource_link}: a one-line reference</li>
     *   <li>{@code image} / {@code audio}: a placeholder, a text-only model cannot use the bytes</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static String renderContent(List<Map<String, Object>> content) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : content) {
            String type = block.get("type") instanceof String t ? t : "";
            String piece = switch (type) {
                case "resource" ->
                        block.get("resource") instanceof Map<?, ?> r ? renderResource((Map<String, Object>) r) : null;
                case "resource_link" -> "[resource link: " + firstString(block, "name", "uri")
                        + (block.get("uri") instanceof String u && !u.equals(block.get("name")) ? " <" + u + ">" : "")
                        + (block.get("description") instanceof String d ? " - " + d : "") + "]";
                case "image", "audio" ->
                        "[" + type + " content" + (block.get("mimeType") instanceof String m ? " (" + m + ")" : "") + " omitted]";
                default ->
                        block.get("text") instanceof String s ? s : null;   // "text" and unknown types that carry text
            };
            if (piece != null && !piece.isEmpty()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    private static String renderResource(Map<String, Object> r) {
        String uri = r.get("uri") instanceof String u ? u : "resource";
        String mime = r.get("mimeType") instanceof String m ? " (" + m + ")" : "";
        if (r.get("text") instanceof String text) return "--- " + uri + mime + " ---\n" + text;
        if (r.get("blob") instanceof String blob)
            return "[binary resource " + uri + mime + ", about " + (blob.length() * 3L / 4) + " bytes, omitted]";
        return "[empty resource " + uri + "]";
    }

    private static String firstString(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.get(k) instanceof String s && !s.isEmpty()) return s;
        return "?";
    }

    /**
     * Sends a JSON-RPC request adhering to MCP 2.0 stateless HTTP transport.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRequest(String method, Map<String, Object> params, String toolName,
                                            Map<String, String> extraHeaders) throws IOException, InterruptedException {
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

        HttpResponse<InputStream> httpResponse;
        boolean retriedAfterChallenge = false;
        while (true) {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(15))     // until the response headers have arrived
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .header("Mcp-Method", method)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

            if ("tools/call".equals(method) && toolName != null) {
                reqBuilder.header("Mcp-Name", ToolHeaders.encodeValue(toolName));
            }
            for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }

            // Credentials go on EVERY request (the spec requires it for bearer tokens), so they are asked for every time.
            for (Map.Entry<String, String> h : auth.requestHeaders().entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }

            httpResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            int status = httpResponse.statusCode();
            if (status == 401 || status == 403) {
                AuthChallenge challenge = AuthChallenge.from(status, httpResponse.headers().allValues("WWW-Authenticate"));
                // 401 is always about authorization. 403 only counts if it carries a Bearer challenge;
                // other 403s (e.g. a rejected Origin) fall through to the normal error handling below.
                if (status == 401 || challenge.bearer()) {
                    closeQuietly(httpResponse.body());
                    if (!retriedAfterChallenge && auth.onChallenge(challenge)) {
                        retriedAfterChallenge = true;   // retry exactly once, then give up
                        continue;
                    }
                    throw new McpAuthException(challenge);
                }
            }
            break;
        }

        int statusCode = httpResponse.statusCode();
        Map<String, Object> responseMap = readMessage(httpResponse, id);

        if (responseMap.containsKey("error")) {
            Map<String, Object> err = (Map<String, Object>) responseMap.get("error");
            int code = err.get("code") instanceof Number n ? n.intValue() : McpCore.INTERNAL_ERROR;
            String message = (String) err.getOrDefault("message", "Unknown error");
            Object data = err.get("data");
            throw new McpException(statusCode, code, message, data);
        }

        if (statusCode >= 400) {
            throw new McpException(statusCode, McpCore.INTERNAL_ERROR,
                    "Server returned HTTP " + statusCode + ": " + responseMap);
        }

        if (responseMap.get("result") instanceof Map<?, ?> r && "input_required".equals(r.get("resultType"))) {
            throw new McpException(statusCode, McpCore.INTERNAL_ERROR,
                    "The server asked for additional input (a multi round-trip request), which this client does not support yet");
        }

        return responseMap;
    }

    // ================================================================== reading responses (JSON or SSE)

    /**
     * The server answers a request with a single JSON object or an SSE stream; the client MUST support both.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readMessage(HttpResponse<InputStream> response, long id) throws IOException {
        int statusCode = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).trim();

        AtomicBoolean timedOut = new AtomicBoolean();
        InputStream in = new LimitedInputStream(response.body(), maxResponseBytes);
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                    timedOut.set(true);
                    closeQuietly(in);
                },
                responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            if (contentType.startsWith("text/event-stream")) {
                return readEventStream(in, id, statusCode);
            }
            byte[] bytes = in.readAllBytes();
            String body = new String(bytes, UTF_8);
            if (looksLikeEventStream(body)) {
                // Not what the spec says (it must be text/event-stream), but a proxy or server may get the header wrong.
                LOG.log(System.Logger.Level.WARNING, "Server sent an event stream with Content-Type ''{0}''; reading it as one", contentType);
                return readEventStream(new java.io.ByteArrayInputStream(bytes), id, statusCode);
            }
            try {
                return mapper.readValue(body, Map.class);
            } catch (Exception e) {
                throw new McpException(statusCode, McpCore.PARSE_ERROR,
                        "Failed to parse server response as JSON (HTTP " + statusCode + ", Content-Type: "
                                + (contentType.isEmpty() ? "none" : contentType) + "): " + abbreviate(body));
            }
        } catch (IOException e) {
            if (timedOut.get())
                throw new IOException("Timed out waiting for the server's response (" + responseTimeout + ")", e);
            throw e;
        } finally {
            watchdog.cancel(false);
            closeQuietly(in);   // also cancels a stream we stopped reading (closing it is the cancellation signal)
        }
    }

    /**
     * Reads Server-Sent Events until the JSON-RPC response to request {@code id} arrives. Notifications (progress,
     * log messages) that come first are skipped. Event fields other than "data" are ignored, comments too.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readEventStream(InputStream in, long id, int statusCode) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
        StringBuilder data = new StringBuilder();
        boolean hasData = false;
        boolean first = true;
        String line;
        while ((line = reader.readLine()) != null) {      // handles \n, \r\n and \r
            if (first) {
                first = false;
                if (line.startsWith("\uFEFF")) line = line.substring(1);
            }
            if (line.isEmpty()) {                          // blank line = dispatch the event
                if (hasData) {
                    Map<String, Object> msg = tryParse(data.toString());
                    data.setLength(0);
                    hasData = false;
                    if (msg != null && isResponseTo(msg, id)) return msg;
                }
                continue;
            }
            if (line.startsWith(":")) continue;           // comment / keep-alive
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if (field.equals("data")) {
                if (hasData) data.append('\n');
                data.append(value);
                hasData = true;
            }
        }
        if (hasData) {                                     // stream ended without a final blank line
            Map<String, Object> msg = tryParse(data.toString());
            if (msg != null && isResponseTo(msg, id)) return msg;
        }
        throw new McpException(statusCode, McpCore.INTERNAL_ERROR, "The server's event stream ended without a response to request " + id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryParse(String json) {
        try {
            Object o = mapper.readValue(json, Object.class);
            return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        } catch (Exception e) {
            return null;   // not JSON: not for us
        }
    }

    /**
     * A response has result or error and no method. An error without an id (the server could not read ours) counts too.
     */
    private static boolean isResponseTo(Map<String, Object> msg, long id) {
        if (msg.containsKey("method")) return false;                 // notification (or a request we cannot answer)
        if (!msg.containsKey("result") && !msg.containsKey("error")) return false;
        Object mid = msg.get("id");
        return mid == null || (mid instanceof Number n && n.longValue() == id);
    }

    /**
     * SSE starts with a field ("event:", "data:") or a comment; JSON never does.
     */
    private static boolean looksLikeEventStream(String body) {
        int i = 0;
        if (body.startsWith("\uFEFF")) i = 1;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
        return body.startsWith("event:", i) || body.startsWith("data:", i) || body.startsWith(":", i);
    }

    private static String abbreviate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "... (" + s.length() + " chars)" : s;
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) { /* nothing to do */ }
    }

    /**
     * Fails reading once more than {@code limit} bytes have been delivered.
     */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) add(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) add(n);
            return n;
        }

        private void add(long n) throws IOException {
            count += n;
            if (count > limit) throw new IOException("Server response is larger than " + limit + " bytes");
        }
    }

    /**
     * Encodes header values per the MCP spec (Base64 sentinel for anything that is not plain visible ASCII).
     */
    static String encodeHeaderValue(String value) {
        return ToolHeaders.encodeValue(value);
    }
}
