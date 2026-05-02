package com.codeexplainer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Service — Fetch repository contents via the GitHub REST API.
 */
@Service
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(30))
            .build();

    @Value("${github.token:}")
    private String githubToken;

    // Extensions we care about
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".py", ".js", ".ts", ".jsx", ".tsx",
            ".java", ".go", ".cpp", ".c", ".cs",
            ".rb", ".rs", ".h", ".hpp"
    );

    // Directories to skip
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build",
            "__pycache__", ".next", ".venv", "venv",
            "env", ".tox", ".mypy_cache", ".pytest_cache",
            "vendor", "target", "bin", "obj"
    );

    // Max file size (100 KB)
    private static final int MAX_FILE_SIZE = 100_000;

    /**
     * Parse owner and repo from a GitHub URL.
     */
    private String[] parseGitHubUrl(String url) {
        url = url.trim().replaceAll("/+$", "");
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }

        Matcher m = Pattern.compile("github\\.com/([^/]+)/([^/]+)").matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + url);
        }

        String owner = m.group(1);
        String repo = m.group(2);

        // Try to extract branch
        String branch = "main";
        Matcher branchMatcher = Pattern.compile("github\\.com/[^/]+/[^/]+/tree/([^/]+)").matcher(url);
        if (branchMatcher.find()) {
            branch = branchMatcher.group(1);
        }

        return new String[]{owner, repo, branch};
    }

    private Request.Builder requestBuilder(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json");

        if (githubToken != null && !githubToken.isBlank()
                && !githubToken.equals("your_github_token_here_optional")) {
            builder.header("Authorization", "token " + githubToken);
        }

        return builder;
    }

    private boolean shouldSkip(String path) {
        String[] parts = path.split("/");
        for (String part : parts) {
            if (SKIP_DIRS.contains(part)) return true;
        }
        return false;
    }

    private boolean hasAllowedExtension(String path) {
        for (String ext : ALLOWED_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }

    /**
     * Fetch all relevant source files from a GitHub repository.
     */
    public Map<String, String> fetchRepo(String githubUrl) {
        String[] parsed = parseGitHubUrl(githubUrl);
        String owner = parsed[0];
        String repo = parsed[1];
        String branch = parsed[2];

        // Try specified branch first, fall back to "master"
        JsonNode treeData = fetchTree(owner, repo, branch);
        if (treeData == null && "main".equals(branch)) {
            branch = "master";
            treeData = fetchTree(owner, repo, branch);
        }

        if (treeData == null) {
            throw new RuntimeException("GitHub API error: Could not fetch repository tree");
        }

        Map<String, String> files = new LinkedHashMap<>();
        JsonNode tree = treeData.get("tree");

        if (tree != null && tree.isArray()) {
            for (JsonNode item : tree) {
                String path = item.has("path") ? item.get("path").asText() : "";
                String type = item.has("type") ? item.get("type").asText() : "";
                int size = item.has("size") ? item.get("size").asInt() : 0;

                if (!"blob".equals(type)) continue;
                if (shouldSkip(path)) continue;
                if (!hasAllowedExtension(path)) continue;
                if (size > MAX_FILE_SIZE) {
                    logger.info("Skipping large file: {} ({} bytes)", path, size);
                    continue;
                }

                try {
                    String content = fetchFileContent(owner, repo, path, branch);
                    if (content != null) {
                        files.put(path, content);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch {}: {}", path, e.getMessage());
                }
            }
        }

        logger.info("Fetched {} files from {}/{}", files.size(), owner, repo);
        return files;
    }

    private JsonNode fetchTree(String owner, String repo, String branch) {
        String url = String.format(
                "https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1",
                owner, repo, branch
        );

        Request request = requestBuilder(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) return null;
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("GitHub API error (" + response.code() + "): " + body);
            }
            return objectMapper.readTree(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch GitHub tree: " + e.getMessage(), e);
        }
    }

    private String fetchFileContent(String owner, String repo, String path, String branch) {
        String url = String.format(
                "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                owner, repo, path, branch
        );

        Request request = requestBuilder(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            JsonNode data = objectMapper.readTree(response.body().string());
            String contentB64 = data.has("content") ? data.get("content").asText() : "";
            String encoding = data.has("encoding") ? data.get("encoding").asText() : "";

            if ("base64".equals(encoding) && !contentB64.isBlank()) {
                // GitHub returns base64 with newlines
                String cleaned = contentB64.replaceAll("\\s", "");
                byte[] decoded = Base64.getDecoder().decode(cleaned);
                return new String(decoded, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to fetch content for {}: {}", path, e.getMessage());
        }
        return null;
    }

    /**
     * Get the set of allowed extensions (used by upload controller).
     */
    public static Set<String> getAllowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }

    /**
     * Get the set of directories to skip (used by upload controller).
     */
    public static Set<String> getSkipDirs() {
        return SKIP_DIRS;
    }
}
