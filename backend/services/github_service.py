import base64
import logging
import re
from typing import Dict, Optional, Tuple
import httpx
import config

logger = logging.getLogger(__name__)

class GitHubService:
    ALLOWED_EXTENSIONS = {
        ".py", ".js", ".ts", ".jsx", ".tsx",
        ".java", ".go", ".cpp", ".c", ".cs",
        ".rb", ".rs", ".h", ".hpp"
    }

    SKIP_DIRS = {
        "node_modules", ".git", "dist", "build",
        "__pycache__", ".next", ".venv", "venv",
        "env", ".tox", ".mypy_cache", ".pytest_cache",
        "vendor", "target", "bin", "obj"
    }

    MAX_FILE_SIZE = 100_000

    def parse_github_url(self, url: str) -> Tuple[str, str, str]:
        url = url.strip().rstrip('/')
        if url.endswith(".git"):
            url = url[:-4]

        # Extract owner and repo
        match = re.search(r"github\.com/([^/]+)/([^/]+)", url)
        if not match:
            raise ValueError(f"Invalid GitHub URL: {url}")

        owner = match.group(1)
        repo = match.group(2)

        # Try to extract branch
        branch = "main"
        branch_match = re.search(r"github\.com/[^/]+/[^/]+/tree/([^/]+)", url)
        if branch_match:
            branch = branch_match.group(1)

        return owner, repo, branch

    def _get_headers(self) -> Dict[str, str]:
        headers = {
            "Accept": "application/vnd.github.v3+json"
        }
        token = config.GITHUB_TOKEN
        if token and token != "your_github_token_here_optional":
            headers["Authorization"] = f"token {token}"
        return headers

    def should_skip(self, path: str) -> bool:
        parts = path.split('/')
        for part in parts:
            if part in self.SKIP_DIRS:
                return True
        return False

    def has_allowed_extension(self, path: str) -> bool:
        for ext in self.ALLOWED_EXTENSIONS:
            if path.endswith(ext):
                return True
        return False

    def fetch_repo(self, github_url: str) -> Dict[str, str]:
        owner, repo, branch = self.parse_github_url(github_url)

        tree_data = self._fetch_tree(owner, repo, branch)
        if tree_data is None and branch == "main":
            branch = "master"
            tree_data = self._fetch_tree(owner, repo, branch)

        if tree_data is None:
            raise RuntimeError("GitHub API error: Could not fetch repository tree")

        files: Dict[str, str] = {}
        tree = tree_data.get("tree", [])

        if isinstance(tree, list):
            for item in tree:
                path = item.get("path", "")
                item_type = item.get("type", "")
                size = item.get("size", 0)

                if item_type != "blob":
                    continue
                if self.should_skip(path):
                    continue
                if not self.has_allowed_extension(path):
                    continue
                if size > self.MAX_FILE_SIZE:
                    logger.info(f"Skipping large file: {path} ({size} bytes)")
                    continue

                try:
                    content = self._fetch_file_content(owner, repo, path, branch)
                    if content is not None:
                        files[path] = content
                except Exception as e:
                    logger.warning(f"Failed to fetch {path}: {e}")

        logger.info(f"Fetched {len(files)} files from {owner}/{repo}")
        return files

    def _fetch_tree(self, owner: str, repo: str, branch: str) -> Optional[dict]:
        url = f"https://api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1"
        headers = self._get_headers()

        try:
            with httpx.Client(timeout=30.0) as client:
                response = client.get(url, headers=headers)
                if response.status_code == 404:
                    return None
                if response.status_code != 200:
                    raise RuntimeError(f"GitHub API error ({response.status_code}): {response.text}")
                return response.json()
        except Exception as e:
            raise RuntimeError(f"Failed to fetch GitHub tree: {e}") from e

    def _fetch_file_content(self, owner: str, repo: str, path: str, branch: str) -> Optional[str]:
        url = f"https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={branch}"
        headers = self._get_headers()

        try:
            with httpx.Client(timeout=30.0) as client:
                response = client.get(url, headers=headers)
                if response.status_code != 200:
                    return None

                data = response.json()
                content_b64 = data.get("content", "")
                encoding = data.get("encoding", "")

                if encoding == "base64" and content_b64:
                    cleaned = re.sub(r"\s+", "", content_b64)
                    decoded_bytes = base64.b64decode(cleaned)
                    return decoded_bytes.decode("utf-8")
        except Exception as e:
            logger.warning(f"Failed to fetch content for {path}: {e}")
        return None
