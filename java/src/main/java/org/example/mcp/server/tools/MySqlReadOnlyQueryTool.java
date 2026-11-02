package org.example.mcp.server.tools;

import org.example.mcp.server.McpCore;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MCP tool for executing read-only SQL queries on a MySQL database using JDBC.
 */
public class MySqlReadOnlyQueryTool implements McpTool {

    public static final String NAME = "mysql_query";

    private static final Set<String> ALLOWED_START_KEYWORDS = Set.of(
            "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "WITH"
    );

    private static final Pattern DISALLOWED_KEYWORDS_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|RENAME|REPLACE|GRANT|REVOKE|LOCK|UNLOCK|SET|CALL|EXECUTE|FLUSH|KILL|SHUTDOWN)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, Object> DEFINITION = McpCore.obj(
            "name", NAME,
            "title", "Execute read-only MySQL SQL query",
            "description", "Executes a read-only SQL query (SELECT, SHOW, DESCRIBE, EXPLAIN) against a MySQL database using JDBC. "
                    + "Mutating queries (INSERT, UPDATE, DELETE, DROP, ALTER, etc.) are strictly disallowed.",
            "inputSchema", McpCore.obj(
                    "type", "object",
                    "properties", McpCore.obj(
                            "query", McpCore.obj(
                                    "type", "string",
                                    "description", "The read-only SQL query to execute (e.g., 'SELECT * FROM users LIMIT 10', 'SHOW TABLES')."),
                            "database", McpCore.obj(
                                    "type", "string",
                                    "description", "Optional MySQL database/catalog name to switch to for the query."),
                            "maxRows", McpCore.obj(
                                    "type", "integer",
                                    "description", "Maximum number of rows to return (default 100, max 1000).")),
                    "required", List.of("query"),
                    "additionalProperties", false),
            "outputSchema", McpCore.obj(
                    "type", "object",
                    "properties", McpCore.obj(
                            "columns", McpCore.obj("type", "array", "items", McpCore.obj("type", "string")),
                            "rowCount", McpCore.obj("type", "integer"),
                            "truncated", McpCore.obj("type", "boolean"),
                            "rows", McpCore.obj("type", "array", "items", McpCore.obj("type", "object"))),
                    "required", List.of("columns", "rowCount", "truncated", "rows")),
            "annotations", McpCore.obj(
                    "readOnlyHint", true,
                    "idempotentHint", true,
                    "openWorldHint", false)
    );

    private final String defaultJdbcUrl;
    private final String defaultUser;
    private final String defaultPassword;
    private final String defaultDatabase;

    public MySqlReadOnlyQueryTool() {
        this(
                System.getenv().getOrDefault("MYSQL_URL", System.getenv().getOrDefault("MYSQL_JDBC_URL", "jdbc:mysql://localhost:3306/")),
                System.getenv().getOrDefault("MYSQL_USER", "root"),
                System.getenv().getOrDefault("MYSQL_PASSWORD", ""),
                System.getenv("MYSQL_DATABASE")
        );
    }

    public MySqlReadOnlyQueryTool(String jdbcUrl, String user, String password) {
        this(jdbcUrl, user, password, null);
    }

    public MySqlReadOnlyQueryTool(String jdbcUrl, String user, String password, String defaultDatabase) {
        this.defaultJdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.defaultUser = user != null ? user : "root";
        this.defaultPassword = password != null ? password : "";
        this.defaultDatabase = defaultDatabase;
    }

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
        Object queryObj = arguments.get("query");
        if (!(queryObj instanceof String rawQuery) || rawQuery.isBlank()) {
            return ToolResult.error("Missing required parameter 'query'");
        }

        String validationError = validateReadOnlyQuery(rawQuery);
        if (validationError != null) {
            return ToolResult.error(validationError);
        }

        int maxRows = 100;
        if (arguments.get("maxRows") instanceof Number n) {
            maxRows = Math.min(Math.max(1, n.intValue()), 1000);
        }

        String database = arguments.get("database") instanceof String db && !db.isBlank()
                ? db.trim() : defaultDatabase;

        String jdbcUrl = resolveJdbcUrl(defaultJdbcUrl, database);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, defaultUser, defaultPassword)) {
            conn.setReadOnly(true);

            if (database != null && !database.isBlank()) {
                try {
                    conn.setCatalog(database);
                } catch (SQLException ignored) {
                    // Fall back if catalog setting is unsupported or already set via URL
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.setMaxRows(maxRows + 1); // fetch one extra to test truncation

                try (ResultSet rs = stmt.executeQuery(rawQuery)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columnCount = md.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(md.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    boolean truncated = false;

                    while (rs.next()) {
                        if (rows.size() >= maxRows) {
                            truncated = true;
                            break;
                        }

                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String colName = columns.get(i - 1);
                            Object val = rs.getObject(i);
                            row.put(colName, val);
                        }
                        rows.add(row);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("columns", columns);
                    result.put("rowCount", rows.size());
                    result.put("truncated", truncated);
                    result.put("rows", rows);

                    return ToolResult.structured(result);
                }
            }
        } catch (SQLException e) {
            return ToolResult.error("MySQL query failed (" + e.getErrorCode() + "): " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Database connection error: " + e.getMessage());
        }
    }

    /**
     * Validates that the query is strictly read-only and does not contain mutating commands or chained queries.
     */
    static String validateReadOnlyQuery(String query) {
        String clean = stripComments(query).trim();
        if (clean.isEmpty()) {
            return "Query cannot be empty.";
        }

        // Prevent multiple statement execution via semicolon
        int semicolonIdx = clean.indexOf(';');
        if (semicolonIdx != -1 && semicolonIdx < clean.length() - 1) {
            String remainder = clean.substring(semicolonIdx + 1).trim();
            if (!remainder.isEmpty()) {
                return "Multiple queries in a single call are not allowed.";
            }
        }

        // Check starting keyword
        String firstWord = clean.split("\\s+")[0].toUpperCase(Locale.ROOT);
        if (!ALLOWED_START_KEYWORDS.contains(firstWord)) {
            return "Only read-only queries (SELECT, SHOW, DESCRIBE, EXPLAIN, WITH) are permitted. Found: " + firstWord;
        }

        // Guard against CTEs or subqueries containing mutating statements
        if (DISALLOWED_KEYWORDS_PATTERN.matcher(clean).find()) {
            return "Query contains forbidden modification keywords (INSERT, UPDATE, DELETE, DROP, ALTER, etc.).";
        }

        return null;
    }

    /**
     * Strips SQL comments (-- ..., /* ... *&#47;, # ...)
     */
    static String stripComments(String sql) {
        String noBlockComments = sql.replaceAll("/\\*.*?\\*/", " ");
        String noLineComments1 = noBlockComments.replaceAll("--[^\r\n]*", " ");
        return noLineComments1.replaceAll("#[^\r\n]*", " ");
    }

    static String resolveJdbcUrl(String baseUrl, String database) {
        if (database == null || database.isBlank()) {
            return baseUrl;
        }
        // If baseUrl ends with '/' or has query parameters, format properly
        if (baseUrl.contains("?")) {
            int qIdx = baseUrl.indexOf('?');
            String path = baseUrl.substring(0, qIdx);
            String query = baseUrl.substring(qIdx);
            if (!path.endsWith("/")) {
                path += "/";
            }
            return path + database + query;
        } else {
            return baseUrl.endsWith("/") ? baseUrl + database : baseUrl + "/" + database;
        }
    }
}
