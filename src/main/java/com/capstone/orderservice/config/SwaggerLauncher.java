package com.capstone.orderservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SwaggerLauncher {

    @Value("${swagger.host-url}")
    private String hostUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        String swaggerUrl = hostUrl + "/swagger-ui/index.html";
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", swaggerUrl).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", swaggerUrl).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", swaggerUrl).start();
            } else {
                log.warn("⚠️ Unknown OS — please open Swagger manually: {}", swaggerUrl);
            }
        } catch (IOException e) {
            log.warn("❌ Failed to open Swagger UI: {}", e.getMessage());
        }
    }
}