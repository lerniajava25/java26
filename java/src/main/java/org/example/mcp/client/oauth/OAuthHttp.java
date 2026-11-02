package org.example.mcp.client.oauth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The few HTTP calls the OAuth flow makes. The URLs come from the (untrusted) MCP server and authorization server,
 * so: https only, no redirects followed, bounded response size, timeouts.
 */
final class OAuthHttp {
    static final int MAX_BYTES = 1_048_576;

    record Response(int status, String body) {
    }

    private final HttpClient client;
    private final boolean allowLoopbackHttp;
    private final JsonMapper json = JsonMapper.builder().build();

    OAuthHttp(HttpClient client, boolean allowLoopbackHttp) {
        this.client = client;
        this.allowLoopbackHttp = allowLoopbackHttp;
    }

    static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Authorization server endpoints MUST be https. Plain http is accepted for loopback only when explicitly enabled (development).
     */
    void requireSecure(URI uri, String what) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) && uri.getHost() != null) return;
        if (allowLoopbackHttp && "http".equalsIgnoreCase(scheme) && isLoopback(uri.getHost())) return;
        throw new OAuthException(what + " must use https: " + uri);
    }

    static boolean isLoopback(String host) {
        return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"));
    }

    Response get(URI uri, String what) throws IOException, InterruptedException {
        requireSecure(uri, what);
        return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").GET().build());
    }

    Response postForm(URI uri, Map<String, String> form, String authorizationHeader, String what) throws IOException, InterruptedException {
        requireSecure(uri, what);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString()));
        if (authorizationHeader != null) b.header("Authorization", authorizationHeader);
        return send(b.build());
    }

    Response postJson(URI uri, Object body, String what) throws IOException, InterruptedException {
        requireSecure(uri, what);
        return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
    }

    private Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES)
                throw new OAuthException("Response from " + request.uri().getHost() + " is too large");
            return new Response(r.statusCode(), new String(bytes, UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> parseObject(String body, String what) {
        try {
            Object o = json.readValue(body, Object.class);
            if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (JacksonException ignored) {
            // handled below
        }
        throw new OAuthException(what + " is not a valid JSON object");
    }

    static String enc(String s) {
        return URLEncoder.encode(s, UTF_8).replace("+", "%20");
    }

    static List<String> stringList(Object o) {
        if (!(o instanceof List<?> l)) return null;
        return l.stream().filter(x -> x instanceof String).map(x -> (String) x).toList();
    }
}
