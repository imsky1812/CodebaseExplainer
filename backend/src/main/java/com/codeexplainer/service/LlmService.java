package com.codeexplainer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM Service — Groq API integration for code explanations.
 * Uses the OpenAI-compatible API at https://api.groq.com/openai/v1.
 * Loads prompts from prompts.json, never hardcodes prompt strings.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(60))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api-key:}")
    private String apiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    private Map<String, String> prompts;
    private Path promptsFilePath;

    @PostConstruct
    public void init() {
        loadPrompts();
    }

    // ── Prompt management ─────────────────────────────────────────────────

    private void loadPrompts() {
        try {
            // Try loading from external file first (for editable prompts)
            promptsFilePath = Paths.get("prompts.json");
            if (Files.exists(promptsFilePath)) {
                String content = Files.readString(promptsFilePath);
                prompts = objectMapper.readValue(content, new TypeReference<LinkedHashMap<String, String>>() {});
                logger.info("Loaded prompts from external file: {}", promptsFilePath);
                return;
            }

            // Fall back to classpath resource
            ClassPathResource resource = new ClassPathResource("prompts.json");
            try (InputStream is = resource.getInputStream()) {
                prompts = objectMapper.readValue(is, new TypeReference<LinkedHashMap<String, String>>() {});
                // Copy to external file for editability
                promptsFilePath = Paths.get("prompts.json");
                Files.writeString(promptsFilePath, objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(prompts));
                logger.info("Loaded prompts from classpath and copied to: {}", promptsFilePath);
            }
        } catch (IOException e) {
            logger.error("Failed to load prompts: {}", e.getMessage());
            prompts = new LinkedHashMap<>();
        }
    }

    public Map<String, String> getPrompts() {
        return prompts;
    }

    public void updatePrompt(String key, String value) throws IOException {
        prompts.put(key, value);
        Files.writeString(promptsFilePath, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(prompts));
    }

    public String getPrompt(String key) {
        if (!prompts.containsKey(key)) {
            throw new IllegalArgumentException("Prompt key '" + key + "' not found in prompts.json");
        }
        return prompts.get(key);
    }

    // ── LLM calls ─────────────────────────────────────────────────────────

    private String callLlm(String promptText, int maxRetries) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_groq_api_key_here")) {
            return "[LLM unavailable — GROQ_API_KEY not configured]";
        }

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", new Object[]{
                        Map.of("role", "system", "content",
                                "You are an expert code analyst. Provide clear, concise, and accurate explanations."),
                        Map.of("role", "user", "content", promptText)
                });
                requestBody.put("temperature", 0.4);
                requestBody.put("max_tokens", 2048);

                String json = objectMapper.writeValueAsString(requestBody);

                Request request = new Request.Builder()
                        .url(GROQ_API_URL)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(json, MediaType.parse("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonNode root = objectMapper.readTree(response.body().string());
                        return root.at("/choices/0/message/content").asText();
                    }

                    // Handle rate limiting
                    if (response.code() == 429) {
                        int waitTime = (int) Math.pow(2, attempt) + 1;
                        logger.warn("Groq API rate limited (attempt {}/{}). Retrying in {}s...",
                                attempt + 1, maxRetries, waitTime);
                        Thread.sleep(waitTime * 1000L);
                        continue;
                    }

                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    throw new IOException("Groq API error (" + response.code() + "): " + errorBody);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "[LLM error: interrupted]";
            } catch (Exception e) {
                int waitTime = (int) Math.pow(2, attempt) + 1;
                logger.warn("Groq API call failed (attempt {}/{}): {}. Retrying in {}s...",
                        attempt + 1, maxRetries, e.getMessage(), waitTime);
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(waitTime * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "[LLM error: interrupted]";
                    }
                } else {
                    logger.error("Groq API call failed after {} attempts: {}", maxRetries, e.getMessage());
                    return "[LLM error after " + maxRetries + " retries: " + e.getMessage() + "]";
                }
            }
        }
        return "[LLM error: max retries exceeded]";
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Generate a plain-English summary of a single file.
     */
    public String summarizeFile(String filename, String content) {
        return summarizeFile(filename, content, "file_summary");
    }

    public String summarizeFile(String filename, String content, String promptKey) {
        String template = getPrompt(promptKey);
        String promptText = template
                .replace("{filename}", filename)
                .replace("{content}", content);
        return callLlm(promptText, 3);
    }

    /**
     * Generate an architecture overview of the entire codebase.
     */
    public String explainArchitecture(String fileTree, String deps, String entryPoints) {
        return explainArchitecture(fileTree, deps, entryPoints, "architecture_overview");
    }

    public String explainArchitecture(String fileTree, String deps, String entryPoints, String promptKey) {
        String template = getPrompt(promptKey);
        String promptText = template
                .replace("{file_tree}", fileTree)
                .replace("{dependencies}", deps)
                .replace("{entry_points}", entryPoints);
        return callLlm(promptText, 3);
    }

    /**
     * Explain a file in the simplest possible terms.
     */
    public String explainSimple(String filename, String content) {
        return explainSimple(filename, content, "simple_explanation");
    }

    public String explainSimple(String filename, String content, String promptKey) {
        String template = getPrompt(promptKey);
        String promptText = template
                .replace("{filename}", filename)
                .replace("{content}", content);
        return callLlm(promptText, 3);
    }

    /**
     * Explain a dependency relationship between two files.
     */
    public String explainDependency(String source, String target,
                                     String sourceContent, String targetContent) {
        String template = getPrompt("dependency_explanation");
        String promptText = template
                .replace("{source}", source)
                .replace("{target}", target)
                .replace("{source_content}", sourceContent)
                .replace("{target_content}", targetContent);
        return callLlm(promptText, 3);
    }
}
