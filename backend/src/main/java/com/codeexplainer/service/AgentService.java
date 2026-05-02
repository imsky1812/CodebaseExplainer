package com.codeexplainer.service;

import com.codeexplainer.engine.CodeParser;
import com.codeexplainer.engine.EntryPointDetector;
import com.codeexplainer.engine.GraphBuilder;
import com.codeexplainer.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent Service — AI Agent that orchestrates codebase analysis.
 * Pipeline: detect_language → parse_files → build_graph → detect_entry_points → summarize_files → generate_overview
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final CodeParser codeParser;
    private final GraphBuilder graphBuilder;
    private final EntryPointDetector entryPointDetector;
    private final LlmService llmService;

    public AgentService(CodeParser codeParser, GraphBuilder graphBuilder,
                        EntryPointDetector entryPointDetector, LlmService llmService) {
        this.codeParser = codeParser;
        this.graphBuilder = graphBuilder;
        this.entryPointDetector = entryPointDetector;
        this.llmService = llmService;
    }

    /**
     * Run the full analysis pipeline on a set of source files.
     *
     * @param repoData Map of filename → file content
     * @return Analysis results map matching the Python API's response schema
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(Map<String, String> repoData) {
        Map<String, Object> results = new LinkedHashMap<>();

        List<String> steps = List.of(
                "detect_language", "parse_files", "build_graph",
                "detect_entry_points", "summarize_files", "generate_overview"
        );
        logger.info("Agent planned {} steps: {}", steps.size(), steps);

        // ── Step 1: Detect languages ──────────────────────────────────────
        logger.info("Agent executing step: detect_language");
        Map<String, Integer> langCounts = new LinkedHashMap<>();
        for (String filename : repoData.keySet()) {
            String lang = codeParser.detectLanguage(filename);
            langCounts.merge(lang, 1, Integer::sum);
        }
        results.put("languages", langCounts);
        results.put("primary_language", langCounts.isEmpty() ? "unknown"
                : Collections.max(langCounts.entrySet(), Map.Entry.comparingByValue()).getKey());
        logger.info("Step 'detect_language' completed successfully");

        // ── Step 2: Parse files ───────────────────────────────────────────
        logger.info("Agent executing step: parse_files");
        Map<String, ParsedFile> parsedFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repoData.entrySet()) {
            try {
                parsedFiles.put(entry.getKey(), codeParser.parseFile(entry.getKey(), entry.getValue()));
            } catch (Exception e) {
                logger.warn("Failed to parse {}: {}", entry.getKey(), e.getMessage());
                parsedFiles.put(entry.getKey(), new ParsedFile(
                        entry.getKey(), codeParser.detectLanguage(entry.getKey()),
                        List.of(), List.of(), List.of()
                ));
            }
        }
        logger.info("Step 'parse_files' completed successfully");

        // ── Step 3: Build graph ───────────────────────────────────────────
        logger.info("Agent executing step: build_graph");
        Map<String, Object> graphData = graphBuilder.build(parsedFiles);
        List<List<String>> cycles = graphBuilder.detectCycles();
        results.put("graph", graphData);
        results.put("cycles", cycles);
        logger.info("Step 'build_graph' completed successfully");

        // ── Step 4: Detect entry points ───────────────────────────────────
        logger.info("Agent executing step: detect_entry_points");
        List<String> entryPoints = entryPointDetector.detect(parsedFiles, graphData);
        results.put("entry_points", entryPoints);

        // Update graph data with entry points and cycles
        ((Map<String, Object>) graphData).put("entry_points", entryPoints);
        ((Map<String, Object>) graphData).put("cycles", cycles);
        logger.info("Step 'detect_entry_points' completed successfully");

        // ── Step 5: Summarize files ───────────────────────────────────────
        logger.info("Agent executing step: summarize_files");
        Map<String, String> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repoData.entrySet()) {
            try {
                String content = entry.getValue();
                if (content.length() > 8000) content = content.substring(0, 8000);
                summaries.put(entry.getKey(), llmService.summarizeFile(entry.getKey(), content));
            } catch (Exception e) {
                logger.warn("Failed to summarize {}: {}", entry.getKey(), e.getMessage());
                summaries.put(entry.getKey(), "[Summary unavailable: " + e.getMessage() + "]");
            }
        }
        results.put("summaries", summaries);

        // Inject summaries into graph nodes
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data != null && summaries.containsKey(nodeId)) {
                    data.put("summary", summaries.get(nodeId));
                }
            }
        }
        logger.info("Step 'summarize_files' completed successfully");

        // ── Step 6: Generate overview ─────────────────────────────────────
        logger.info("Agent executing step: generate_overview");
        try {
            String fileTree = repoData.keySet().stream().sorted().collect(Collectors.joining("\n"));

            List<Map<String, Object>> edges = (List<Map<String, Object>>) graphData.get("edges");
            String deps;
            if (edges != null && !edges.isEmpty()) {
                deps = edges.stream()
                        .map(e -> e.get("source") + " → " + e.get("target"))
                        .collect(Collectors.joining("\n"));
            } else {
                deps = "No dependencies detected";
            }

            String entryPointsStr = String.join(", ", entryPoints);
            String overview = llmService.explainArchitecture(fileTree, deps, entryPointsStr);
            results.put("architecture_overview", overview);
        } catch (Exception e) {
            logger.error("Failed to generate architecture overview: {}", e.getMessage());
            results.put("architecture_overview", "[Architecture overview unavailable: " + e.getMessage() + "]");
        }
        logger.info("Step 'generate_overview' completed successfully");

        return results;
    }
}
