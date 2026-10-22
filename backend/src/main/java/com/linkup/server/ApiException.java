package com.linkup.server;

/** Thrown by request handlers; Main turns it into a JSON error with the given HTTP status. */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
