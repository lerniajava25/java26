package org.example.mcp.server.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.example.mcp.server.McpCore.obj;

/**
 * MCP tool that fetches up-to-date documentation and code examples from Context7.
 * <p>
 * Requires calling {@code resolve-library-id} first to obtain the exact Context7-compatible
 * library ID, UNLESS the user explicitly provides a library ID in the format {@code /org/project}
 * or {@code /org/project/version}.
 * <p>
 * Requires the {@code CONTEXT7_API_KEY} environment variable to be set.
 */
public class Context7QueryDocsTool implements McpTool {

    public static final String NAME = "query-docs";

    private static final String BASE_URL = "https://context7.com/api/v2/context";

    /**
     * Safeguard: cap documentation response size forwarded to the LLM (~200 KB).
     */
    private static final int MAX_RESPONSE_CHARS = 200_000;

    public static final Map<String, Object> DEFINITION = obj(
            "name", NAME,
            "title", "Query Context7 Documentation",
            "description", """
                    Retrieves and queries up-to-date documentation and code examples from Context7 \
                    for any programming library or framework.
                    
                    You MUST call 'resolve-library-id' first to obtain the exact Context7-compatible \
                    library ID required to use this tool, UNLESS the user explicitly provides a \
                    library ID in the format '/org/project' or '/org/project/version' in their query.\
                    """,
            "inputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "libraryId", obj(
                                    "type", "string",
                                    "description", "Context7-compatible library ID, e.g. /vercel/next.js or /vercel/next.js/v14. Obtain from 'resolve-library-id'."),
                            "query", obj(
                                    "type", "string",
                                    "description", "Natural language question or topic to retrieve documentation for, e.g. 'How to configure middleware'."),
                            "language", obj(
                                    "type", "string",
                                    "description", "Optional preferred programming language for code snippets, e.g. 'Java', 'TypeScript'. Soft preference only.")),
                    "required", List.of("libraryId", "query"),
                    "additionalProperties", false),
            "outputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "documentation", obj(
                                    "type", "string",
                                    "description", "Formatted documentation text with code snippets relevant to the query.")),
                    "required", List.of("documentation")),
            "annotations", obj("readOnlyHint", true, "idempotentHint", true, "openWorldHint", true));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String apiKey = System.getenv("CONTEXT7_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return ToolResult.error(
                    "CONTEXT7_API_KEY environment variable is not set. " +
                            "Please obtain an API key from https://context7.com and set CONTEXT7_API_KEY.");
        }

        Object libraryIdArg = arguments.get("libraryId");
        Object queryArg = arguments.get("query");
        Object languageArg = arguments.get("language");

        if (!(libraryIdArg instanceof String libraryId) || libraryId.isBlank()) {
            return ToolResult.error("'libraryId' is required and must be a non-blank string in the format /org/project.");
        }
        if (!(queryArg instanceof String query) || query.isBlank()) {
            return ToolResult.error("'query' is required and must be a non-blank string.");
        }

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?libraryId=").append(URLEncoder.encode(libraryId, StandardCharsets.UTF_8))
                .append("&query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&type=txt");  // Plain text is simpler and more LLM-friendly than JSON

        if (languageArg instanceof String language && !language.isBlank()) {
            url.append("&language=").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/plain")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                return ToolResult.error("Context7 API returned 401 Unauthorized. Check that CONTEXT7_API_KEY is valid.");
            }
            if (response.statusCode() == 404) {
                return ToolResult.error(
                        "Library ID '" + libraryId + "' was not found on Context7. " +
                                "Use 'resolve-library-id' to find the correct ID.");
            }
            if (response.statusCode() != 200) {
                return ToolResult.error("Context7 API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return ToolResult.text(
                        "Context7 returned no documentation for library '" + libraryId + "' with query: " + query +
                                ". Try a different query or check the library ID.");
            }

            // Truncate very large responses to avoid overwhelming the LLM
            if (body.length() > MAX_RESPONSE_CHARS) {
                body = body.substring(0, MAX_RESPONSE_CHARS)
                        + "\n\n[... documentation truncated at " + MAX_RESPONSE_CHARS + " characters ...]";
            }

            return ToolResult.text(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Request was interrupted: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to call Context7 documentation API: " + e.getMessage());
        }
    }
}
