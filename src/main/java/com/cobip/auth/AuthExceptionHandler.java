package com.cobip.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<AuthDtos.ApiError> authError(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new AuthDtos.ApiError(exception.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AuthDtos.ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), "입력값을 확인해주세요."));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AuthDtos.ApiError("INVALID_REQUEST", "입력값을 확인해주세요.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthDtos.ApiError> unreadableRequest() {
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError(
                "INVALID_REQUEST", "요청 형식을 확인해주세요.", null));
    }
}
