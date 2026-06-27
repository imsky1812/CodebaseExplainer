import re
from typing import Dict, List, Tuple

class ParsedFile:
    def __init__(self, filename: str, language: str, imports: List[str], exports: List[str], declarations: List[str]):
        self.filename = filename
        self.language = language
        self.imports = imports
        self.exports = exports
        self.declarations = declarations

    def to_dict(self) -> dict:
        return {
            "filename": self.filename,
            "language": self.language,
            "imports": self.imports,
            "exports": self.exports,
            "declarations": self.declarations
        }

class CodeParser:
    EXTENSION_MAP = {
        ".py": "python",
        ".js": "javascript",
        ".jsx": "javascript",
        ".ts": "typescript",
        ".tsx": "typescript",
        ".java": "java",
        ".go": "go",
        ".cpp": "cpp",
        ".c": "c",
        ".cs": "csharp",
        ".rb": "ruby",
        ".rs": "rust",
        ".h": "c",
        ".hpp": "cpp"
    }

    def detect_language(self, filename: str) -> str:
        name = filename.lower()
        dot_index = name.rfind('.')
        if dot_index < 0:
            return "unknown"
        ext = name[dot_index:]
        return self.EXTENSION_MAP.get(ext, "unknown")

    def parse_file(self, filename: str, content: str) -> ParsedFile:
        language = self.detect_language(filename)

        if language == "python":
            imports, exports, declarations = self._parse_python(content)
        elif language in ("javascript", "typescript"):
            imports, exports, declarations = self._parse_javascript(content)
        elif language == "java":
            imports, exports, declarations = self._parse_java(content)
        else:
            imports, exports, declarations = self._parse_generic(content)

        return ParsedFile(filename, language, imports, exports, declarations)

    def _parse_python(self, content: str) -> Tuple[List[str], List[str], List[str]]:
        imports = []
        exports = []
        declarations = []

        import_pattern = re.compile(r"^import\s+(\S+)", re.MULTILINE)
        from_import_pattern = re.compile(r"^from\s+(\S+)\s+import", re.MULTILINE)
        func_pattern = re.compile(r"^def\s+(\w+)\s*\(", re.MULTILINE)
        class_pattern = re.compile(r"^class\s+(\w+)[\s:(]", re.MULTILINE)

        for match in import_pattern.finditer(content):
            imports.append(match.group(1))

        for match in from_import_pattern.finditer(content):
            imports.append(match.group(1))

        for match in func_pattern.finditer(content):
            name = match.group(1)
            declarations.append(name)
            if not name.startswith("_"):
                exports.append(name)

        for match in class_pattern.finditer(content):
            name = match.group(1)
            declarations.append(name)
            exports.append(name)

        return imports, exports, declarations

    def _parse_javascript(self, content: str) -> Tuple[List[str], List[str], List[str]]:
        imports = []
        exports = []
        declarations = []

        from_pattern = re.compile(r"from\s+['\"]([^'\"]+)['\"]")
        import_direct_pattern = re.compile(r"^import\s+['\"]([^'\"]+)['\"]")
        require_pattern = re.compile(r"require\s*\(\s*['\"]([^'\"]+)['\"]\s*\)")
        export_pattern = re.compile(r"^export\s+(?:default\s+)?(?:function|class|const|let|var)\s+(\w+)")
        func_pattern = re.compile(r"^(?:async\s+)?function\s+(\w+)")
        class_pattern = re.compile(r"^class\s+(\w+)")
        var_pattern = re.compile(r"^(?:const|let|var)\s+(\w+)")

        for line in content.splitlines():
            trimmed = line.strip()

            # import ... from '...'
            m = from_pattern.search(trimmed)
            if m and "import" in trimmed:
                imports.append(m.group(1))
                continue

            # import '...'
            m = import_direct_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            # require('...')
            m = require_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            # export function/class/const
            m = export_pattern.search(trimmed)
            if m:
                name = m.group(1)
                exports.append(name)
                declarations.append(name)
                continue

            # function declarations
            m = func_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))
                continue

            # class declarations
            m = class_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))
                continue

            # const/let/var at top level
            m = var_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))

        return imports, exports, declarations

    def _parse_java(self, content: str) -> Tuple[List[str], List[str], List[str]]:
        imports = []
        exports = []
        declarations = []

        import_pattern = re.compile(r"^import\s+([\w.]+);")
        class_pattern = re.compile(r"^(?:public\s+)?(?:abstract\s+)?class\s+(\w+)")
        interface_pattern = re.compile(r"^(?:public\s+)?interface\s+(\w+)")
        method_pattern = re.compile(r"\s*(?:public|protected|private)\s+.*?\s+(\w+)\s*\(")

        for line in content.splitlines():
            trimmed = line.strip()

            m = import_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            m = class_pattern.search(trimmed)
            if m:
                name = m.group(1)
                declarations.append(name)
                exports.append(name)
                continue

            m = interface_pattern.search(trimmed)
            if m:
                name = m.group(1)
                declarations.append(name)
                exports.append(name)
                continue

            m = method_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))

        return imports, exports, declarations

    def _parse_generic(self, content: str) -> Tuple[List[str], List[str], List[str]]:
        imports = []
        exports = []
        declarations = []

        include_pattern = re.compile(r"#include\s*[<\"]([^>\"]+)[>\"]")
        use_pattern = re.compile(r"^use\s+([\w:]+)")
        go_import_pattern = re.compile(r"^import\s+\"([^\"]+)\"")
        fn_pattern = re.compile(r"^(?:pub\s+)?fn\s+(\w+)")
        func_pattern = re.compile(r"^func\s+(\w+)")

        for line in content.splitlines():
            trimmed = line.strip()

            m = include_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            m = use_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            m = go_import_pattern.search(trimmed)
            if m:
                imports.append(m.group(1))
                continue

            m = fn_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))
                continue

            m = func_pattern.search(trimmed)
            if m:
                declarations.append(m.group(1))

        return imports, exports, declarations
