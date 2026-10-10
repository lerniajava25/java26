package org.example.mcp.tools;

import java.util.Map;

/**
 * Metadata descriptor for a tool exposed by an MCP server.
 */
public record McpToolInfo(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations
) {
    @SuppressWarnings("unchecked")
    public static McpToolInfo fromMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        String title = (String) map.get("title");
        String description = (String) map.get("description");
        Map<String, Object> inputSchema = map.get("inputSchema") instanceof Map
                ? (Map<String, Object>) map.get("inputSchema") : Map.of();
        Map<String, Object> outputSchema = map.get("outputSchema") instanceof Map
                ? (Map<String, Object>) map.get("outputSchema") : null;
        Map<String, Object> annotations = map.get("annotations") instanceof Map
                ? (Map<String, Object>) map.get("annotations") : null;
        return new McpToolInfo(name, title, description, inputSchema, outputSchema, annotations);
    }

    public String effectiveDescription() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        if (title != null && !title.isBlank()) {
            return title;
        }
        return name;
    }
}
