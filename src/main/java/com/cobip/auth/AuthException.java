package com.cobip.auth;

import org.springframework.http.HttpStatus;

final class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }
}
