package com.codeexplainer.controller;

import com.codeexplainer.service.AgentService;
import com.codeexplainer.service.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Upload Route — POST /api/upload endpoint for analyzing uploaded ZIP files.
 */
@RestController
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);
    private static final int MAX_FILE_SIZE = 100_000; // 100 KB per file

    private final AgentService agentService;

    public UploadController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/api/upload")
    public ResponseEntity<?> analyzeUpload(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file type
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.endsWith(".zip")) {
                return ResponseEntity.badRequest().body(
                        Map.of("detail", "Only .zip files are accepted.")
                );
            }

            logger.info("Processing uploaded file: {} ({} bytes)", filename, file.getSize());

            // Extract files from ZIP
            Map<String, String> repoData = extractZip(file.getInputStream());

            if (repoData.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("detail", "No supported source files found in the ZIP archive.")
                );
            }

            // Run analysis
            Map<String, Object> results = agentService.analyze(repoData);

            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("filename", filename);
            response.put("file_count", repoData.size());

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

        } catch (Exception e) {
            logger.error("Upload analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", "Analysis failed: " + e.getMessage()));
        }
    }

    private Map<String, String> extractZip(InputStream inputStream) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> allowedExtensions = GitHubService.getAllowedExtensions();
        Set<String> skipDirs = GitHubService.getSkipDirs();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String path = entry.getName().replace("\\", "/");

                // Remove leading directory (common in GitHub ZIPs)
                String[] parts = path.split("/");
                if (parts.length > 1) {
                    path = String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
                }

                if (path.isEmpty()) continue;

                // Apply filters
                if (shouldSkip(path, skipDirs)) continue;
                if (!hasAllowedExtension(path, allowedExtensions)) continue;
                if (entry.getSize() > MAX_FILE_SIZE) {
                    logger.info("Skipping large file: {} ({} bytes)", path, entry.getSize());
                    continue;
                }

                try {
                    byte[] content = zis.readAllBytes();
                    files.put(path, new String(content, java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {
                    logger.warn("Failed to read {} from ZIP: {}", path, e.getMessage());
                }

                zis.closeEntry();
            }
        }

        return files;
    }

    private boolean shouldSkip(String path, Set<String> skipDirs) {
        for (String part : path.split("/")) {
            if (skipDirs.contains(part)) return true;
        }
        return false;
    }

    private boolean hasAllowedExtension(String path, Set<String> allowedExtensions) {
        for (String ext : allowedExtensions) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }
}
