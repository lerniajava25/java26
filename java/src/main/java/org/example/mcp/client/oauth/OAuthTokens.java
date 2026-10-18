package org.example.mcp.client.oauth;

import java.time.Duration;
import java.time.Instant;

/**
 * Tokens for one (authorization server, MCP resource) pair. Treat as a secret: the spec requires secure storage.
 *
 * @param expiresAt null if the server did not say when the access token expires
 */
public record OAuthTokens(String accessToken, String refreshToken, Instant expiresAt, String scope,
                          String issuer, String resource, String clientId) {

    /**
     * True if the access token is still usable for at least {@code skew} more.
     */
    public boolean isValid(Instant now, Duration skew) {
        return expiresAt == null || now.plus(skew).isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "OAuthTokens[issuer=" + issuer + ", resource=" + resource + ", scope=" + scope
                + ", expiresAt=" + expiresAt + ", tokens=***]";   // never print the tokens
    }
}
