package com.codeexplainer.controller;

import com.codeexplainer.dto.ExplainRequest;
import com.codeexplainer.dto.PromptUpdate;
import com.codeexplainer.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explain Route — Endpoints for file explanations and prompt management.
 */
@RestController
public class ExplainController {

    private static final Logger logger = LoggerFactory.getLogger(ExplainController.class);

    private final LlmService llmService;

    public ExplainController(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * Generate an AI explanation for a single file.
     */
    @PostMapping("/api/explain")
    public ResponseEntity<?> explainFile(@RequestBody ExplainRequest request) {
        try {
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("detail", "File content cannot be empty.")
                );
            }

            // Truncate very large files
            String content = request.getContent();
            if (content.length() > 8000) {
                content = content.substring(0, 8000);
            }

            String explanation;
            if (request.isSimple()) {
                explanation = llmService.explainSimple(request.getFilename(), content);
            } else {
                explanation = llmService.summarizeFile(request.getFilename(), content);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("filename", request.getFilename());
            response.put("explanation", explanation);
            response.put("mode", request.isSimple() ? "simple" : "detailed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Explanation failed for {}: {}", request.getFilename(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", "Explanation failed: " + e.getMessage()));
        }
    }

    /**
     * Return all configured LLM prompts.
     */
    @GetMapping("/api/prompts")
    public ResponseEntity<?> getPrompts() {
        try {
            Map<String, String> prompts = llmService.getPrompts();
            return ResponseEntity.ok(Map.of("status", "success", "prompts", prompts));
        } catch (Exception e) {
            logger.error("Failed to load prompts: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    /**
     * Update a specific prompt by key.
     */
    @PutMapping("/api/prompts/{key}")
    public ResponseEntity<?> updatePrompt(@PathVariable String key, @RequestBody PromptUpdate update) {
        try {
            Map<String, String> prompts = llmService.getPrompts();

            if (!prompts.containsKey(key)) {
                return ResponseEntity.status(404).body(
                        Map.of("detail", "Prompt key '" + key + "' not found. Available keys: " + prompts.keySet())
                );
            }

            if (update.getValue() == null || update.getValue().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("detail", "Prompt value cannot be empty.")
                );
            }

            llmService.updatePrompt(key, update.getValue());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("key", key);
            response.put("value", update.getValue());
            response.put("message", "Prompt '" + key + "' updated successfully.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to update prompt '{}': {}", key, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", e.getMessage()));
        }
    }
}
