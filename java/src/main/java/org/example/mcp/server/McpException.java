package org.example.mcp.server;

/**
 * A protocol-level failure: carries the JSON-RPC error code AND the HTTP status to use.
 */
public class McpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public final int httpStatus;
    public final int code;
    public final transient Object data;

    public McpException(int httpStatus, int code, String message, Object data) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.data = data;
    }

    public McpException(int httpStatus, int code, String message) {
        this(httpStatus, code, message, null);
    }
}
