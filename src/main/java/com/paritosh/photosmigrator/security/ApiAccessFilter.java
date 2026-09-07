package com.paritosh.photosmigrator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiAccessFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Migrator-Key";
    private final String accessKey;

    public ApiAccessFilter(@Value("${app.security.access-key:}") String accessKey) {
        this.accessKey = accessKey == null ? "" : accessKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return accessKey.isBlank()
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || "/api/health".equals(path)
                || "/api/oauth/callback".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (!matches(supplied, accessKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid migration access key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String supplied, String expected) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
