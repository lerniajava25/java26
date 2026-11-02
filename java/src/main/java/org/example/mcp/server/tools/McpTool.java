package org.example.mcp.server.tools;

import java.util.Map;

/**
 * Interface representing an MCP tool (protocol version 2026-07-28).
 * <p>
 * Implementations define tool metadata (name, description, input and output schemas)
 * and provide the execution logic.
 */
public interface McpTool {

    /**
     * The unique name of the tool.
     * By default, extracts the "name" property from {@link #definition()}.
     */
    default String name() {
        Object n = definition().get("name");
        if (n instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalStateException("Tool definition must specify a non-blank 'name'");
    }

    /**
     * Returns the tool descriptor according to the MCP specification.
     * Must include "name", and typically "title", "description", "inputSchema", "outputSchema", and "annotations".
     */
    Map<String, Object> definition();

    /**
     * Alias for {@link #definition()}.
     */
    default Map<String, Object> toolInfo() {
        return definition();
    }

    /**
     * Executes the tool with the provided arguments.
     *
     * @param arguments input arguments map passed by the caller (never null, may be empty)
     * @return the execution result
     * @throws Exception if tool execution encounters an error
     */
    ToolResult execute(Map<String, Object> arguments) throws Exception;

    /**
     * Functional interface for tool execution logic.
     */
    @FunctionalInterface
    interface Executor {
        ToolResult execute(Map<String, Object> arguments) throws Exception;
    }

    /**
     * Creates an {@link McpTool} from a definition map and an execution lambda.
     */
    static McpTool of(Map<String, Object> definition, Executor executor) {
        return new McpTool() {
            @Override
            public Map<String, Object> definition() {
                return definition;
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments) throws Exception {
                return executor.execute(arguments);
            }
        };
    }
}
