package org.example.mcp.client;

import org.example.mcp.server.McpCore;
import org.example.mcp.server.McpException;

/**
 * The server rejected the request for authorization reasons (HTTP 401, or 403 with a Bearer challenge).
 * Extends {@link McpException} so existing catch blocks keep working; {@link #challenge()} carries what
 * the server asked for (scopes, metadata URL, error code).
 */
public class McpAuthException extends McpException {
    private static final long serialVersionUID = 1L;
    private final transient AuthChallenge challenge;

    public McpAuthException(AuthChallenge challenge) {
        super(challenge.status(), McpCore.INVALID_REQUEST, describe(challenge));
        this.challenge = challenge;
    }

    /**
     * For failures while trying to satisfy a challenge (e.g. the user cancelled the sign-in).
     */
    public McpAuthException(AuthChallenge challenge, String message, Throwable cause) {
        super(challenge.status(), McpCore.INVALID_REQUEST, message);
        initCause(cause);
        this.challenge = challenge;
    }

    public AuthChallenge challenge() {
        return challenge;
    }

    private static String describe(AuthChallenge c) {
        StringBuilder sb = new StringBuilder("Authorization failed (HTTP ").append(c.status()).append(")");
        if (c.error() != null) sb.append(": ").append(c.error());
        if (c.errorDescription() != null) sb.append(" - ").append(c.errorDescription());
        if (c.scope() != null) sb.append(" [required scope: ").append(c.scope()).append("]");
        return sb.toString();
    }
}
