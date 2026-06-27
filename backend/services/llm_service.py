import json
import logging
import os
import time
from typing import Dict, Optional
import httpx
import config

logger = logging.getLogger(__name__)

class LlmService:
    GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"

    def __init__(self):
        self.prompts: Dict[str, str] = {}
        self.prompts_file_path = "prompts.json"
        self._load_prompts()

    def _load_prompts(self):
        try:
            # First try in the current working directory
            if os.path.exists(self.prompts_file_path):
                with open(self.prompts_file_path, "r", encoding="utf-8") as f:
                    self.prompts = json.load(f)
                logger.info(f"Loaded prompts from file: {self.prompts_file_path}")
                return

            # Fallback default prompts if file doesn't exist
            self.prompts = {
                "file_summary": (
                    "You are a code explainer helping students understand codebases. Given the following file from a software project, provide a concise plain-English summary explaining:\n"
                    "1. What this file does\n"
                    "2. Key functions/classes and their purposes\n"
                    "3. How it fits into the larger project\n"
                    "4. Any important patterns or concepts used\n\n"
                    "File: {filename}\n\n"
                    "Content:\n```\n{content}\n```\n\n"
                    "Provide a clear, beginner-friendly explanation:"
                ),
                "architecture_overview": (
                    "You are a software architecture expert helping students understand codebases. Given the following file tree and dependency relationships of a software project, describe the overall architecture in plain English.\n\n"
                    "Explain:\n"
                    "1. The high-level structure and organization\n"
                    "2. Key components and their responsibilities\n"
                    "3. How data flows through the system\n"
                    "4. Design patterns used\n"
                    "5. Where a new developer should start reading the code\n\n"
                    "File Tree:\n{file_tree}\n\n"
                    "Dependency Relationships:\n{dependencies}\n\n"
                    "Entry Points:\n{entry_points}\n\n"
                    "Provide a comprehensive but beginner-friendly architecture overview:"
                ),
                "entry_point_detection": (
                    "Given this list of files from a software project, identify which are likely entry points (files where execution begins). Explain your reasoning for each.\n\n"
                    "Files:\n{files}\n\n"
                    "Identify entry points and explain why:"
                ),
                "dependency_explanation": (
                    "Explain why file '{source}' depends on file '{target}' in simple terms. What functionality does it import or use?\n\n"
                    "Source file ({source}):\n```\n{source_content}\n```\n\n"
                    "Target file ({target}):\n```\n{target_content}\n```\n\n"
                    "Explain the dependency relationship in plain English:"
                ),
                "simple_explanation": (
                    "You are explaining code to a complete beginner who has never programmed before. Given the following file, explain what it does using everyday analogies and simple language. Avoid technical jargon.\n\n"
                    "File: {filename}\n\n"
                    "Content:\n```\n{content}\n```\n\n"
                    "Explain in the simplest possible terms:"
                )
            }
            # Write default prompts to root prompts.json
            self._save_prompts()
            logger.info(f"Initialized default prompts and wrote to: {self.prompts_file_path}")
        except Exception as e:
            logger.error(f"Failed to load prompts: {e}")
            self.prompts = {}

    def _save_prompts(self):
        try:
            with open(self.prompts_file_path, "w", encoding="utf-8") as f:
                json.dump(self.prompts, f, indent=2)
        except Exception as e:
            logger.error(f"Failed to save prompts: {e}")

    def get_prompts(self) -> Dict[str, str]:
        return self.prompts

    def get_prompt(self, key: str) -> str:
        if key not in self.prompts:
            raise ValueError(f"Prompt key '{key}' not found in prompts.json")
        return self.prompts[key]

    def update_prompt(self, key: str, value: str):
        self.prompts[key] = value
        self._save_prompts()

    def call_llm(self, prompt_text: str, max_retries: int = 3) -> str:
        api_key = config.GROQ_API_KEY
        if not api_key or api_key == "your_groq_api_key_here":
            return "[LLM unavailable — GROQ_API_KEY not configured]"

        for attempt in range(max_retries):
            try:
                request_body = {
                    "model": config.GROQ_MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are an expert code analyst. Provide clear, concise, and accurate explanations."
                        },
                        {
                            "role": "user",
                            "content": prompt_text
                        }
                    ],
                    "temperature": 0.4,
                    "max_tokens": 2048
                }

                headers = {
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json"
                }

                with httpx.Client(timeout=60.0) as client:
                    response = client.post(self.GROQ_API_URL, json=request_body, headers=headers)

                    if response.status_code == 200:
                        res_json = response.json()
                        return res_json["choices"][0]["message"]["content"]

                    if response.status_code == 429:
                        wait_time = 2 ** attempt + 1
                        logger.warning(
                            f"Groq API rate limited (attempt {attempt + 1}/{max_retries}). "
                            f"Retrying in {wait_time}s..."
                        )
                        time.sleep(wait_time)
                        continue

                    raise httpx.HTTPStatusError(
                        f"Groq API error ({response.status_code}): {response.text}",
                        request=response.request,
                        response=response
                    )
            except Exception as e:
                wait_time = 2 ** attempt + 1
                logger.warning(
                    f"Groq API call failed (attempt {attempt + 1}/{max_retries}): {e}. "
                    f"Retrying in {wait_time}s..."
                )
                if attempt < max_retries - 1:
                    time.sleep(wait_time)
                else:
                    logger.error(f"Groq API call failed after {max_retries} attempts: {e}")
                    return f"[LLM error after {max_retries} retries: {e}]"

        return "[LLM error: max retries exceeded]"

    def summarize_file(self, filename: str, content: str, prompt_key: str = "file_summary") -> str:
        template = self.get_prompt(prompt_key)
        prompt_text = template.replace("{filename}", filename).replace("{content}", content)
        return self.call_llm(prompt_text, 3)

    def explain_architecture(self, file_tree: str, deps: str, entry_points: str, prompt_key: str = "architecture_overview") -> str:
        template = self.get_prompt(prompt_key)
        prompt_text = template.replace("{file_tree}", file_tree).replace("{dependencies}", deps).replace("{entry_points}", entry_points)
        return self.call_llm(prompt_text, 3)

    def explain_simple(self, filename: str, content: str, prompt_key: str = "simple_explanation") -> str:
        template = self.get_prompt(prompt_key)
        prompt_text = template.replace("{filename}", filename).replace("{content}", content)
        return self.call_llm(prompt_text, 3)

    def explain_dependency(self, source: str, target: str, source_content: str, target_content: str) -> str:
        template = self.get_prompt("dependency_explanation")
        prompt_text = (
            template.replace("{source}", source)
            .replace("{target}", target)
            .replace("{source_content}", source_content)
            .replace("{target_content}", target_content)
        )
        return self.call_llm(prompt_text, 3)
