package com.codeexplainer.controller;

import com.codeexplainer.dto.GitHubRequest;
import com.codeexplainer.service.AgentService;
import com.codeexplainer.service.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Route — POST /api/github endpoint for analyzing GitHub repositories.
 */
@RestController
public class GitHubController {

    private static final Logger logger = LoggerFactory.getLogger(GitHubController.class);

    private final GitHubService gitHubService;
    private final AgentService agentService;

    public GitHubController(GitHubService gitHubService, AgentService agentService) {
        this.gitHubService = gitHubService;
        this.agentService = agentService;
    }

    @PostMapping("/api/github")
    public ResponseEntity<?> analyzeGithub(@RequestBody GitHubRequest request) {
        try {
            logger.info("Analyzing GitHub repo: {}", request.getUrl());

            // Fetch repository files
            Map<String, String> repoData = gitHubService.fetchRepo(request.getUrl());

            if (repoData == null || repoData.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("detail", "No supported source files found in the repository.")
                );
            }

            // Run the analysis agent
            Map<String, Object> results = agentService.analyze(repoData);

            // Build response matching Python API contract
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("url", request.getUrl());
            response.put("file_count", repoData.size());

            // Truncate file contents to first 200 chars
            Map<String, String> truncatedFiles = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : repoData.entrySet()) {
                String content = entry.getValue();
                truncatedFiles.put(entry.getKey(),
                        content.length() > 200 ? content.substring(0, 200) + "..." : content);
            }
            response.put("files", truncatedFiles);

            response.put("languages", results.getOrDefault("languages", Map.of()));
            response.put("primary_language", results.getOrDefault("primary_language", "unknown"));
            response.put("graph", results.getOrDefault("graph", Map.of("nodes", List.of(), "edges", List.of())));
            response.put("summaries", results.getOrDefault("summaries", Map.of()));
            response.put("architecture_overview", results.getOrDefault("architecture_overview", ""));
            response.put("entry_points", results.getOrDefault("entry_points", List.of()));
            response.put("cycles", results.getOrDefault("cycles", List.of()));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("GitHub analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("detail", e.getMessage()));
        } catch (Exception e) {
            logger.error("GitHub analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", "Analysis failed: " + e.getMessage()));
        }
    }
}
