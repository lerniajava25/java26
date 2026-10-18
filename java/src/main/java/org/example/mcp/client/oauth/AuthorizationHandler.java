package org.example.mcp.client.oauth;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * The "user in front of a browser" part of the flow. Your application decides HOW the user gets to the sign-in
 * page and HOW the redirect comes back:
 * <ul>
 *   <li>desktop app: {@link LoopbackAuthorizationHandler} (opens the system browser, listens on 127.0.0.1)</li>
 *   <li>web app: redirect the user's browser to the URI, and have your own HTTPS callback route hand the query
 *       parameters back to the waiting {@link #authorize} call</li>
 * </ul>
 */
public interface AuthorizationHandler {

    /**
     * The redirect URI this handler receives the response on. It must be registered with the authorization server
     * and is matched EXACTLY: with a pre-registered client or a Client ID Metadata Document the port must be fixed.
     * The spec requires it to be either localhost or https.
     */
    URI redirectUri();

    /**
     * Sends the user to {@code authorizationUri}, blocks until the authorization server redirects back to
     * {@link #redirectUri()}, and returns the query parameters of that redirect (code, state, iss, error, ...).
     * <p>
     * Implementations SHOULD ignore redirects whose {@code state} differs from {@code state} (they are not
     * answers to this request) and keep waiting.
     */
    Map<String, String> authorize(URI authorizationUri, String state) throws IOException, InterruptedException;
}
