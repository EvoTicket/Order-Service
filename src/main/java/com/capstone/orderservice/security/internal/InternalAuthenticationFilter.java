package com.capstone.orderservice.security.internal;

import com.capstone.orderservice.property.InternalServiceTokenProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String INTERNAL_API_PATH = "/api/internal/";

    private final InternalServiceTokenService tokenService;
    private final InternalServiceTokenProperties properties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();
        String internalToken = request.getHeader(INTERNAL_TOKEN_HEADER);

        boolean isInternalApi = requestPath.contains(INTERNAL_API_PATH);

        // If internal token is present, validate it
        if (internalToken != null && !internalToken.isEmpty()) {
            try {
                if (tokenService.validateServiceToken(internalToken)) {
                    String serviceName = tokenService.getServiceName(internalToken);

                    InternalServiceAuthentication authentication = new InternalServiceAuthentication(serviceName);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Internal service authenticated: {}", serviceName);
                } else {
                    log.warn("Invalid internal service token from IP: {}", request.getRemoteAddr());
                    if (isInternalApi) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token");
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("Error validating internal service token: {}", e.getMessage());
                if (isInternalApi) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal service authentication failed");
                    return;
                }
            }
        } else if (isInternalApi) {
            log.warn("Internal API accessed without token from IP: {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal service token required");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
