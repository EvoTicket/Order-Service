package com.capstone.orderservice.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.security.internal")
public class InternalServiceTokenProperties {
    private String secretKey;

    private Long tokenExpiration = 3600000L;

    private boolean enabled = true;

    private String serviceName;

    private String[] allowedServices;
}
