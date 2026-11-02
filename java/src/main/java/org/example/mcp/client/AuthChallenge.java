package org.example.mcp.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed Bearer challenge from a 401/403 response's WWW-Authenticate header
 * (RFC 6750 section 3, RFC 9728 section 5.1). Everything an OAuth flow needs to get started:
 * where the protected resource metadata lives, and which scopes the server asks for.
 *
 * @param status           HTTP status of the response (401 or 403)
 * @param bearer           true if the response actually carried a Bearer challenge
 * @param resourceMetadata URL of the RFC 9728 protected resource metadata document, or null
 * @param scope            space-separated scopes the server requires for this operation, or null
 * @param error            e.g. "invalid_token" or "insufficient_scope", or null
 * @param errorDescription human readable text, or null
 */
public record AuthChallenge(int status, boolean bearer, String resourceMetadata, String scope,
                            String error, String errorDescription) {

    /**
     * Parses all WWW-Authenticate header values of a response. Unknown schemes are ignored.
     */
    public static AuthChallenge from(int status, List<String> headerValues) {
        Map<String, String> params = null;
        for (String header : headerValues) {
            Map<String, String> p = parseBearerParams(header);
            if (p != null) {
                params = p;
                break;
            }
        }
        if (params == null) return new AuthChallenge(status, false, null, null, null, null);
        return new AuthChallenge(status, true, params.get("resource_metadata"), params.get("scope"),
                params.get("error"), params.get("error_description"));
    }

    /**
     * Returns the auth-params of the Bearer challenge in this header value, or null if there is none.
     */
    private static Map<String, String> parseBearerParams(String s) {
        Map<String, String> bearerParams = null;
        boolean inBearer = false;
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
            int start = i;
            while (i < n && s.charAt(i) != ',' && s.charAt(i) != '=' && s.charAt(i) != '"'
                    && !Character.isWhitespace(s.charAt(i))) i++;
            if (start == i) {
                i++;
                continue;
            }                       // stray character
            String token = s.substring(start, i);

            int j = i;
            while (j < n && Character.isWhitespace(s.charAt(j))) j++;
            if (j < n && s.charAt(j) == '=') {                       // name=value  -> an auth-param
                i = j + 1;
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                StringBuilder value = new StringBuilder();
                if (i < n && s.charAt(i) == '"') {                   // quoted-string, with \-escapes
                    i++;
                    while (i < n && s.charAt(i) != '"') {
                        if (s.charAt(i) == '\\' && i + 1 < n) i++;
                        value.append(s.charAt(i++));
                    }
                    i++;                                             // closing quote
                } else {                                             // bare token
                    while (i < n && s.charAt(i) != ',' && !Character.isWhitespace(s.charAt(i)))
                        value.append(s.charAt(i++));
                }
                if (inBearer) bearerParams.put(token.toLowerCase(java.util.Locale.ROOT), value.toString());
            } else {                                                 // no '=' -> this token is a new scheme
                inBearer = token.equalsIgnoreCase("Bearer");
                if (inBearer && bearerParams == null) bearerParams = new HashMap<>();
            }
        }
        return bearerParams;
    }
}
