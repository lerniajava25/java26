package org.example.mcp.server.tools;

import org.example.mcp.server.McpCore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of executing an MCP tool.
 *
 * @param content           list of content items (e.g. text or embedded resources)
 * @param structuredContent optional structured JSON output
 * @param isError           whether the execution encountered an error
 */
public record ToolResult(
        List<Map<String, Object>> content,
        Map<String, Object> structuredContent,
        boolean isError
) {
    public ToolResult {
        content = content != null ? List.copyOf(content) : List.of();
    }

    /**
     * Creates a successful text result.
     */
    public static ToolResult text(String text) {
        return new ToolResult(List.of(Map.of("type", "text", "text", text)), null, false);
    }

    /**
     * Alias for {@link #text(String)}.
     */
    public static ToolResult success(String text) {
        return text(text);
    }

    /**
     * Creates a successful structured result. Serializes structured content to text
     * in the content block for compatibility with LLMs that only read text.
     */
    public static ToolResult structured(Map<String, Object> structured) {
        return new ToolResult(
                List.of(Map.of("type", "text", "text", McpCore.toJson(structured))),
                structured,
                false
        );
    }

    /**
     * Alias for {@link #structured(Map)}.
     */
    public static ToolResult success(Map<String, Object> structured) {
        return structured(structured);
    }

    /**
     * Creates a successful structured result with explicit text.
     */
    public static ToolResult structured(String text, Map<String, Object> structured) {
        return new ToolResult(
                List.of(Map.of("type", "text", "text", text)),
                structured,
                false
        );
    }

    /**
     * Creates an error result. In MCP, invalid tool input or business logic failures return isError: true
     * so that the model can inspect the message and self-correct.
     */
    public static ToolResult error(String message) {
        return new ToolResult(List.of(Map.of("type", "text", "text", message)), null, true);
    }

    /**
     * Converts to the JSON-RPC result map expected by the MCP protocol.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", content);
        if (structuredContent != null) {
            map.put("structuredContent", structuredContent);
        }
        map.put("isError", isError);
        return map;
    }
}
