package com.codeexplainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * CodeBase Explainer — Spring Boot Backend
 * Main application entry point.
 */
@SpringBootApplication
public class CodeExplainerApplication {

    private static final Logger logger = LoggerFactory.getLogger(CodeExplainerApplication.class);

    @Value("${server.port:8000}")
    private int port;

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${github.token:}")
    private String githubToken;

    public static void main(String[] args) {
        SpringApplication.run(CodeExplainerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        logger.info("============================================================");
        logger.info("  CodeBase Explainer API — Starting");
        logger.info("============================================================");
        logger.info("  Port: {}", port);
        logger.info("  Groq API Key:   {}", (groqApiKey != null && !groqApiKey.isBlank() && !groqApiKey.equals("your_groq_api_key_here")) ? "✓ configured" : "✗ not set");
        logger.info("  Groq Model:     {}", groqModel);
        logger.info("  GitHub Token:   {}", (githubToken != null && !githubToken.isBlank() && !githubToken.equals("your_github_token_here_optional")) ? "✓ configured" : "✗ not set (public repos only)");
        logger.info("============================================================");
    }
}
