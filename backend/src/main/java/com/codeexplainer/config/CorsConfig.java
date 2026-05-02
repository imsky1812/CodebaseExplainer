package com.codeexplainer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS configuration — allows the frontend to call the API.
 */
@Configuration
public class CorsConfig {

    @Value("${frontend.url:}")
    private String frontendUrl;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                List<String> origins = new ArrayList<>();
                origins.add("http://localhost:3000");
                origins.add("http://127.0.0.1:3000");

                if (frontendUrl != null && !frontendUrl.isBlank()) {
                    String cleaned = frontendUrl.endsWith("/")
                            ? frontendUrl.substring(0, frontendUrl.length() - 1)
                            : frontendUrl;
                    origins.add(cleaned);
                }

                registry.addMapping("/**")
                        .allowedOrigins(origins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
