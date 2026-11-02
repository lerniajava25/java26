package org.example.mcp.client.oauth;

/**
 * Something went wrong in the OAuth flow. {@link #error()} is the OAuth error code when there is one.
 */
public class OAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String error;

    public OAuthException(String message) {
        this(message, null, null);
    }

    public OAuthException(String message, String error) {
        this(message, error, null);
    }

    public OAuthException(String message, String error, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    /**
     * OAuth error code such as "invalid_grant" or "access_denied", or null.
     */
    public String error() {
        return error;
    }
}
