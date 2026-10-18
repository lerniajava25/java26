package org.example.mcp.client.oauth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where tokens (and client registrations obtained at runtime) are kept between calls.
 * <p>
 * The default {@link #inMemory()} store loses everything on restart, which means the user signs in again.
 * To persist across restarts implement this interface on top of something that encrypts at rest (OS keychain,
 * a secrets manager, an encrypted column). Never write refresh tokens to logs or plain files: the spec
 * requires secure token storage.
 */
public interface TokenStore {
    OAuthTokens load();

    void save(OAuthTokens tokens);

    void clear();

    /**
     * Client credentials from dynamic registration, keyed by authorization server issuer.
     */
    ClientCredentials loadClient(String issuer);

    void saveClient(ClientCredentials credentials);

    static TokenStore inMemory() {
        return new TokenStore() {
            private volatile OAuthTokens tokens;
            private final Map<String, ClientCredentials> clients = new ConcurrentHashMap<>();

            public OAuthTokens load() {
                return tokens;
            }

            public void save(OAuthTokens t) {
                tokens = t;
            }

            public void clear() {
                tokens = null;
            }

            public ClientCredentials loadClient(String issuer) {
                return clients.get(issuer);
            }

            public void saveClient(ClientCredentials c) {
                clients.put(c.issuer(), c);
            }
        };
    }
}
