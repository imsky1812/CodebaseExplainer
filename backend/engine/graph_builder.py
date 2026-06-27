import os
from typing import Dict, List, Set, Any, Optional
from engine.code_parser import ParsedFile

class GraphBuilder:
    LANGUAGE_COLORS = {
        "python": "#3776AB",
        "javascript": "#F7DF1E",
        "typescript": "#3178C6",
        "java": "#ED8B00",
        "go": "#00ADD8",
        "cpp": "#00599C",
        "c": "#A8B9CC",
        "csharp": "#239120",
        "ruby": "#CC342D",
        "rust": "#DEA584",
        "unknown": "#6B7280"
    }

    def __init__(self):
        self.file_set: Set[str] = set()
        self.adjacency: Dict[str, Dict[str, List[str]]] = {}

    def build(self, parsed_files: Dict[str, ParsedFile]) -> Dict[str, Any]:
        self.file_set = set(parsed_files.keys())
        self.adjacency = {}

        nodes = []
        edges = []

        # Create nodes
        for filename, data in parsed_files.items():
            language = data.language
            basename = filename.split('/')[-1] if '/' in filename else filename

            node_data = {
                "label": basename,
                "fullPath": filename,
                "language": language,
                "imports": data.imports,
                "exports": data.exports,
                "declarations": data.declarations,
                "summary": ""
            }

            bg_color = self.LANGUAGE_COLORS.get(language, self.LANGUAGE_COLORS["unknown"])
            text_color = "#000" if language == "javascript" else "#fff"

            style = {
                "background": bg_color,
                "color": text_color,
                "border": "2px solid rgba(255,255,255,0.2)",
                "borderRadius": "12px",
                "padding": "10px",
                "fontSize": "12px",
                "fontWeight": "600",
                "width": 180
            }

            node = {
                "id": filename,
                "type": "default",
                "data": node_data,
                "position": {"x": 0, "y": 0},
                "style": style
            }

            nodes.add(node) if hasattr(nodes, 'add') else nodes.append(node)
            self.adjacency[filename] = {
                "imports": [],
                "imported_by": []
            }

        # Resolve imports and create edges
        edge_id = 0
        for filename, data in parsed_files.items():
            for imp in data.imports:
                resolved = self._resolve_import(filename, imp)
                if resolved and resolved in self.file_set:
                    edge_id += 1
                    edge = {
                        "id": f"e{edge_id}",
                        "source": filename,
                        "target": resolved,
                        "animated": True,
                        "style": {"stroke": "#6366f1", "strokeWidth": 2},
                        "type": "smoothstep"
                    }
                    edges.append(edge)

                    self.adjacency[filename]["imports"].append(resolved)
                    if resolved in self.adjacency:
                        self.adjacency[resolved]["imported_by"].append(filename)

        # Calculate coordinates using layouts
        self._calculate_positions(nodes)

        return {
            "nodes": nodes,
            "edges": edges,
            "cycles": [],
            "entry_points": []
        }

    def detect_cycles(self) -> List[List[str]]:
        visited: Set[str] = set()
        rec_stack: Set[str] = set()
        cycles: List[List[str]] = []
        path: List[str] = []

        for node in self.adjacency.keys():
            if node not in visited:
                self._dfs_cycle(node, visited, rec_stack, path, cycles)
        return cycles

    def _dfs_cycle(self, node: str, visited: Set[str], rec_stack: Set[str], path: List[str], cycles: List[List[str]]):
        visited.add(node)
        rec_stack.add(node)
        path.append(node)

        neighbors = self.adjacency.get(node, {}).get("imports", [])
        for neighbor in neighbors:
            if neighbor not in visited:
                self._dfs_cycle(neighbor, visited, rec_stack, path, cycles)
            elif neighbor in rec_stack:
                cycle_start = path.index(neighbor)
                cycle = path[cycle_start:].copy()
                cycle.append(neighbor)
                cycles.append(cycle)

        path.pop()
        rec_stack.remove(node)

    def get_adjacency(self, filename: str) -> Dict[str, List[str]]:
        return self.adjacency.get(filename, {"imports": [], "imported_by": []})

    def _resolve_import(self, source_file: str, import_path: str) -> Optional[str]:
        # Handle relative imports starting with .
        if import_path.startswith("."):
            source_dir = source_file.rpartition('/')[0] if '/' in source_file else ""

            if import_path.startswith("./"):
                candidate_base = f"{source_dir}/{import_path[2:]}" if source_dir else import_path[2:]
            elif import_path.startswith("../"):
                parts = source_dir.split('/') if source_dir else []
                import_parts = import_path.split('/')
                up_count = 0
                for p in import_parts:
                    if p == "..":
                        up_count += 1
                    else:
                        break
                remaining = "/".join(import_parts[up_count:])
                base_parts = parts[:-up_count] if up_count <= len(parts) else []
                candidate_base = f"{'/'.join(base_parts)}/{remaining}" if base_parts else remaining
            else:
                candidate_base = import_path[1:]
                if source_dir:
                    candidate_base = f"{source_dir}/{candidate_base}"
            
            return self._find_file(candidate_base)

        # Handle absolute / module dotted path (Python style)
        module_path = import_path.replace(".", "/")
        res = self._find_file(module_path)
        if res:
            return res

        # Try as-is
        return self._find_file(import_path)

    def _find_file(self, basePath: str) -> Optional[str]:
        if basePath in self.file_set:
            return basePath

        extensions = [".py", ".js", ".ts", ".jsx", ".tsx", ".java", ".go", ".rs"]
        for ext in extensions:
            candidate = basePath + ext
            if candidate in self.file_set:
                return candidate

        # Index files for JS/TS
        for idx in ["index.js", "index.ts", "index.jsx", "index.tsx"]:
            candidate = f"{basePath}/{idx}"
            if candidate in self.file_set:
                return candidate

        # Python init files
        init_py = f"{basePath}/__init__.py"
        if init_py in self.file_set:
            return init_py

        # --- Fallback: Suffix Matching ---
        # Normalize slashes to forward slashes for cross-platform robustness
        normalized_base = basePath.replace('\\', '/')

        def find_suffix(candidate_path: str) -> Optional[str]:
            for file in self.file_set:
                normalized_file = file.replace('\\', '/')
                if normalized_file == candidate_path or normalized_file.endswith('/' + candidate_path):
                    return file
            return None

        # Check with direct base name using suffix match
        res = find_suffix(normalized_base)
        if res:
            return res

        # Check with extensions using suffix match
        for ext in extensions:
            res = find_suffix(normalized_base + ext)
            if res:
                return res

        # Check index files using suffix match
        for idx in ["index.js", "index.ts", "index.jsx", "index.tsx"]:
            res = find_suffix(f"{normalized_base}/{idx}")
            if res:
                return res

        # Check Python init file using suffix match
        res = find_suffix(f"{normalized_base}/__init__.py")
        if res:
            return res

        return None

    def _calculate_positions(self, nodes: List[dict]):
        if not nodes:
            return

        layers: Dict[int, List[int]] = {}
        for i, node in enumerate(nodes):
            node_id = node["id"]
            depth = node_id.count('/')
            layers.setdefault(depth, []).append(i)

        sorted_layer_keys = sorted(layers.keys())
        y_offset = 0
        x_spacing = 280
        y_spacing = 150

        for depth in sorted_layer_keys:
            indices = layers[depth]
            total_width = len(indices) * x_spacing
            x_start = -(total_width // 2) + (x_spacing // 2)

            for j, idx in enumerate(indices):
                nodes[idx]["position"] = {
                    "x": x_start + j * x_spacing,
                    "y": y_offset
                }
            y_offset += y_spacing
