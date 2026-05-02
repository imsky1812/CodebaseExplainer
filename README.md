## 🎯 Problem Statement

Students and new developers struggle to understand large codebases. There's no clear overview of file connections, system flow, or where to begin reading code.

## 💡 Solution

CodeBase Explainer accepts a GitHub URL or file upload, parses the codebase using tree-sitter, builds a dependency graph, and uses Google Gemini to generate plain-English explanations of every file and the overall architecture.

---

## 🏗️ Architecture

```
┌─────────────┐     ┌──────────────────────────────────────────┐     ┌───────────────┐
│             │     │              FastAPI Backend              │     │               │
│   Next.js   │────▶│                                          │────▶│  Gemini LLM   │
│  Frontend   │◀────│  Routes ──▶ Agent ──▶ Engine ──▶ LLM    │◀────│   (AI API)    │
│             │     │                                          │     │               │
│  React Flow │     │  • Parser (tree-sitter + regex)          │     └───────────────┘
│  Tailwind   │     │  • Graph Builder (dependency graph)      │
│             │     │  • Entry Point Detector                  │     ┌───────────────┐
└─────────────┘     │  • GitHub Service (fetch repos)          │────▶│  GitHub API   │
                    └──────────────────────────────────────────┘     └───────────────┘
```

---

## 🛠️ Tech Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Backend       | FastAPI (Python)                    |
| Core Engine   | tree-sitter (Python bindings)       |
| Frontend      | Next.js (React) + Tailwind CSS      |
| Graph Viz     | React Flow                          |
| LLM           | Google Gemini (generativeai SDK)     |
| Auth/Secrets  | .env files (python-dotenv)           |
| Testing       | pytest (backend)                     |

---

## 📋 Setup Instructions

### Prerequisites
- Python 3.10+
- Node.js 18+
- A Gemini API key ([Get one here](https://aistudio.google.com/apikey))

### Backend Setup

```bash
cd backend
pip install -r requirements.txt

# Create .env from example
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# Run the server
uvicorn main:app --reload --port 8000
```

### Frontend Setup

```bash
cd frontend
npm install

# Create .env.local from example
cp .env.local.example .env.local

# Run the dev server
npm run dev
```

Open **http://localhost:3000** in your browser.

---

## 🚀 How to Use

1. **Enter a GitHub URL** (e.g., `https://github.com/expressjs/express`) or **upload a ZIP file**
2. Wait for the analysis pipeline (fetch → parse → graph → summarize)
3. **Explore the dependency graph** — click nodes to see explanations
4. **Browse the file tree** — files are color-coded by language
5. **Read AI explanations** — every file gets a plain-English summary
6. **View architecture overview** — understand the big picture
7. **Edit prompts** — click ⚙️ to customize AI behavior

---

## 📡 API Endpoints

### `GET /health`
Health check.
```json
{"status": "ok"}
```

### `POST /api/github`
Analyze a GitHub repository.
```json
// Request
{"url": "https://github.com/owner/repo"}

// Response
{
  "status": "success",
  "file_count": 15,
  "graph": {"nodes": [...], "edges": [...]},
  "summaries": {"src/main.py": "This file..."},
  "architecture_overview": "The project is...",
  "entry_points": ["src/main.py"],
  "cycles": []
}
```

### `POST /api/upload`
Analyze an uploaded ZIP file. Send as `multipart/form-data` with field `file`.

### `POST /api/explain`
Get AI explanation for a single file.
```json
// Request
{"filename": "utils.py", "content": "def hello()...", "simple": false}

// Response
{"status": "success", "explanation": "This file..."}
```

### `GET /api/prompts`
Get all LLM prompts.

### `PUT /api/prompts/{key}`
Update a prompt.
```json
{"value": "New prompt text..."}
```

---

## ⚙️ Prompt Configuration

All LLM prompts are stored in `backend/config/prompts.json`. Available prompts:

| Key | Purpose |
|-----|---------|
| `file_summary` | Generates detailed file explanations |
| `architecture_overview` | Describes overall project architecture |
| `entry_point_detection` | Identifies entry point files |
| `dependency_explanation` | Explains why File A depends on File B |
| `simple_explanation` | Beginner-friendly explanations |

Prompts can be edited via the **⚙️ Prompt Editor** in the UI or the API.

---

## 👥 Team

| Name | Role |
|------|------|
| **Radhika** | Backend & AI Agent |
| **Prateek** | Core Engine (Parser + Graph) |
| **Priya** | Frontend |

---

