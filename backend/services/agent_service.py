import logging
from typing import Dict, Any, List
from engine.code_parser import CodeParser, ParsedFile
from engine.graph_builder import GraphBuilder
from engine.entry_point_detector import EntryPointDetector
from services.llm_service import LlmService

logger = logging.getLogger(__name__)

class AgentService:
    def __init__(self, code_parser: CodeParser, graph_builder: GraphBuilder,
                 entry_point_detector: EntryPointDetector, llm_service: LlmService):
        self.code_parser = code_parser
        self.graph_builder = graph_builder
        self.entry_point_detector = entry_point_detector
        self.llm_service = llm_service

    def analyze(self, repo_data: Dict[str, str]) -> Dict[str, Any]:
        results: Dict[str, Any] = {}

        steps = [
            "detect_language", "parse_files", "build_graph",
            "detect_entry_points", "summarize_files", "generate_overview"
        ]
        logger.info(f"Agent planned {len(steps)} steps: {steps}")

        # ── Step 1: Detect languages ──────────────────────────────────────
        logger.info("Agent executing step: detect_language")
        lang_counts: Dict[str, int] = {}
        for filename in repo_data.keys():
            lang = self.code_parser.detect_language(filename)
            lang_counts[lang] = lang_counts.get(lang, 0) + 1
        
        results["languages"] = lang_counts
        if not lang_counts:
            results["primary_language"] = "unknown"
        else:
            results["primary_language"] = max(lang_counts.items(), key=lambda x: x[1])[0]
        logger.info("Step 'detect_language' completed successfully")

        # ── Step 2: Parse files ───────────────────────────────────────────
        logger.info("Agent executing step: parse_files")
        parsed_files: Dict[str, ParsedFile] = {}
        for filename, content in repo_data.items():
            try:
                parsed_files[filename] = self.code_parser.parse_file(filename, content)
            except Exception as e:
                logger.warning(f"Failed to parse {filename}: {e}")
                parsed_files[filename] = ParsedFile(
                    filename, self.code_parser.detect_language(filename),
                    [], [], []
                )
        logger.info("Step 'parse_files' completed successfully")

        # ── Step 3: Build graph ───────────────────────────────────────────
        logger.info("Agent executing step: build_graph")
        graph_data = self.graph_builder.build(parsed_files)
        cycles = self.graph_builder.detect_cycles()
        results["graph"] = graph_data
        results["cycles"] = cycles
        logger.info("Step 'build_graph' completed successfully")

        # ── Step 4: Detect entry points ───────────────────────────────────
        logger.info("Agent executing step: detect_entry_points")
        entry_points = self.entry_point_detector.detect(parsed_files, graph_data)
        results["entry_points"] = entry_points

        # Update graph data with entry points and cycles
        graph_data["entry_points"] = entry_points
        graph_data["cycles"] = cycles
        logger.info("Step 'detect_entry_points' completed successfully")

        # ── Step 5: Summarize files ───────────────────────────────────────
        logger.info("Agent executing step: summarize_files")
        summaries: Dict[str, str] = {}
        for filename, content in repo_data.items():
            try:
                truncated_content = content[:8000] if len(content) > 8000 else content
                summaries[filename] = self.llm_service.summarize_file(filename, truncated_content)
            except Exception as e:
                logger.warning(f"Failed to summarize {filename}: {e}")
                summaries[filename] = f"[Summary unavailable: {e}]"
        results["summaries"] = summaries

        # Inject summaries into graph nodes
        nodes = graph_data.get("nodes", [])
        for node in nodes:
            node_id = node.get("id")
            if node_id in summaries:
                node.setdefault("data", {})["summary"] = summaries[node_id]
        logger.info("Step 'summarize_files' completed successfully")

        # ── Step 6: Generate overview ─────────────────────────────────────
        logger.info("Agent executing step: generate_overview")
        try:
            file_tree = "\n".join(sorted(repo_data.keys()))
            edges = graph_data.get("edges", [])
            
            if edges:
                deps = "\n".join(f"{e.get('source')} → {e.get('target')}" for e in edges if e.get('source') and e.get('target'))
            else:
                deps = "No dependencies detected"

            entry_points_str = ", ".join(entry_points)
            overview = self.llm_service.explain_architecture(file_tree, deps, entry_points_str)
            results["architecture_overview"] = overview
        except Exception as e:
            logger.error(f"Failed to generate architecture overview: {e}")
            results["architecture_overview"] = f"[Architecture overview unavailable: {e}]"
        logger.info("Step 'generate_overview' completed successfully")

        return results
