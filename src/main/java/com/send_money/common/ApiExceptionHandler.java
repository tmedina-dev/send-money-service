package com.send_money.common;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        log.warn("api_error status={} reason={}", exception.getStatusCode().value(), exception.getReason());
        String message = switch (exception.getStatusCode().value()) {
            case 400 -> "Invalid request.";
            case 404 -> "Requested resource not found.";
            default -> "Unable to process your request.";
        };
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message));
    }

    public record ApiError(String detail) {
    }
}
