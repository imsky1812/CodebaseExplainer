package com.codeexplainer.engine;

import com.codeexplainer.model.GraphEdge;
import com.codeexplainer.model.GraphNode;
import com.codeexplainer.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Graph Builder — Constructs a dependency graph from parsed source files.
 * Output is compatible with React Flow for frontend visualization.
 */
@Component
public class GraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);

    // Language → color mapping for graph nodes
    private static final Map<String, String> LANGUAGE_COLORS = Map.ofEntries(
            Map.entry("python", "#3776AB"),
            Map.entry("javascript", "#F7DF1E"),
            Map.entry("typescript", "#3178C6"),
            Map.entry("java", "#ED8B00"),
            Map.entry("go", "#00ADD8"),
            Map.entry("cpp", "#00599C"),
            Map.entry("c", "#A8B9CC"),
            Map.entry("csharp", "#239120"),
            Map.entry("ruby", "#CC342D"),
            Map.entry("rust", "#DEA584"),
            Map.entry("unknown", "#6B7280")
    );

    private Set<String> fileSet;
    private Map<String, Map<String, List<String>>> adjacency;

    /**
     * Build the dependency graph from parsed file data.
     *
     * @param parsedFiles Map of filename → ParsedFile
     * @return Graph data map compatible with React Flow
     */
    public Map<String, Object> build(Map<String, ParsedFile> parsedFiles) {
        fileSet = new HashSet<>(parsedFiles.keySet());
        adjacency = new LinkedHashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Create nodes
        for (Map.Entry<String, ParsedFile> entry : parsedFiles.entrySet()) {
            String filename = entry.getKey();
            ParsedFile data = entry.getValue();
            String language = data.getLanguage();

            // Extract basename
            String basename = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1)
                    : filename;

            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("label", basename);
            nodeData.put("fullPath", filename);
            nodeData.put("language", language);
            nodeData.put("imports", data.getImports());
            nodeData.put("exports", data.getExports());
            nodeData.put("declarations", data.getDeclarations());
            nodeData.put("summary", "");

            String bgColor = LANGUAGE_COLORS.getOrDefault(language, LANGUAGE_COLORS.get("unknown"));
            String textColor = "javascript".equals(language) ? "#000" : "#fff";

            Map<String, Object> style = new LinkedHashMap<>();
            style.put("background", bgColor);
            style.put("color", textColor);
            style.put("border", "2px solid rgba(255,255,255,0.2)");
            style.put("borderRadius", "12px");
            style.put("padding", "10px");
            style.put("fontSize", "12px");
            style.put("fontWeight", "600");
            style.put("width", 180);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", filename);
            node.put("type", "default");
            node.put("data", nodeData);
            node.put("position", Map.of("x", 0, "y", 0));
            node.put("style", style);

            nodes.add(node);

            Map<String, List<String>> adj = new LinkedHashMap<>();
            adj.put("imports", new ArrayList<>());
            adj.put("imported_by", new ArrayList<>());
            adjacency.put(filename, adj);
        }

        // Create edges by resolving imports
        int edgeId = 0;
        for (Map.Entry<String, ParsedFile> entry : parsedFiles.entrySet()) {
            String filename = entry.getKey();
            ParsedFile data = entry.getValue();

            for (String imp : data.getImports()) {
                String resolved = resolveImport(filename, imp);
                if (resolved != null && fileSet.contains(resolved)) {
                    edgeId++;
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("id", "e" + edgeId);
                    edge.put("source", filename);
                    edge.put("target", resolved);
                    edge.put("animated", true);
                    edge.put("style", Map.of("stroke", "#6366f1", "strokeWidth", 2));
                    edge.put("type", "smoothstep");
                    edges.add(edge);

                    adjacency.get(filename).get("imports").add(resolved);
                    if (adjacency.containsKey(resolved)) {
                        adjacency.get(resolved).get("imported_by").add(filename);
                    }
                }
            }
        }

        // Calculate positions
        calculatePositions(nodes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("cycles", new ArrayList<>());
        result.put("entry_points", new ArrayList<>());

        return result;
    }

    /**
     * Detect circular dependencies using DFS.
     */
    public List<List<String>> detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        List<String> path = new ArrayList<>();

        for (String node : adjacency.keySet()) {
            if (!visited.contains(node)) {
                dfsCycle(node, visited, recStack, path, cycles);
            }
        }
        return cycles;
    }

    private void dfsCycle(String node, Set<String> visited, Set<String> recStack,
                          List<String> path, List<List<String>> cycles) {
        visited.add(node);
        recStack.add(node);
        path.add(node);

        List<String> imports = adjacency.getOrDefault(node, Map.of("imports", List.of()))
                .getOrDefault("imports", List.of());

        for (String neighbor : imports) {
            if (!visited.contains(neighbor)) {
                dfsCycle(neighbor, visited, recStack, path, cycles);
            } else if (recStack.contains(neighbor)) {
                int cycleStart = path.indexOf(neighbor);
                List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(neighbor);
                cycles.add(cycle);
            }
        }

        path.remove(path.size() - 1);
        recStack.remove(node);
    }

    /**
     * Get adjacency info for a filename.
     */
    public Map<String, List<String>> getAdjacency(String filename) {
        return adjacency.getOrDefault(filename,
                Map.of("imports", List.of(), "imported_by", List.of()));
    }

    // ── Import resolution ─────────────────────────────────────────────────

    private String resolveImport(String sourceFile, String importPath) {
        // Handle relative imports
        if (importPath.startsWith(".")) {
            String sourceDir = sourceFile.contains("/")
                    ? sourceFile.substring(0, sourceFile.lastIndexOf('/'))
                    : "";

            String candidateBase;
            if (importPath.startsWith("./")) {
                candidateBase = sourceDir.isEmpty()
                        ? importPath.substring(2)
                        : sourceDir + "/" + importPath.substring(2);
            } else if (importPath.startsWith("../")) {
                String[] parts = sourceDir.isEmpty() ? new String[0] : sourceDir.split("/");
                String[] importParts = importPath.split("/");
                int upCount = 0;
                for (String p : importParts) {
                    if ("..".equals(p)) upCount++;
                    else break;
                }
                String remaining = String.join("/",
                        Arrays.copyOfRange(importParts, upCount, importParts.length));
                String[] baseParts = upCount <= parts.length
                        ? Arrays.copyOfRange(parts, 0, parts.length - upCount)
                        : new String[0];
                candidateBase = baseParts.length > 0
                        ? String.join("/", baseParts) + "/" + remaining
                        : remaining;
            } else {
                candidateBase = importPath.substring(1); // strip leading dot
                if (!sourceDir.isEmpty()) {
                    candidateBase = sourceDir + "/" + candidateBase;
                }
            }
            return findFile(candidateBase);
        }

        // Handle absolute/module imports (Python style: dotted path)
        String modulePath = importPath.replace(".", "/");
        String result = findFile(modulePath);
        if (result != null) return result;

        // Try as-is
        return findFile(importPath);
    }

    private String findFile(String basePath) {
        if (fileSet.contains(basePath)) return basePath;

        String[] extensions = {".py", ".js", ".ts", ".jsx", ".tsx", ".java", ".go", ".rs"};
        for (String ext : extensions) {
            String candidate = basePath + ext;
            if (fileSet.contains(candidate)) return candidate;
        }

        // Index files (JS/TS convention)
        for (String idx : List.of("index.js", "index.ts", "index.jsx", "index.tsx")) {
            String candidate = basePath + "/" + idx;
            if (fileSet.contains(candidate)) return candidate;
        }

        // __init__.py (Python convention)
        String initPy = basePath + "/__init__.py";
        if (fileSet.contains(initPy)) return initPy;

        return null;
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private void calculatePositions(List<Map<String, Object>> nodes) {
        if (nodes.isEmpty()) return;

        Map<Integer, List<Integer>> layers = new TreeMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String id = (String) nodes.get(i).get("id");
            int depth = (int) id.chars().filter(c -> c == '/').count();
            layers.computeIfAbsent(depth, k -> new ArrayList<>()).add(i);
        }

        int yOffset = 0;
        int xSpacing = 280;
        int ySpacing = 150;

        for (Map.Entry<Integer, List<Integer>> entry : layers.entrySet()) {
            List<Integer> indices = entry.getValue();
            int totalWidth = indices.size() * xSpacing;
            int xStart = -(totalWidth / 2) + (xSpacing / 2);

            for (int j = 0; j < indices.size(); j++) {
                int idx = indices.get(j);
                nodes.get(idx).put("position", Map.of(
                        "x", xStart + j * xSpacing,
                        "y", yOffset
                ));
            }
            yOffset += ySpacing;
        }
    }
}
