package org.example.mcp.client.oauth;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovery as required by MCP 2026-07-28: RFC 9728 protected resource metadata, then RFC 8414 / OpenID Connect
 * authorization server metadata, tried in the order the spec prescribes.
 */
final class OAuthDiscovery {

    /**
     * RFC 9728 document: which authorization servers may issue tokens for this MCP server.
     */
    record ResourceMetadata(String resource, List<String> authorizationServers, List<String> scopesSupported) {
    }

    /**
     * The parts of the authorization server metadata (RFC 8414 / OIDC) this client uses.
     */
    record AuthServerMetadata(String issuer, String authorizationEndpoint, String tokenEndpoint,
                              String registrationEndpoint, List<String> codeChallengeMethods,
                              boolean issParameterSupported, boolean clientIdMetadataDocumentSupported,
                              List<String> scopesSupported, List<String> tokenEndpointAuthMethods) {
    }

    private final OAuthHttp http;
    private final Map<String, AuthServerMetadata> cache = new ConcurrentHashMap<>();

    OAuthDiscovery(OAuthHttp http) {
        this.http = http;
    }

    // ------------------------------------------------------------------ protected resource metadata

    /**
     * @param headerUrl the resource_metadata URL from the WWW-Authenticate challenge, or null
     * @param endpoint  the MCP endpoint URL
     */
    ResourceMetadata resourceMetadata(String headerUrl, URI endpoint) throws IOException, InterruptedException {
        OAuthHttp.Response r = null;
        if (headerUrl != null) {
            // The challenge names the document: use exactly that.
            URI uri;
            try {
                uri = URI.create(headerUrl);
            } catch (IllegalArgumentException e) {
                throw new OAuthException("Invalid resource_metadata URL in challenge");
            }
            r = http.get(uri, "Protected resource metadata URL");
            if (r.status() != 200)
                throw new OAuthException("Protected resource metadata request failed (HTTP " + r.status() + ")");
        } else {
            // No hint: well-known URIs, with the endpoint path inserted first, then the root.
            String origin = origin(endpoint);
            String path = trimTrailingSlash(endpoint.getPath());
            List<URI> candidates = new ArrayList<>();
            if (!path.isEmpty()) candidates.add(URI.create(origin + "/.well-known/oauth-protected-resource" + path));
            candidates.add(URI.create(origin + "/.well-known/oauth-protected-resource"));
            for (URI c : candidates) {
                OAuthHttp.Response attempt = http.get(c, "Protected resource metadata URL");
                if (attempt.status() == 200) {
                    r = attempt;
                    break;
                }
            }
            if (r == null)
                throw new OAuthException("The server publishes no protected resource metadata (tried " + candidates + ")");
        }

        Map<String, Object> m = http.parseObject(r.body(), "Protected resource metadata");
        String resource = m.get("resource") instanceof String s ? s : null;
        List<String> servers = OAuthHttp.stringList(m.get("authorization_servers"));
        if (resource == null) throw new OAuthException("Protected resource metadata has no 'resource'");
        if (servers == null || servers.isEmpty())
            throw new OAuthException("Protected resource metadata lists no authorization_servers");
        if (!identifiesEndpoint(resource, endpoint)) {
            // RFC 9728 section 3.3: metadata that describes some other resource must not be used.
            throw new OAuthException("Protected resource metadata is for '" + resource + "', not for " + endpoint);
        }
        return new ResourceMetadata(resource, servers, OAuthHttp.stringList(m.get("scopes_supported")));
    }

    /**
     * The metadata's resource must be the endpoint itself or a parent of it on the same origin (e.g. the site root).
     */
    static boolean identifiesEndpoint(String resource, URI endpoint) {
        URI r;
        try {
            r = URI.create(resource);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (r.getScheme() == null || r.getHost() == null || r.getFragment() != null || r.getRawQuery() != null)
            return false;
        if (!origin(r).equalsIgnoreCase(origin(endpoint))) return false;
        String rp = trimTrailingSlash(r.getPath());
        String ep = trimTrailingSlash(endpoint.getPath());
        return rp.isEmpty() || ep.equals(rp) || ep.startsWith(rp + "/");
    }

    // ------------------------------------------------------------------ authorization server metadata

    AuthServerMetadata authServerMetadata(String issuer) throws IOException, InterruptedException {
        AuthServerMetadata cached = cache.get(issuer);
        if (cached != null) return cached;

        URI iss;
        try {
            iss = URI.create(issuer);
        } catch (IllegalArgumentException e) {
            throw new OAuthException("Invalid authorization server URL: " + issuer);
        }
        http.requireSecure(iss, "Authorization server");
        if (iss.getRawQuery() != null || iss.getFragment() != null)
            throw new OAuthException("Invalid issuer (query or fragment): " + issuer);

        String origin = origin(iss);
        String path = trimTrailingSlash(iss.getPath());
        List<URI> candidates = new ArrayList<>();
        if (path.isEmpty()) {
            candidates.add(URI.create(origin + "/.well-known/oauth-authorization-server"));
            candidates.add(URI.create(origin + "/.well-known/openid-configuration"));
        } else {   // the three URLs in the order the spec requires
            candidates.add(URI.create(origin + "/.well-known/oauth-authorization-server" + path));
            candidates.add(URI.create(origin + "/.well-known/openid-configuration" + path));
            candidates.add(URI.create(origin + path + "/.well-known/openid-configuration"));
        }

        for (URI c : candidates) {
            OAuthHttp.Response r = http.get(c, "Authorization server metadata URL");
            if (r.status() != 200) continue;
            AuthServerMetadata md = parseAuthServerMetadata(r.body(), issuer);
            cache.put(issuer, md);
            return md;
        }
        throw new OAuthException("No authorization server metadata found for " + issuer + " (tried " + candidates + ")");
    }

    private AuthServerMetadata parseAuthServerMetadata(String body, String expectedIssuer) {
        Map<String, Object> m = http.parseObject(body, "Authorization server metadata");
        // RFC 8414 3.3: the issuer in the document MUST be identical to the one the URL was built from.
        if (!expectedIssuer.equals(m.get("issuer")))
            throw new OAuthException("Authorization server metadata names issuer '" + m.get("issuer")
                    + "' but was fetched for '" + expectedIssuer + "'; refusing to use it");
        String authEndpoint = m.get("authorization_endpoint") instanceof String s ? s : null;
        String tokenEndpoint = m.get("token_endpoint") instanceof String s ? s : null;
        if (authEndpoint == null || tokenEndpoint == null)
            throw new OAuthException("Authorization server metadata lacks authorization_endpoint or token_endpoint");
        return new AuthServerMetadata(expectedIssuer, authEndpoint, tokenEndpoint,
                m.get("registration_endpoint") instanceof String s ? s : null,
                OAuthHttp.stringList(m.get("code_challenge_methods_supported")),
                Boolean.TRUE.equals(m.get("authorization_response_iss_parameter_supported")),
                Boolean.TRUE.equals(m.get("client_id_metadata_document_supported")),
                OAuthHttp.stringList(m.get("scopes_supported")),
                OAuthHttp.stringList(m.get("token_endpoint_auth_methods_supported")));
    }

    // ------------------------------------------------------------------ helpers

    static String origin(URI u) {
        String scheme = u.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = u.getHost().toLowerCase(java.util.Locale.ROOT);
        int port = u.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    static String trimTrailingSlash(String path) {
        if (path == null) return "";
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') end--;
        return path.substring(0, end);
    }
}
