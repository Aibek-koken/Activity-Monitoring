package com.easylang.activitymonitoring.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemResponse> badCredentials(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid email or password",
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemResponse> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String error = HttpStatus.resolve(statusCode.value()) == null
                ? "Request failed"
                : HttpStatus.resolve(statusCode.value()).getReasonPhrase();
        String message = exception.getReason() == null ? "The request could not be completed" : exception.getReason();

        return ResponseEntity.status(statusCode).body(ProblemResponse.of(
                statusCode.value(),
                error,
                message,
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemResponse> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(new ProblemResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                "Check the highlighted fields",
                request.getRequestURI(),
                errors
        ));
    }
}
