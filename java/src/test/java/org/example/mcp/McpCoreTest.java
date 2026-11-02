package org.example.mcp;

import org.example.mcp.server.McpCore;
import org.example.mcp.server.McpException;
import org.example.mcp.server.tools.CurrentTimeTool;
import org.example.mcp.server.tools.McpTool;
import org.example.mcp.server.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpCoreTest {

    private static final String V = "2026-07-28";
    private static final Map<String, Object> VALID_META = Map.of(
            McpCore.META_VERSION, V,
            McpCore.META_CLIENT_CAPS, Map.of()
    );

    @Test
    void defaultCoreHasCurrentTimeTool() {
        McpCore core = new McpCore(new CurrentTimeTool());
        assertTrue(core.getTools().containsKey("get_current_time"));

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/list",
                "params", Map.of("_meta", VALID_META)
        );
        Map<String, Object> response = core.handleRequest(request);
        assertNotNull(response.get("result"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(1, tools.size());
        assertEquals("get_current_time", tools.get(0).get("name"));
    }

    @Test
    void executeCurrentTimeTool() {
        McpCore core = new McpCore(new CurrentTimeTool());

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_current_time",
                        "arguments", Map.of("timezone", "UTC"),
                        "_meta", VALID_META
                )
        );
        Map<String, Object> response = core.handleRequest(request);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertEquals(false, result.get("isError"));
        assertNotNull(result.get("structuredContent"));
        assertNotNull(result.get("content"));
    }

    @Test
    void customToolViaInterfaceImplementation() {
        class CalculatorTool implements McpTool {
            @Override
            public Map<String, Object> definition() {
                return McpCore.obj(
                        "name", "add",
                        "title", "Addition",
                        "description", "Adds two numbers",
                        "inputSchema", McpCore.obj(
                                "type", "object",
                                "properties", McpCore.obj(
                                        "a", McpCore.obj("type", "number"),
                                        "b", McpCore.obj("type", "number")
                                )
                        )
                );
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                Number a = (Number) arguments.get("a");
                Number b = (Number) arguments.get("b");
                if (a == null || b == null) {
                    return ToolResult.error("Both 'a' and 'b' parameters are required");
                }
                double sum = a.doubleValue() + b.doubleValue();
                return ToolResult.structured(Map.of("sum", sum));
            }
        }

        McpCore core = new McpCore(new CalculatorTool());
        assertEquals("add", new CalculatorTool().name());

        // Test execution with valid params
        Map<String, Object> validReq = Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of(
                        "name", "add",
                        "arguments", Map.of("a", 5, "b", 7),
                        "_meta", VALID_META
                )
        );
        Map<String, Object> resp = core.handleRequest(validReq);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        assertEquals(false, res.get("isError"));
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) res.get("structuredContent");
        assertEquals(12.0, structured.get("sum"));

        // Test error handling
        Map<String, Object> invalidReq = Map.of(
                "jsonrpc", "2.0",
                "id", 4,
                "method", "tools/call",
                "params", Map.of(
                        "name", "add",
                        "arguments", Map.of("a", 5),
                        "_meta", VALID_META
                )
        );
        Map<String, Object> errResp = core.handleRequest(invalidReq);
        @SuppressWarnings("unchecked")
        Map<String, Object> errRes = (Map<String, Object>) errResp.get("result");
        assertEquals(true, errRes.get("isError"));
    }

    @Test
    void customToolViaLambdaFactory() {
        McpTool upperTool = McpTool.of(
                McpCore.obj(
                        "name", "uppercase",
                        "description", "Converts string to uppercase"
                ),
                args -> {
                    String input = (String) args.get("text");
                    return ToolResult.text(input.toUpperCase());
                }
        );

        McpCore core = new McpCore();
        core.addTool(upperTool);

        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 5,
                "method", "tools/call",
                "params", Map.of(
                        "name", "uppercase",
                        "arguments", Map.of("text", "hello world"),
                        "_meta", VALID_META
                )
        );
        Map<String, Object> resp = core.handleRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        assertEquals(false, res.get("isError"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        assertEquals("HELLO WORLD", content.get(0).get("text"));
    }

    @Test
    void toolExceptionIsSafelyCaughtAndReturnsErrorResult() {
        McpTool failingTool = McpTool.of(
                McpCore.obj("name", "fail"),
                args -> {
                    throw new IllegalStateException("Simulated crash");
                }
        );

        McpCore core = new McpCore(failingTool);
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 6,
                "method", "tools/call",
                "params", Map.of(
                        "name", "fail",
                        "arguments", Map.of(),
                        "_meta", VALID_META
                )
        );
        Map<String, Object> resp = core.handleRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        assertEquals(true, res.get("isError"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        assertTrue(content.get(0).get("text").toString().contains("Simulated crash"));
    }

    @Test
    void unknownToolThrowsMcpExceptionWithHttp200() {
        McpCore core = new McpCore();
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 7,
                "method", "tools/call",
                "params", Map.of(
                        "name", "non_existent",
                        "arguments", Map.of(),
                        "_meta", VALID_META
                )
        );
        McpException ex = assertThrows(McpException.class, () -> core.handleRequest(req));
        assertEquals(200, ex.httpStatus);
        assertEquals(McpCore.INVALID_PARAMS, ex.code);
    }
}
