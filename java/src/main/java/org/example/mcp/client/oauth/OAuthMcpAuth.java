package org.example.mcp.client.oauth;

import org.example.mcp.client.AuthChallenge;
import org.example.mcp.client.McpAuth;
import org.example.mcp.client.McpAuthException;
import org.example.mcp.client.oauth.OAuthDiscovery.AuthServerMetadata;
import org.example.mcp.client.oauth.OAuthDiscovery.ResourceMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * OAuth 2.1 authorization for an MCP server, per MCP specification 2026-07-28, for applications where a user can
 * sign in with a browser: authorization code flow with PKCE (S256), RFC 8707 resource indicators, RFC 9728 /
 * RFC 8414 discovery, RFC 9207 issuer validation, refresh tokens and step-up authorization.
 * <p>
 * Plug it into the client: {@code new McpClient(url, auth)}. Nothing happens until the server answers 401: then
 * {@link #onChallenge} discovers the authorization server, registers/identifies the client, sends the user
 * through {@link AuthorizationHandler}, and the client retries the request with the new token.
 */
public final class OAuthMcpAuth implements McpAuth {

    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
    private static final int MAX_STEP_UP_ATTEMPTS = 2;

    private record PreRegistered(String issuer, String clientId, String clientSecret) {
    }

    private final URI mcpEndpoint;
    private final AuthorizationHandler handler;
    private final TokenStore store;
    private final OAuthHttp http;
    private final OAuthDiscovery discovery;
    private final String clientName;
    private final URI clientMetadataDocument;
    private final PreRegistered preRegistered;
    private final Function<List<String>, String> serverSelector;
    private final boolean requestRefreshToken;
    private final String initialScope;
    private final ClientAuthMethod clientAuthMethod;

    private final ReentrantLock lock = new ReentrantLock();
    private final ThreadLocal<String> tokenUsedByThisThread = new ThreadLocal<>();
    private final Set<String> requestedScopes = new LinkedHashSet<>();
    private final Map<String, Integer> stepUpAttempts = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    private OAuthMcpAuth(Builder b) {
        this.mcpEndpoint = b.mcpEndpoint;
        this.handler = b.handler;
        this.store = b.store;
        this.http = new OAuthHttp(b.httpClient, b.allowLoopbackHttp);
        this.discovery = new OAuthDiscovery(http);
        this.clientName = b.clientName;
        this.clientMetadataDocument = b.clientMetadataDocument;
        this.preRegistered = b.preRegistered;
        this.serverSelector = b.serverSelector;
        this.requestRefreshToken = b.requestRefreshToken;
        this.initialScope = b.initialScope;
        this.clientAuthMethod = b.clientAuthMethod;
    }

    public static Builder builder(URI mcpEndpoint) {
        return new Builder(mcpEndpoint);
    }

    public static Builder builder(String mcpEndpoint) {
        return new Builder(URI.create(mcpEndpoint));
    }

    // ================================================================== McpAuth

    /**
     * Returns a valid access token, refreshing it silently if needed. Never opens a browser.
     */
    @Override
    public String accessToken() throws IOException, InterruptedException {
        OAuthTokens t = store.load();
        if (t == null) {
            tokenUsedByThisThread.set(null);
            return null;
        }
        if (t.isValid(Instant.now(), EXPIRY_SKEW)) {
            tokenUsedByThisThread.set(t.accessToken());
            return t.accessToken();
        }

        lock.lock();
        try {
            t = store.load();                                    // somebody may have refreshed while we waited
            if (t == null) {
                tokenUsedByThisThread.set(null);
                return null;
            }
            if (t.isValid(Instant.now(), EXPIRY_SKEW)) {
                tokenUsedByThisThread.set(t.accessToken());
                return t.accessToken();
            }
            if (t.refreshToken() != null) {
                try {
                    t = refresh(t);
                    store.save(t);
                    tokenUsedByThisThread.set(t.accessToken());
                    return t.accessToken();
                } catch (OAuthException e) {
                    if ("invalid_grant".equals(e.error()) || "invalid_client".equals(e.error())) store.clear();
                }
            } else {
                store.clear();
            }
            tokenUsedByThisThread.set(null);
            return null;   // the request goes out without a token; the 401 leads to onChallenge -> interactive sign-in
        } finally {
            lock.unlock();
        }
    }

    /**
     * 401 (sign-in needed) or 403 insufficient_scope (step-up). Returns true if the request should be retried.
     */
    @Override
    public boolean onChallenge(AuthChallenge c) throws IOException, InterruptedException {
        boolean stepUp = "insufficient_scope".equals(c.error());
        if (c.status() == 403 && !stepUp) return false;

        lock.lock();
        try {
            OAuthTokens cur = store.load();
            String used = tokenUsedByThisThread.get();

            // Another thread signed in / refreshed while we waited for the lock: just retry with the new token.
            if (!stepUp && cur != null && !Objects.equals(used, cur.accessToken()) && cur.isValid(Instant.now(), EXPIRY_SKEW))
                return true;

            // A refresh token is cheaper than bothering the user.
            if (!stepUp && cur != null && cur.refreshToken() != null) {
                try {
                    store.save(refresh(cur));
                    return true;
                } catch (OAuthException e) {
                    if ("invalid_grant".equals(e.error()) || "invalid_client".equals(e.error())) store.clear();
                    // otherwise: fall through to the interactive flow
                }
            }

            return authorizeInteractively(c, stepUp);
        } catch (OAuthException e) {
            throw new McpAuthException(c, "OAuth authorization failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forgets tokens and step-up history (e.g. on logout).
     */
    public void signOut() {
        lock.lock();
        try {
            store.clear();
            requestedScopes.clear();
            stepUpAttempts.clear();
        } finally {
            lock.unlock();
        }
    }

    // ================================================================== interactive flow

    private boolean authorizeInteractively(AuthChallenge c, boolean stepUp) throws IOException, InterruptedException {
        // 1. Which authorization server? (protected resource metadata, RFC 9728)
        ResourceMetadata prm = discovery.resourceMetadata(c.resourceMetadata(), mcpEndpoint);
        String issuer = serverSelector.apply(prm.authorizationServers());
        if (issuer == null || !prm.authorizationServers().contains(issuer))
            throw new OAuthException("No acceptable authorization server among " + prm.authorizationServers());

        // 2. What can it do? (RFC 8414 / OpenID Connect discovery)
        AuthServerMetadata as = discovery.authServerMetadata(issuer);
        // PKCE is mandatory, and the only way to know the server supports it is its metadata.
        if (as.codeChallengeMethods() == null || !as.codeChallengeMethods().contains("S256"))
            throw new OAuthException("The authorization server does not advertise PKCE S256 (code_challenge_methods_supported); refusing to continue");
        http.requireSecure(URI.create(as.authorizationEndpoint()), "authorization_endpoint");
        http.requireSecure(URI.create(as.tokenEndpoint()), "token_endpoint");

        // 3. Scopes: the challenge wins, then initialScope(...) if configured, then scopes_supported; always keep what we asked for before (step-up).
        Set<String> scopes = new LinkedHashSet<>();
        if (c.scope() != null) scopes.addAll(splitScopes(c.scope()));
        else if (initialScope != null)
            scopes.addAll(splitScopes(initialScope));   // chosen by the application developer
        else if (prm.scopesSupported() != null) scopes.addAll(prm.scopesSupported());
        scopes.addAll(requestedScopes);
        if (requestRefreshToken && as.scopesSupported() != null && as.scopesSupported().contains("offline_access"))
            scopes.add("offline_access");

        if (stepUp) {   // avoid asking the user again and again for the same thing
            String key = String.join(" ", new TreeSet<>(scopes));
            if (stepUpAttempts.merge(key, 1, Integer::sum) > MAX_STEP_UP_ATTEMPTS) return false;
        }

        // 4. Who are we? (pre-registered, Client ID Metadata Document, or dynamic registration)
        URI redirectUri = handler.redirectUri();
        ClientCredentials client = resolveClient(as, redirectUri);

        // 5. Authorization request with PKCE, state and the resource indicator.
        String resource = prm.resource();
        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(32);
        Map<String, String> q = new LinkedHashMap<>();
        q.put("response_type", "code");
        q.put("client_id", client.clientId());
        q.put("redirect_uri", redirectUri.toString());
        q.put("state", state);
        q.put("code_challenge", s256(verifier));
        q.put("code_challenge_method", "S256");
        q.put("resource", resource);
        if (!scopes.isEmpty()) q.put("scope", String.join(" ", scopes));
        URI authorizationUri = withQuery(as.authorizationEndpoint(), q);

        // The issuer we expect in the response is the one from the VALIDATED metadata, recorded before redirecting.
        Map<String, String> response = handler.authorize(authorizationUri, state);

        // 6. Validate the response BEFORE sending the code anywhere (RFC 9207, state).
        validateAuthorizationResponse(response, state, as);

        // 7. Exchange the code.
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", response.get("code"));
        form.put("redirect_uri", redirectUri.toString());
        form.put("code_verifier", verifier);
        form.put("resource", resource);
        OAuthTokens tokens = requestTokens(as, client, form, resource, String.join(" ", scopes), null);
        store.save(tokens);
        requestedScopes.addAll(scopes);
        return true;
    }

    /**
     * Spec section "Authorization Response Validation" + RFC 9207 section 2.4.
     */
    private static void validateAuthorizationResponse(Map<String, String> p, String expectedState, AuthServerMetadata as) {
        String iss = p.get("iss");
        if (as.issParameterSupported() && iss == null)
            throw new OAuthException("Authorization response lacks the 'iss' parameter the server promised; rejected");
        // Exact string comparison, no normalization. Done first: on mismatch the error fields must not be acted on or shown.
        if (iss != null && !iss.equals(as.issuer()))
            throw new OAuthException("Authorization response came from a different issuer than expected; rejected");
        String state = p.get("state");
        if (state == null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), expectedState.getBytes(StandardCharsets.UTF_8)))
            throw new OAuthException("Authorization response has a missing or wrong 'state'; rejected");
        if (p.get("error") != null)
            throw new OAuthException("The user or the authorization server denied the request: " + shorten(p.get("error"))
                    + (p.get("error_description") != null ? " (" + shorten(p.get("error_description")) + ")" : ""), p.get("error"));
        if (p.get("code") == null || p.get("code").isEmpty())
            throw new OAuthException("Authorization response contains no code");
    }

    // ================================================================== client registration

    /**
     * Priority from the spec: pre-registered, then Client ID Metadata Document, then dynamic registration.
     */
    private ClientCredentials resolveClient(AuthServerMetadata as, URI redirectUri) throws IOException, InterruptedException {
        if (preRegistered != null) {
            if (!preRegistered.issuer().equals(as.issuer()))
                throw new OAuthException("The configured client credentials belong to authorization server " + preRegistered.issuer()
                        + ", but the MCP server now uses " + as.issuer() + "; not using them there");
            return new ClientCredentials(preRegistered.clientId(), preRegistered.clientSecret(), as.issuer(), redirectUri.toString());
        }
        if (clientMetadataDocument != null && as.clientIdMetadataDocumentSupported())
            return new ClientCredentials(clientMetadataDocument.toString(), null, as.issuer(), redirectUri.toString());

        ClientCredentials stored = store.loadClient(as.issuer());
        if (stored != null && redirectUri.toString().equals(stored.redirectUri())) return stored;

        if (as.registrationEndpoint() != null) return register(as, redirectUri);

        throw new OAuthException("Cannot obtain a client_id from " + as.issuer() + ": it supports neither Client ID Metadata "
                + "Documents (or none is configured) nor dynamic registration. Configure preRegistered(...).");
    }

    /**
     * Dynamic Client Registration (RFC 7591). Deprecated in this spec version, kept for servers without CIMD.
     */
    private ClientCredentials register(AuthServerMetadata as, URI redirectUri) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_name", clientName);
        body.put("redirect_uris", List.of(redirectUri.toString()));
        body.put("grant_types", List.of("authorization_code", "refresh_token"));
        body.put("response_types", List.of("code"));
        body.put("token_endpoint_auth_method", "none");
        // Required by the spec: omitting it defaults to "web" under OIDC and conflicts with localhost redirects.
        body.put("application_type", OAuthHttp.isLoopback(redirectUri.getHost()) ? "native" : "web");

        OAuthHttp.Response r = http.postJson(URI.create(as.registrationEndpoint()), body, "registration_endpoint");
        Map<String, Object> m = http.parseObject(r.body(), "Registration response");
        if ((r.status() != 201 && r.status() != 200) || !(m.get("client_id") instanceof String id) || id.isEmpty())
            throw new OAuthException("Client registration was rejected (HTTP " + r.status() + "): "
                    + (m.get("error") != null ? shorten(String.valueOf(m.get("error"))) : "no client_id returned"), asString(m.get("error")));
        ClientCredentials creds = new ClientCredentials(id, asString(m.get("client_secret")), as.issuer(), redirectUri.toString());
        store.saveClient(creds);
        return creds;
    }

    private ClientCredentials clientForRefresh(AuthServerMetadata as, OAuthTokens t) {
        if (preRegistered != null && preRegistered.issuer().equals(as.issuer()))
            return new ClientCredentials(preRegistered.clientId(), preRegistered.clientSecret(), as.issuer(), null);
        if (clientMetadataDocument != null && clientMetadataDocument.toString().equals(t.clientId()))
            return new ClientCredentials(t.clientId(), null, as.issuer(), null);
        ClientCredentials stored = store.loadClient(as.issuer());
        if (stored != null && stored.clientId().equals(t.clientId())) return stored;
        throw new OAuthException("No client credentials available for " + as.issuer() + " to refresh the token", "invalid_client");
    }

    // ================================================================== token endpoint

    private OAuthTokens refresh(OAuthTokens old) throws IOException, InterruptedException {
        AuthServerMetadata as = discovery.authServerMetadata(old.issuer());
        ClientCredentials client = clientForRefresh(as, old);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", old.refreshToken());
        form.put("resource", old.resource());
        return requestTokens(as, client, form, old.resource(), old.scope(), old);
    }

    private OAuthTokens requestTokens(AuthServerMetadata as, ClientCredentials client, Map<String, String> form,
                                      String resource, String requestedScope, OAuthTokens previous) throws IOException, InterruptedException {
        String authHeader = null;
        if (client.clientSecret() != null) {
            List<String> methods = as.tokenEndpointAuthMethods();
            boolean post = switch (clientAuthMethod) {
                case POST -> true;
                case BASIC -> false;
                case AUTO ->
                        methods != null && methods.contains("client_secret_post") && !methods.contains("client_secret_basic");
            };
            if (post) {
                form.put("client_id", client.clientId());
                form.put("client_secret", client.clientSecret());
            } else authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (OAuthHttp.enc(client.clientId()) + ":" + OAuthHttp.enc(client.clientSecret())).getBytes(StandardCharsets.UTF_8));
        } else {
            form.put("client_id", client.clientId());   // public client
        }

        OAuthHttp.Response r = http.postForm(URI.create(as.tokenEndpoint()), form, authHeader, "token_endpoint");
        Map<String, Object> m = http.parseObject(r.body(), "Token response");
        // Some servers (GitHub) report errors with HTTP 200 and an "error" field, so look at the body first.
        String err = asString(m.get("error"));
        if (err != null || r.status() != 200) {
            throw new OAuthException("Token request failed (HTTP " + r.status() + (err != null ? ", " + shorten(err) : "") + ")", normalizeError(err));
        }
        if (!(m.get("access_token") instanceof String access) || access.isEmpty())
            throw new OAuthException("Token response contains no access_token");
        if (!"bearer".equalsIgnoreCase(asString(m.get("token_type"))))
            throw new OAuthException("Unsupported token_type: " + shorten(String.valueOf(m.get("token_type"))));

        Instant expiresAt = m.get("expires_in") instanceof Number n ? Instant.now().plusSeconds(n.longValue()) : null;
        // Refresh tokens may rotate (public clients MUST get rotation): use the new one if given, else keep the old.
        String refreshToken = asString(m.get("refresh_token"));
        if (refreshToken == null && previous != null) refreshToken = previous.refreshToken();
        String scope = asString(m.get("scope"));
        if (scope == null) scope = requestedScope;
        return new OAuthTokens(access, refreshToken, expiresAt, scope, as.issuer(), resource, client.clientId());
    }

    // ================================================================== helpers

    private String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 is guaranteed by the platform
        }
    }

    static URI withQuery(String endpoint, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(endpoint).append(endpoint.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(OAuthHttp.enc(e.getKey())).append('=').append(OAuthHttp.enc(e.getValue()));
        }
        return URI.create(sb.toString());
    }

    static List<String> splitScopes(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.trim().split("\\s+")) if (!part.isEmpty()) out.add(part);
        return out;
    }

    /**
     * Maps GitHub's non-standard error codes to the standard ones the rest of the code reacts to.
     */
    static String normalizeError(String error) {
        if (error == null) return null;
        return switch (error) {
            case "bad_refresh_token", "bad_verification_code" -> "invalid_grant";
            case "incorrect_client_credentials" -> "invalid_client";
            default -> error;
        };
    }

    private static String asString(Object o) {
        return o instanceof String s ? s : null;
    }

    private static String shorten(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    // ================================================================== builder

    public static final class Builder {
        private final URI mcpEndpoint;
        private AuthorizationHandler handler;
        private TokenStore store = TokenStore.inMemory();
        private HttpClient httpClient = OAuthHttp.defaultClient();
        private String clientName = "java-mcp-client";
        private URI clientMetadataDocument;
        private PreRegistered preRegistered;
        private Function<List<String>, String> serverSelector = servers -> servers.get(0);
        private boolean allowLoopbackHttp = false;
        private boolean requestRefreshToken = true;
        private String initialScope;
        private ClientAuthMethod clientAuthMethod = ClientAuthMethod.AUTO;

        private Builder(URI mcpEndpoint) {
            this.mcpEndpoint = Objects.requireNonNull(mcpEndpoint, "mcpEndpoint");
            if (mcpEndpoint.getScheme() == null || mcpEndpoint.getHost() == null)
                throw new IllegalArgumentException("mcpEndpoint must be an absolute URL");
        }

        /**
         * How the user is sent to the browser and the redirect received. Default: {@link LoopbackAuthorizationHandler} on a random port.
         */
        public Builder authorizationHandler(AuthorizationHandler h) {
            this.handler = Objects.requireNonNull(h);
            return this;
        }

        /**
         * Name shown to the user on the consent screen (used for dynamic registration).
         */
        public Builder clientName(String name) {
            this.clientName = Objects.requireNonNull(name);
            return this;
        }

        /**
         * Use a Client ID Metadata Document: {@code url} is an https URL (with a path) where YOU host a JSON document
         * with client_id (= that URL), client_name and redirect_uris (including the handler's redirect URI).
         * Used when the authorization server advertises client_id_metadata_document_supported.
         */
        public Builder clientIdMetadataDocument(URI url) {
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getPath() == null || url.getPath().length() < 2)
                throw new IllegalArgumentException("client metadata document URL must be https and have a path");
            this.clientMetadataDocument = url;
            return this;
        }

        /**
         * Credentials you registered yourself with the authorization server {@code issuer}. Highest priority.
         */
        public Builder preRegistered(String issuer, String clientId, String clientSecretOrNull) {
            this.preRegistered = new PreRegistered(Objects.requireNonNull(issuer), Objects.requireNonNull(clientId), clientSecretOrNull);
            return this;
        }

        /**
         * Where tokens are kept. Default: in memory (the user signs in again after a restart).
         */
        public Builder tokenStore(TokenStore s) {
            this.store = Objects.requireNonNull(s);
            return this;
        }

        /**
         * Chooses among several authorization servers listed by the MCP server. Default: the first.
         */
        public Builder authorizationServerSelector(Function<List<String>, String> f) {
            this.serverSelector = Objects.requireNonNull(f);
            return this;
        }

        /**
         * Scopes to request when the server's 401 challenge names none, instead of everything in the server's
         * scopes_supported. A pre-registered app knows what it needs, e.g. {@code "repo read:org"}.
         */
        public Builder initialScope(String scopes) {
            this.initialScope = scopes;
            return this;
        }

        /**
         * How the client secret is sent to the token endpoint. Default {@link ClientAuthMethod#AUTO}; GitHub: {@link ClientAuthMethod#POST}.
         */
        public Builder clientAuthMethod(ClientAuthMethod m) {
            this.clientAuthMethod = Objects.requireNonNull(m);
            return this;
        }

        /**
         * Ask for offline_access (refresh tokens) when the authorization server offers it. Default true.
         */
        public Builder requestRefreshToken(boolean b) {
            this.requestRefreshToken = b;
            return this;
        }

        public Builder httpClient(HttpClient c) {
            this.httpClient = Objects.requireNonNull(c);
            return this;
        }

        /**
         * DEVELOPMENT ONLY: accept http://localhost / 127.0.0.1 for authorization server URLs. The spec requires https.
         */
        public Builder allowInsecureLoopbackHttp(boolean b) {
            this.allowLoopbackHttp = b;
            return this;
        }

        public OAuthMcpAuth build() throws IOException {
            if (handler == null) handler = new LoopbackAuthorizationHandler();
            OAuthMcpAuth oAuthMcpAuth = new OAuthMcpAuth(this);
            return oAuthMcpAuth;
        }
    }
}
