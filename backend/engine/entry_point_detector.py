from typing import Dict, List, Set, Any
from engine.code_parser import ParsedFile

class EntryPointDetector:
    ENTRY_POINT_NAMES = {
        "main.py", "app.py", "server.py", "wsgi.py", "asgi.py",
        "manage.py", "__main__.py", "run.py", "cli.py",
        "index.js", "index.ts", "index.jsx", "index.tsx",
        "app.js", "app.ts", "app.jsx", "app.tsx",
        "server.js", "server.ts",
        "main.js", "main.ts", "main.go", "main.rs",
        "main.java", "main.c", "main.cpp",
        "program.cs", "startup.cs"
    }

    ENTRY_POINT_PATTERNS = [
        "src/index", "src/main", "src/app", "cmd/", "bin/"
    ]

    def detect(self, parsed_files: Dict[str, ParsedFile], graph_data: Dict[str, Any]) -> List[str]:
        entry_points: Set[str] = set()
        all_files = parsed_files.keys()

        # 1. Filename heuristics
        for filename in all_files:
            basename = filename.split('/')[-1].lower() if '/' in filename else filename.lower()

            if basename in self.ENTRY_POINT_NAMES:
                entry_points.add(filename)
                continue

            for pattern in self.ENTRY_POINT_PATTERNS:
                if pattern in filename.lower():
                    entry_points.add(filename)
                    break

        # 2. Files with no incoming edges that import at least one other file
        edges = graph_data.get("edges", [])
        targets = {edge.get("target") for edge in edges if edge.get("target")}

        for filename in all_files:
            if filename not in targets:
                # Check if this file imports at least one other file
                has_imports = any(edge.get("source") == filename for edge in edges)
                if has_imports:
                    entry_points.add(filename)

        # 3. Hub files — files that import the most other files
        import_counts: Dict[str, int] = {}
        for edge in edges:
            src = edge.get("source")
            if src:
                import_counts[src] = import_counts.get(src, 0) + 1

        if import_counts:
            max_imports = max(import_counts.values())
            threshold = max(max_imports * 0.7, 3.0)
            for src, count in import_counts.items():
                if count >= threshold:
                    entry_points.add(src)

        result = sorted(list(entry_points))
        return result
