package com.linkup.app.sync;

/** A failed server call. code is the HTTP status, or 0 when the server could not be reached. */
public class ApiException extends Exception {

    public final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public boolean isUnauthorized() {
        return code == 401;
    }

    public boolean isOffline() {
        return code == 0;
    }
}
