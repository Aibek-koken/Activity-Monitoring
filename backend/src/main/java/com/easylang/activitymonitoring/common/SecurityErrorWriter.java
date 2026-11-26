package com.easylang.activitymonitoring.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorWriter.class);

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(response, ProblemResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI()
        ));
    }

    public void writeForbidden(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception
    ) throws IOException {
        boolean csrfFailure = exception instanceof CsrfException;
        log.warn("Access denied on {}: {}", request.getRequestURI(), exception.getClass().getSimpleName());
        write(response, ProblemResponse.of(
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                csrfFailure
                        ? "Your security token expired. Refresh the page and try again."
                        : "Your role does not have access to this resource",
                request.getRequestURI()
        ));
    }

    private void write(HttpServletResponse response, ProblemResponse problem) throws IOException {
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

