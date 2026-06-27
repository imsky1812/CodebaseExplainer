import io
import logging
import zipfile
from typing import Dict, Any
from fastapi import FastAPI, File, UploadFile, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

import config
from engine.code_parser import CodeParser
from engine.graph_builder import GraphBuilder
from engine.entry_point_detector import EntryPointDetector
from services.llm_service import LlmService
from services.github_service import GitHubService
from services.agent_service import AgentService

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)
logger = logging.getLogger("codeexplainer")

app = FastAPI(title="CodeBase Explainer API", version="1.0.0")

# Setup CORS
origins = ["http://localhost:3000", "http://127.0.0.1:3000"]
if config.FRONTEND_URL:
    cleaned_frontend = config.FRONTEND_URL.rstrip('/')
    origins.append(cleaned_frontend)

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Instantiate services
code_parser = CodeParser()
graph_builder = GraphBuilder()
entry_point_detector = EntryPointDetector()
llm_service = LlmService()
github_service = GitHubService()
agent_service = AgentService(code_parser, graph_builder, entry_point_detector, llm_service)


# ── Request / Response Schema ───────────────────────────────────────────

class GitHubRequest(BaseModel):
    url: str

class ExplainRequest(BaseModel):
    filename: str
    content: str
    simple: bool = False

class PromptUpdate(BaseModel):
    value: str


# Helper to truncate file content
def truncate_files(repo_data: Dict[str, str]) -> Dict[str, str]:
    truncated = {}
    for path, content in repo_data.items():
        if len(content) > 200:
            truncated[path] = content[:200] + "..."
        else:
            truncated[path] = content
    return truncated


# ── Endpoints ───────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/api/github")
def analyze_github(request: GitHubRequest):
    try:
        logger.info(f"Analyzing GitHub repo: {request.url}")
        
        # Fetch files from GitHub
        repo_data = github_service.fetch_repo(request.url)
        
        if not repo_data:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No supported source files found in the repository."
            )
            
        # Run analysis pipeline
        results = agent_service.analyze(repo_data)
        
        # Format response matching Java/Spring schema
        response = {
            "status": "success",
            "url": request.url,
            "file_count": len(repo_data),
            "files": truncate_files(repo_data),
            "languages": results.get("languages", {}),
            "primary_language": results.get("primary_language", "unknown"),
            "graph": results.get("graph", {"nodes": [], "edges": []}),
            "summaries": results.get("summaries", {}),
            "architecture_overview": results.get("architecture_overview", ""),
            "entry_points": results.get("entry_points", []),
            "cycles": results.get("cycles", [])
        }
        return response

    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except RuntimeError as e:
        logger.error(f"GitHub analysis failed: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e))
    except Exception as e:
        logger.error(f"GitHub analysis failed: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"Analysis failed: {str(e)}")

@app.post("/api/upload")
async def analyze_upload(file: UploadFile = File(...)):
    try:
        if not file.filename or not file.filename.endswith(".zip"):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Only .zip files are accepted."
            )

        logger.info(f"Processing uploaded file: {file.filename}")
        
        zip_contents = await file.read()
        repo_data = {}
        
        # Extract files from ZIP
        try:
            with zipfile.ZipFile(io.BytesIO(zip_contents)) as z:
                for info in z.infolist():
                    if info.is_dir():
                        continue
                    
                    path = info.filename.replace("\\", "/")
                    
                    # Remove leading directory (common in GitHub ZIPs)
                    parts = path.split("/")
                    if len(parts) > 1:
                        path = "/".join(parts[1:])
                        
                    if not path:
                        continue
                        
                    if github_service.should_skip(path):
                        continue
                    if not github_service.has_allowed_extension(path):
                        continue
                    if info.file_size > github_service.MAX_FILE_SIZE:
                        logger.info(f"Skipping large file from ZIP: {path} ({info.file_size} bytes)")
                        continue
                        
                    try:
                        with z.open(info) as f:
                            content = f.read().decode("utf-8")
                            repo_data[path] = content
                    except Exception as e:
                        logger.warning(f"Failed to read {path} from ZIP: {e}")
        except zipfile.BadZipFile:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid zip file.")

        if not repo_data:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No supported source files found in the ZIP archive."
            )

        # Run analysis pipeline
        results = agent_service.analyze(repo_data)
        
        # Format response matching Java/Spring schema
        response = {
            "status": "success",
            "filename": file.filename,
            "file_count": len(repo_data),
            "files": truncate_files(repo_data),
            "languages": results.get("languages", {}),
            "primary_language": results.get("primary_language", "unknown"),
            "graph": results.get("graph", {"nodes": [], "edges": []}),
            "summaries": results.get("summaries", {}),
            "architecture_overview": results.get("architecture_overview", ""),
            "entry_points": results.get("entry_points", []),
            "cycles": results.get("cycles", [])
        }
        return response

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Upload analysis failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Analysis failed: {str(e)}"
        )

@app.post("/api/explain")
def explain_file(request: ExplainRequest):
    try:
        if not request.content or not request.content.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="File content cannot be empty."
            )

        # Truncate content
        content = request.content
        if len(content) > 8000:
            content = content[:8000]

        if request.simple:
            explanation = llm_service.explain_simple(request.filename, content)
        else:
            explanation = llm_service.summarize_file(request.filename, content)

        return {
            "status": "success",
            "filename": request.filename,
            "explanation": explanation,
            "mode": "simple" if request.simple else "detailed"
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Explanation failed for {request.filename}: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Explanation failed: {str(e)}"
        )

@app.get("/api/prompts")
def get_prompts():
    try:
        prompts = llm_service.get_prompts()
        return {"status": "success", "prompts": prompts}
    except Exception as e:
        logger.error(f"Failed to load prompts: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@app.put("/api/prompts/{key}")
def update_prompt(key: str, update: PromptUpdate):
    try:
        prompts = llm_service.get_prompts()

        if key not in prompts:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Prompt key '{key}' not found. Available keys: {list(prompts.keys())}"
            )

        if not update.value or not update.value.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Prompt value cannot be empty."
            )

        llm_service.update_prompt(key, update.value)

        return {
            "status": "success",
            "key": key,
            "value": update.value,
            "message": f"Prompt '{key}' updated successfully."
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to update prompt '{key}': {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
