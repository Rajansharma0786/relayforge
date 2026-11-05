package dev.relayforge.delivery;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;
    InternalTokenFilter(@Value("${relayforge.internal-token}") String token) {
        if (token.isBlank()) throw new IllegalArgumentException("Configure INTERNAL_SERVICE_TOKEN.");
        expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String received = request.getHeader("X-RelayForge-Token");
        if (received == null || !MessageDigest.isEqual(expected, received.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(401, "Internal service authentication required.");
            return;
        }
        chain.doFilter(request, response);
    }
}
