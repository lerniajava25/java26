package org.example.mcp;

/**
 * A protocol-level failure: carries the JSON-RPC error code AND the HTTP status to use.
 */
public class McpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    final int httpStatus;
    final int code;
    final transient Object data;

    McpException(int httpStatus, int code, String message, Object data) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.data = data;
    }

    McpException(int httpStatus, int code, String message) {
        this(httpStatus, code, message, null);
    }
}
