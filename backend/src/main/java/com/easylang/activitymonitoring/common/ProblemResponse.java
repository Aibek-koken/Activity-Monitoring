package com.easylang.activitymonitoring.common;

import java.time.Instant;
import java.util.Map;

public record ProblemResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ProblemResponse of(int status, String error, String message, String path) {
        return new ProblemResponse(Instant.now(), status, error, message, path, Map.of());
    }
}

