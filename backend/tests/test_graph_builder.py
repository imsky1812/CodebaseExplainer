from engine.code_parser import ParsedFile
from engine.graph_builder import GraphBuilder

def test_graph_builder_java_suffix_resolution():
    # Mock parsed files representing the Java package structure
    parsed_files = {
        "backend/src/main/java/com/codeexplainer/controller/ExplainController.java": ParsedFile(
            filename="backend/src/main/java/com/codeexplainer/controller/ExplainController.java",
            language="java",
            imports=["com.codeexplainer.service.LlmService", "com.codeexplainer.dto.ExplainRequest"],
            exports=["ExplainController"],
            declarations=["ExplainController"]
        ),
        "backend/src/main/java/com/codeexplainer/service/LlmService.java": ParsedFile(
            filename="backend/src/main/java/com/codeexplainer/service/LlmService.java",
            language="java",
            imports=[],
            exports=["LlmService"],
            declarations=["LlmService"]
        ),
        "backend/src/main/java/com/codeexplainer/dto/ExplainRequest.java": ParsedFile(
            filename="backend/src/main/java/com/codeexplainer/dto/ExplainRequest.java",
            language="java",
            imports=[],
            exports=["ExplainRequest"],
            declarations=["ExplainRequest"]
        )
    }

    builder = GraphBuilder()
    result = builder.build(parsed_files)

    # Check edges
    edges = result["edges"]
    assert len(edges) == 2

    # Check sources and targets
    edge_sources = [e["source"] for e in edges]
    edge_targets = [e["target"] for e in edges]

    assert all(src == "backend/src/main/java/com/codeexplainer/controller/ExplainController.java" for src in edge_sources)
    assert "backend/src/main/java/com/codeexplainer/service/LlmService.java" in edge_targets
    assert "backend/src/main/java/com/codeexplainer/dto/ExplainRequest.java" in edge_targets
