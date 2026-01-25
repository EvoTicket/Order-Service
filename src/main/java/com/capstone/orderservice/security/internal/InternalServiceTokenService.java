package com.capstone.orderservice.security.internal;

import com.capstone.orderservice.property.InternalServiceTokenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalServiceTokenService {

    private final InternalServiceTokenProperties properties;

    public String generateServiceToken(String serviceName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("serviceName", serviceName);
        claims.put("type", "INTERNAL_SERVICE");

        Date now = new Date();
        Date expiration = new Date(now.getTime() + properties.getTokenExpiration());

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(serviceName)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();

        log.debug("Generated internal service token for service: {}", serviceName);
        return token;
    }

    public boolean validateServiceToken(String token) {
        try {
            Claims claims = extractServiceClaims(token);

            String type = claims.get("type", String.class);
            if (!"INTERNAL_SERVICE".equals(type)) {
                log.warn("Invalid token type: {}", type);
                return false;
            }

            if (claims.getExpiration().before(new Date())) {
                log.warn("Token expired for service: {}", claims.getSubject());
                return false;
            }

            if (properties.getAllowedServices() != null && properties.getAllowedServices().length > 0) {
                String serviceName = claims.get("serviceName", String.class);
                boolean isAllowed = false;
                for (String allowedService : properties.getAllowedServices()) {
                    if (allowedService.equals(serviceName)) {
                        isAllowed = true;
                        break;
                    }
                }
                if (!isAllowed) {
                    log.warn("Service not in allowed list: {}", serviceName);
                    return false;
                }
            }

            log.debug("Token validated successfully for service: {}", claims.getSubject());
            return true;

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractServiceClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getServiceName(String token) {
        Claims claims = extractServiceClaims(token);
        return claims.get("serviceName", String.class);
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
