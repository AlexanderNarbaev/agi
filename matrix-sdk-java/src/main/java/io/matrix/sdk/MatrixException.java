package io.matrix.sdk;

/**
 * SDK exception wrapping non-2xx responses from matrix-api-gateway.
 */
public class MatrixException extends RuntimeException {

    public final int statusCode;
    public final String errorBody;

    public MatrixException(int statusCode, String errorBody) {
        super("HTTP " + statusCode + ": " + errorBody);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }
}
