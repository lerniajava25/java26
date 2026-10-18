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
 * MCP tool that resolves a library/package name to a Context7-compatible library ID.
 * <p>
 * <b>You MUST call this tool before {@code query-docs}</b> to obtain a valid Context7-compatible
 * library ID, UNLESS the user explicitly provides a library ID in the format {@code /org/project}
 * or {@code /org/project/version}.
 * <p>
 * Requires the {@code CONTEXT7_API_KEY} environment variable to be set.
 */
public class Context7ResolveLibraryTool implements McpTool {

    public static final String NAME = "resolve-library-id";

    private static final String BASE_URL = "https://context7.com/api/v2/libs/search";

    public static final Map<String, Object> DEFINITION = obj(
            "name", NAME,
            "title", "Resolve Context7 Library ID",
            "description", """
                    Resolves a package/product name to a Context7-compatible library ID and returns \
                    matching libraries. You MUST call this function before 'query-docs' to obtain a \
                    valid Context7-compatible library ID UNLESS the user explicitly provides a library \
                    ID in the format '/org/project' or '/org/project/version' in their query.
                    
                    Selection Process:
                    1. Analyze the query to understand what library/package the user is looking for.
                    2. Return the most relevant match based on:
                       - Name similarity to the query (exact matches prioritized)
                       - Description relevance to the query's intent
                       - Documentation coverage (prioritize libraries with higher snippet counts)
                       - trustScore (0-10, higher is better; prefer >= 7)
                       - benchmarkScore: quality indicator (100 is the highest score)
                    
                    Response Format:
                    - Return the selected library ID in a clearly marked section.
                    - Provide a brief explanation for why this library was chosen.
                    - If multiple good matches exist, acknowledge this but proceed with the most relevant one.
                    - If no good matches exist, clearly state this and suggest query refinements.
                    - For ambiguous queries, request clarification before proceeding.
                    
                    IMPORTANT: Do not call this tool more than 3 times per question. If you cannot \
                    find what you need after 3 calls, use the best result you have.\
                    """,
            "inputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "libraryName", obj(
                                    "type", "string",
                                    "description", "The library or package name to search for, e.g. 'react', 'spring-boot', 'jackson'."),
                            "query", obj(
                                    "type", "string",
                                    "description", "The user's original question or intent, used for relevance ranking, e.g. 'How to use hooks in React'.")),
                    "required", List.of("libraryName", "query"),
                    "additionalProperties", false),
            "outputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "results", obj(
                                    "type", "array",
                                    "description", "List of matching libraries ordered by relevance.",
                                    "items", obj(
                                            "type", "object",
                                            "properties", obj(
                                                    "id", obj("type", "string", "description", "Context7-compatible library ID, e.g. /vercel/next.js"),
                                                    "title", obj("type", "string"),
                                                    "description", obj("type", "string"),
                                                    "stars", obj("type", "integer"),
                                                    "trustScore", obj("type", "number", "description", "Reputation score 0-10"),
                                                    "benchmarkScore", obj("type", "number", "description", "Quality score 0-100"),
                                                    "totalSnippets", obj("type", "integer"),
                                                    "totalTokens", obj("type", "integer"),
                                                    "versions", obj("type", "array", "items", obj("type", "string"))))),
                            "searchFilterApplied", obj("type", "boolean")),
                    "required", List.of("results")),
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

        Object libraryNameArg = arguments.get("libraryName");
        Object queryArg = arguments.get("query");

        if (!(libraryNameArg instanceof String libraryName) || libraryName.isBlank()) {
            return ToolResult.error("'libraryName' is required and must be a non-blank string.");
        }
        if (!(queryArg instanceof String query) || query.isBlank()) {
            return ToolResult.error("'query' is required and must be a non-blank string.");
        }

        String url = BASE_URL
                + "?libraryName=" + URLEncoder.encode(libraryName, StandardCharsets.UTF_8)
                + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                return ToolResult.error("Context7 API returned 401 Unauthorized. Check that CONTEXT7_API_KEY is valid.");
            }
            if (response.statusCode() != 200) {
                return ToolResult.error("Context7 API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            // Return the raw JSON body as text so the LLM can read all fields directly
            return ToolResult.text(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Request was interrupted: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to call Context7 search API: " + e.getMessage());
        }
    }
}
