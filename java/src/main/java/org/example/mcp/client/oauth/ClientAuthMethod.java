package org.example.mcp.client.oauth;

/**
 * How a confidential client (one with a client secret) authenticates at the token endpoint.
 */
public enum ClientAuthMethod {
    /**
     * Follow the authorization server metadata; if it says nothing, HTTP Basic (the RFC 8414 default).
     */
    AUTO,
    /**
     * client_id and client_secret in an Authorization: Basic header (RFC 6749 section 2.3.1).
     */
    BASIC,
    /**
     * client_id and client_secret as form fields in the request body. What GitHub's documentation describes.
     */
    POST
}
