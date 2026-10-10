package org.example.mcp.tools;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.example.mcp.McpCore.obj;

/**
 * Built-in MCP tool that returns the current date and time.
 */
public class CurrentTimeTool implements McpTool {

    public static final String NAME = "get_current_time";

    public static final Map<String, Object> DEFINITION = obj(
            "name", NAME,
            "title", "Current date and time",
            "description", "Returns the current date and time. Optionally takes an IANA time zone "
                    + "such as 'Europe/Stockholm' or 'America/New_York'. Defaults to UTC.",
            "inputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "timezone", obj(
                                    "type", "string",
                                    "description", "IANA time zone ID, e.g. Europe/Stockholm. Defaults to UTC.")),
                    "additionalProperties", false),
            "outputSchema", obj(
                    "type", "object",
                    "properties", obj(
                            "iso8601", obj("type", "string"),
                            "date", obj("type", "string"),
                            "time", obj("type", "string"),
                            "timezone", obj("type", "string"),
                            "utcOffset", obj("type", "string"),
                            "epochMillis", obj("type", "integer")),
                    "required", List.of("iso8601", "date", "time", "timezone", "utcOffset", "epochMillis")),
            "annotations", obj("readOnlyHint", true, "idempotentHint", true, "openWorldHint", false));

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
        ZoneId zone;
        Object tz = arguments.get("timezone");
        try {
            if (tz == null) {
                zone = ZoneOffset.UTC;
            } else if (tz instanceof String s) {
                zone = ZoneId.of(s);
            } else {
                return ToolResult.error("'timezone' must be a string, e.g. \"Europe/Stockholm\".");
            }
        } catch (DateTimeException e) {
            return ToolResult.error("Unknown time zone error for '" + tz
                    + "'. Use an IANA ID such as \"Europe/Stockholm\" or \"UTC\".");
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> structured = obj(
                "iso8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "date", now.toLocalDate().toString(),
                "time", now.toLocalTime().withNano(0).toString(),
                "timezone", zone.getId(),
                "utcOffset", now.getOffset().getId(),
                "epochMillis", now.toInstant().toEpochMilli());

        return ToolResult.structured(structured);
    }
}
