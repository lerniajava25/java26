package org.example.mcp.client.oauth;

/**
 * The OAuth client identity used with ONE specific authorization server (the spec requires credentials to be
 * keyed by issuer and never reused with another one).
 *
 * @param clientId     the client_id (for Client ID Metadata Documents: the HTTPS URL of the document)
 * @param clientSecret null for public clients
 * @param issuer       the authorization server that issued/accepts these credentials
 * @param redirectUri  the redirect URI registered with it (null if not applicable)
 */
public record ClientCredentials(String clientId, String clientSecret, String issuer, String redirectUri) {
    @Override
    public String toString() {
        return "ClientCredentials[clientId=" + clientId + ", clientSecret=" + (clientSecret == null ? "none" : "***")
                + ", issuer=" + issuer + ", redirectUri=" + redirectUri + "]";
    }
}
