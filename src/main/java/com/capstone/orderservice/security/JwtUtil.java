package com.capstone.orderservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final JwtService jwtService;
    private final HttpServletRequest request;

    public TokenMetaData getDataFromAuth() {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token =header.substring(7);
        }

        Claims claims = jwtService.extractAllClaims(token);
        Long userId = claims.get("userId", Long.class);
        Boolean isOrganization = claims.get("isOrganization", Boolean.class);
        Long organizationId = claims.get("organizationId", Long.class);

        return new TokenMetaData(userId, isOrganization, organizationId);
    }

    public String getToken(){
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token =header.substring(7);
        }
        return token;
    }
}
