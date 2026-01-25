package com.capstone.orderservice.config;

import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.property.InternalServiceTokenProperties;
import com.capstone.orderservice.security.internal.InternalServiceTokenService;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeignClientConfig {

    private final InternalServiceTokenService internalServiceTokenService;
    private final InternalServiceTokenProperties internalServiceTokenProperties;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            try {
                String serviceName = internalServiceTokenProperties.getServiceName();
                String internalToken = internalServiceTokenService.generateServiceToken(serviceName);

                requestTemplate.header("X-Internal-Service-Token", internalToken);

                log.debug("Added internal service token to Feign request for service: {}", serviceName);
            } catch (Exception e) {
                log.error("Failed to generate internal service token for Feign request: {}", e.getMessage());
                throw new AppException(ErrorCode.UNAUTHORIZED, "Failed to authenticate service-to-service call", e);
            }
        };
    }
}