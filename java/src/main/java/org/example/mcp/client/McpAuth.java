package org.example.mcp.client;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Supplies the access token for an {@link McpClient}. The token is asked for on EVERY HTTP request
 * (the spec requires the Authorization header on every request), so an implementation can hand out
 * a fresh token whenever the old one is about to expire.
 * <p>
 * Simple cases: {@code McpAuth.bearer("token")} or {@code McpAuth.bearer(() -> store.currentToken())}.
 * An OAuth 2.1 implementation (authorization code + PKCE, refresh tokens, ...) is just another
 * implementation of this interface; {@link McpClient} does not need to change.
 */
@FunctionalInterface
public interface McpAuth {

    /**
     * Headers the client sets itself.
     */
    java.util.Set<String> RESERVED_HEADERS = java.util.Set.of("content-type", "accept", "host", "content-length",
            "connection", "transfer-encoding", "expect", "upgrade", "origin");

    /**
     * No authentication: no Authorization header is sent.
     */
    McpAuth NONE = () -> null;

    /**
     * The access token to send as "Authorization: Bearer ...", or null to send none.
     */
    String accessToken() throws IOException, InterruptedException;

    /**
     * The authentication headers to add to every request. The default sends the access token as
     * {@code Authorization: Bearer ...}; override it for schemes that use another header (API keys, ...).
     */
    default Map<String, String> requestHeaders() throws IOException, InterruptedException {
        String token = accessToken();
        return token == null ? Map.of() : Map.of("Authorization", "Bearer " + token);
    }

    /**
     * Called when the server answers 401 (or 403 with a Bearer challenge). Return true if the credentials
     * have changed (token refreshed, step-up authorization done, ...) and the request should be retried
     * once. The default gives up immediately.
     */
    default boolean onChallenge(AuthChallenge challenge) throws IOException, InterruptedException {
        return false;
    }

    /**
     * A fixed token, e.g. a personal access token or a token you obtained elsewhere.
     */
    static McpAuth bearer(String token) {
        String checked = checkToken(token);
        return () -> checked;
    }

    /**
     * A token that is looked up per request, e.g. from a cache that a background job keeps fresh.
     */
    static McpAuth bearer(Supplier<String> tokenSupplier) {
        Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
        return () -> {
            String t = tokenSupplier.get();
            return t == null ? null : checkToken(t);
        };
    }

    /**
     * An API key sent in a header of your choice, e.g. {@code McpAuth.apiKey("X-API-Key", key)}.
     * For schemes with a prefix use the Authorization header: {@code McpAuth.apiKey("Authorization", "ApiKey " + key)}.
     */
    static McpAuth apiKey(String headerName, String value) {
        String checked = checkHeaderValue(value);
        return apiKey(headerName, () -> checked);
    }

    /**
     * Like {@link #apiKey(String, String)} but the key is looked up for every request (rotation, secrets manager).
     */
    static McpAuth apiKey(String headerName, Supplier<String> valueSupplier) {
        String name = checkHeaderName(headerName);
        Objects.requireNonNull(valueSupplier, "valueSupplier must not be null");
        return new McpAuth() {
            @Override
            public String accessToken() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                String v = valueSupplier.get();
                return v == null ? Map.of() : Map.of(name, checkHeaderValue(v));
            }
        };
    }

    /**
     * Only syntactically valid header names, and none the client sets itself (a duplicate could contradict the real one).
     */
    private static String checkHeaderName(String name) {
        Objects.requireNonNull(name, "headerName must not be null");
        if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new IllegalArgumentException("invalid header name");
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("mcp-") || RESERVED_HEADERS.contains(n))
            throw new IllegalArgumentException("header '" + name + "' is set by the client itself");
        return name;
    }

    private static String checkHeaderValue(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c >= 0x7f) throw new IllegalArgumentException("value contains illegal characters");
        }
        return value;
    }

    /**
     * Rejects blank tokens and anything that could break out of the header value (CR/LF, spaces, control chars).
     */
    private static String checkToken(String token) {
        Objects.requireNonNull(token, "token must not be null");
        if (token.isBlank()) throw new IllegalArgumentException("token must not be blank");
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= 0x20 || c >= 0x7f) throw new IllegalArgumentException("token contains illegal characters");
        }
        return token;
    }
}
