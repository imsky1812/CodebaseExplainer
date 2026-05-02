package com.codeexplainer.engine;

import com.codeexplainer.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Entry Point Detector — Identifies likely entry point files in a codebase.
 * Uses filename heuristics, graph topology, and import hub analysis.
 */
@Component
public class EntryPointDetector {

    private static final Logger logger = LoggerFactory.getLogger(EntryPointDetector.class);

    // Common entry point filenames (case-insensitive basenames)
    private static final Set<String> ENTRY_POINT_NAMES = Set.of(
            "main.py", "app.py", "server.py", "wsgi.py", "asgi.py",
            "manage.py", "__main__.py", "run.py", "cli.py",
            "index.js", "index.ts", "index.jsx", "index.tsx",
            "app.js", "app.ts", "app.jsx", "app.tsx",
            "server.js", "server.ts",
            "main.js", "main.ts", "main.go", "main.rs",
            "main.java", "main.c", "main.cpp",
            "program.cs", "startup.cs"
    );

    // Patterns that suggest an entry point
    private static final List<String> ENTRY_POINT_PATTERNS = List.of(
            "src/index", "src/main", "src/app", "cmd/", "bin/"
    );

    /**
     * Detect entry point files using multiple heuristics.
     */
    @SuppressWarnings("unchecked")
    public List<String> detect(Map<String, ParsedFile> parsedFiles, Map<String, Object> graphData) {
        Set<String> entryPoints = new LinkedHashSet<>();
        Set<String> allFiles = parsedFiles.keySet();

        // 1. Filename heuristics
        for (String filename : allFiles) {
            String basename = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1).toLowerCase()
                    : filename.toLowerCase();

            if (ENTRY_POINT_NAMES.contains(basename)) {
                entryPoints.add(filename);
                continue;
            }

            for (String pattern : ENTRY_POINT_PATTERNS) {
                if (filename.toLowerCase().contains(pattern)) {
                    entryPoints.add(filename);
                    break;
                }
            }
        }

        // 2. Files with no incoming edges (nothing imports them)
        List<Map<String, Object>> edges = (List<Map<String, Object>>)
                graphData.getOrDefault("edges", List.of());

        Set<String> targets = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            targets.add((String) edge.get("target"));
        }

        for (String filename : allFiles) {
            if (!targets.contains(filename)) {
                // Check if this file imports at least one other file
                boolean hasImports = edges.stream()
                        .anyMatch(e -> filename.equals(e.get("source")));
                if (hasImports) {
                    entryPoints.add(filename);
                }
            }
        }

        // 3. Hub files — files that import the most other files
        Map<String, Integer> importCounts = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            String src = (String) edge.get("source");
            importCounts.merge(src, 1, Integer::sum);
        }

        if (!importCounts.isEmpty()) {
            int maxImports = importCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            double threshold = Math.max(maxImports * 0.7, 3);
            for (Map.Entry<String, Integer> entry : importCounts.entrySet()) {
                if (entry.getValue() >= threshold) {
                    entryPoints.add(entry.getKey());
                }
            }
        }

        List<String> result = new ArrayList<>(entryPoints);
        Collections.sort(result);
        logger.info("Detected {} entry points: {}", result.size(), result);
        return result;
    }
}
