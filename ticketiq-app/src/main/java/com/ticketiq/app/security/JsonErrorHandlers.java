package com.ticketiq.app.security;

import com.ticketiq.app.common.ApiError;
import com.ticketiq.core.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 401 and 403 from the security filter chain, in the same JSON shape as every other error. */
public class JsonErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, 401, "Unauthorized", "Please sign in");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, 403, "Forbidden", "You do not have permission to do that");
    }

    private static void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        ApiError body = new ApiError(Instant.now(), status, error, message, null);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("timestamp", body.timestamp().toString());
        json.put("status", body.status());
        json.put("error", body.error());
        json.put("message", body.message());
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(Json.write(json));
    }
}
