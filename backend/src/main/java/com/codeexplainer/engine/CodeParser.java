package com.codeexplainer.engine;

import com.codeexplainer.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser — Extracts imports, exports, and declarations from source files.
 * Uses regex-based parsing for all supported languages.
 */
@Component
public class CodeParser {

    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);

    // ── Language detection ─────────────────────────────────────────────────

    private static final Map<String, String> EXTENSION_MAP = Map.ofEntries(
            Map.entry(".py", "python"),
            Map.entry(".js", "javascript"),
            Map.entry(".jsx", "javascript"),
            Map.entry(".ts", "typescript"),
            Map.entry(".tsx", "typescript"),
            Map.entry(".java", "java"),
            Map.entry(".go", "go"),
            Map.entry(".cpp", "cpp"),
            Map.entry(".c", "c"),
            Map.entry(".cs", "csharp"),
            Map.entry(".rb", "ruby"),
            Map.entry(".rs", "rust"),
            Map.entry(".h", "c"),
            Map.entry(".hpp", "cpp")
    );

    /**
     * Detect programming language from file extension.
     */
    public String detectLanguage(String filename) {
        String name = filename.toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) return "unknown";
        String ext = name.substring(dotIndex);
        return EXTENSION_MAP.getOrDefault(ext, "unknown");
    }

    /**
     * Parse a source file to extract imports, exports, and declarations.
     */
    public ParsedFile parseFile(String filename, String content) {
        String language = detectLanguage(filename);

        List<String> imports;
        List<String> exports;
        List<String> declarations;

        switch (language) {
            case "python":
                var pyResult = parsePython(content);
                imports = pyResult[0];
                exports = pyResult[1];
                declarations = pyResult[2];
                break;
            case "javascript":
            case "typescript":
                var jsResult = parseJavaScript(content);
                imports = jsResult[0];
                exports = jsResult[1];
                declarations = jsResult[2];
                break;
            case "java":
                var javaResult = parseJava(content);
                imports = javaResult[0];
                exports = javaResult[1];
                declarations = javaResult[2];
                break;
            default:
                var genResult = parseGeneric(content);
                imports = genResult[0];
                exports = genResult[1];
                declarations = genResult[2];
                break;
        }

        return new ParsedFile(filename, language, imports, exports, declarations);
    }

    // ── Python parser ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String>[] parsePython(String content) {
        List<String> imports = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> declarations = new ArrayList<>();

        Pattern importPattern = Pattern.compile("^import\\s+(\\S+)", Pattern.MULTILINE);
        Pattern fromImportPattern = Pattern.compile("^from\\s+(\\S+)\\s+import", Pattern.MULTILINE);
        Pattern funcPattern = Pattern.compile("^def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
        Pattern classPattern = Pattern.compile("^class\\s+(\\w+)[\\s:(]", Pattern.MULTILINE);

        Matcher m = importPattern.matcher(content);
        while (m.find()) imports.add(m.group(1));

        m = fromImportPattern.matcher(content);
        while (m.find()) imports.add(m.group(1));

        m = funcPattern.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            declarations.add(name);
            if (!name.startsWith("_")) exports.add(name);
        }

        m = classPattern.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            declarations.add(name);
            exports.add(name);
        }

        return new List[]{imports, exports, declarations};
    }

    // ── JavaScript/TypeScript parser ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String>[] parseJavaScript(String content) {
        List<String> imports = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> declarations = new ArrayList<>();

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            // import ... from '...'
            Matcher m = Pattern.compile("from\\s+['\"]([^'\"]+)['\"]").matcher(trimmed);
            if (m.find() && trimmed.contains("import")) {
                imports.add(m.group(1));
                continue;
            }

            // import '...'
            m = Pattern.compile("^import\\s+['\"]([^'\"]+)['\"]").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            // require('...')
            m = Pattern.compile("require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            // export function/class/const
            m = Pattern.compile("^export\\s+(?:default\\s+)?(?:function|class|const|let|var)\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                String name = m.group(1);
                exports.add(name);
                declarations.add(name);
                continue;
            }

            // function declarations
            m = Pattern.compile("^(?:async\\s+)?function\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
                continue;
            }

            // class declarations
            m = Pattern.compile("^class\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
                continue;
            }

            // const/let/var at top level
            m = Pattern.compile("^(?:const|let|var)\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
            }
        }

        return new List[]{imports, exports, declarations};
    }

    // ── Java parser ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String>[] parseJava(String content) {
        List<String> imports = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> declarations = new ArrayList<>();

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            Matcher m = Pattern.compile("^import\\s+([\\w.]+);").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            m = Pattern.compile("^(?:public\\s+)?(?:abstract\\s+)?class\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                String name = m.group(1);
                declarations.add(name);
                exports.add(name);
                continue;
            }

            m = Pattern.compile("^(?:public\\s+)?interface\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                String name = m.group(1);
                declarations.add(name);
                exports.add(name);
                continue;
            }

            m = Pattern.compile("\\s*(?:public|protected|private)\\s+.*?\\s+(\\w+)\\s*\\(").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
            }
        }

        return new List[]{imports, exports, declarations};
    }

    // ── Generic parser (Go, Rust, C, etc.) ───────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String>[] parseGeneric(String content) {
        List<String> imports = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> declarations = new ArrayList<>();

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            // #include
            Matcher m = Pattern.compile("#include\\s*[<\"]([^>\"]+)[>\"]").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            // Rust: use
            m = Pattern.compile("^use\\s+([\\w:]+)").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            // Go: import "..."
            m = Pattern.compile("^import\\s+\"([^\"]+)\"").matcher(trimmed);
            if (m.find()) {
                imports.add(m.group(1));
                continue;
            }

            // Rust: fn
            m = Pattern.compile("^(?:pub\\s+)?fn\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
                continue;
            }

            // Go: func
            m = Pattern.compile("^func\\s+(\\w+)").matcher(trimmed);
            if (m.find()) {
                declarations.add(m.group(1));
            }
        }

        return new List[]{imports, exports, declarations};
    }
}
