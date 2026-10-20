package org.example.mcp.client;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The "x-mcp-header" feature of MCP 2026-07-28 (Streamable HTTP, "Custom Headers from Tool Parameters"):
 * a server can mark tool parameters in the tool's inputSchema, and the client must then copy the value of each
 * marked parameter into an {@code Mcp-Param-<name>} header, so gateways can route on it without parsing the body.
 */
public final class ToolHeaders {
    private ToolHeaders() {
    }

    /**
     * One annotated parameter: where its value is in the arguments and the header name suffix.
     */
    public record Param(List<String> path, String name, String type) {
    }

    private static final Pattern TCHAR = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> TYPES = Set.of("integer", "string", "boolean");
    /**
     * Keywords whose values are data, not schemas: an "x-mcp-header" key in there means nothing.
     */
    private static final Set<String> DATA_KEYWORDS = Set.of("enum", "const", "default", "examples");
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;   // 2^53 - 1

    /**
     * Finds the annotated parameters of an inputSchema.
     *
     * @throws IllegalArgumentException if any annotation breaks the spec's constraints; the tool must then not be used
     */
    @SuppressWarnings("unchecked")
    public static List<Param> parse(Object inputSchema) {
        List<Param> out = new ArrayList<>();
        if (inputSchema instanceof Map<?, ?> m) walk((Map<String, Object>) m, true, List.of(), out, new HashSet<>());
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Map<String, Object> node, boolean reachable, List<String> path, List<Param> out, Set<String> seen) {
        if (node.containsKey("x-mcp-header")) {
            if (!reachable)
                throw invalid("x-mcp-header is only allowed on properties reachable through 'properties' keys only");
            if (path.isEmpty()) throw invalid("x-mcp-header cannot be on the schema root");
            if (!(node.get("x-mcp-header") instanceof String name) || name.isEmpty() || !TCHAR.matcher(name).matches())
                throw invalid("x-mcp-header must be a non-empty HTTP token");
            if (!seen.add(name.toLowerCase(Locale.ROOT)))
                throw invalid("x-mcp-header '" + name + "' is used more than once (case-insensitive)");
            if (!(node.get("type") instanceof String type) || !TYPES.contains(type))
                throw invalid("x-mcp-header '" + name + "' is on a parameter that is not an integer, string or boolean");
            out.add(new Param(List.copyOf(path), name, type));
        }
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            if (key.equals("x-mcp-header") || DATA_KEYWORDS.contains(key)) continue;
            if (key.equals("properties") && v instanceof Map<?, ?> props) {
                for (Map.Entry<?, ?> pe : props.entrySet()) {
                    if (pe.getValue() instanceof Map<?, ?> ps) {
                        List<String> child = new ArrayList<>(path);
                        child.add(String.valueOf(pe.getKey()));
                        walk((Map<String, Object>) ps, reachable, child, out, seen);
                    }
                }
            } else if (v instanceof Map<?, ?> sub) {
                walk((Map<String, Object>) sub, false, path, out, seen);          // items, anyOf, $defs, not, ...: not reachable
            } else if (v instanceof List<?> list) {
                for (Object o : list)
                    if (o instanceof Map<?, ?> sub) walk((Map<String, Object>) sub, false, path, out, seen);
            }
        }
    }

    /**
     * The Mcp-Param-* headers for one call. A parameter that is missing or null gets no header.
     *
     * @throws IllegalArgumentException if a value does not fit its declared type
     */
    public static Map<String, String> headersFor(List<Param> params, Map<String, Object> arguments) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Param p : params) {
            Object v = lookup(arguments, p.path());
            if (v == null) continue;
            String text = switch (p.type()) {
                case "string" -> {
                    if (!(v instanceof String s))
                        throw new IllegalArgumentException("Argument '" + String.join(".", p.path()) + "' must be a string");
                    yield s;
                }
                case "boolean" -> {
                    if (!(v instanceof Boolean b))
                        throw new IllegalArgumentException("Argument '" + String.join(".", p.path()) + "' must be a boolean");
                    yield b ? "true" : "false";
                }
                default -> Long.toString(toSafeInteger(v, String.join(".", p.path())));
            };
            headers.put("Mcp-Param-" + p.name(), encodeValue(text));
        }
        return headers;
    }

    private static long toSafeInteger(Object v, String what) {
        long l;
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            l = ((Number) v).longValue();
        } else if (v instanceof Double || v instanceof Float || v instanceof java.math.BigDecimal) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d))
                throw new IllegalArgumentException("Argument '" + what + "' must be an integer");
            l = (long) d;
        } else if (v instanceof java.math.BigInteger bi && bi.bitLength() < 63) {
            l = bi.longValue();
        } else {
            throw new IllegalArgumentException("Argument '" + what + "' must be an integer");
        }
        if (Math.abs(l) > MAX_SAFE_INTEGER)
            throw new IllegalArgumentException("Argument '" + what + "' is outside the safe integer range");
        return l;
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> arguments, List<String> path) {
        Object cur = arguments;
        for (String key : path) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(key)) return null;
            cur = ((Map<String, Object>) m).get(key);
        }
        return cur;
    }

    /**
     * Header value encoding of the spec: plain if it is visible ASCII (space and tab allowed inside), otherwise
     * (non-ASCII, control characters, leading/trailing whitespace, or looks like the sentinel itself)
     * {@code =?base64?<Base64 of UTF-8>?=}. Used for Mcp-Param-* and Mcp-Name.
     */
    public static String encodeValue(String value) {
        if (value == null) return "";
        boolean plain = !(value.startsWith("=?base64?") && value.endsWith("?="));
        if (plain && !value.isEmpty() && (isBlank(value.charAt(0)) || isBlank(value.charAt(value.length() - 1))))
            plain = false;
        for (int i = 0; plain && i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c == '\t' || (c >= 0x20 && c <= 0x7E))) plain = false;
        }
        return plain ? value : "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
