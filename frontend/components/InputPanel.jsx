"use client";

import { useState, useRef } from "react";
import { Upload, GitBranch, Loader2, AlertCircle, ArrowRight } from "lucide-react";

/**
 * InputPanel — Tab-based input for GitHub URL or ZIP upload.
 */
export default function InputPanel({ onAnalyze, isLoading, loadingStep }) {
  const [activeTab, setActiveTab] = useState("github");
  const [githubUrl, setGithubUrl] = useState("");
  const [error, setError] = useState("");
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef(null);

  const handleGithubSubmit = (e) => {
    e.preventDefault();
    setError("");
    if (!githubUrl.trim()) {
      setError("Please enter a GitHub URL");
      return;
    }
    if (!githubUrl.includes("github.com")) {
      setError("Please enter a valid GitHub URL");
      return;
    }
    onAnalyze({ type: "github", url: githubUrl.trim() });
  };

  const handleFileUpload = (file) => {
    setError("");
    if (!file) return;
    if (!file.name.endsWith(".zip")) {
      setError("Only .zip files are accepted");
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setError("File too large. Maximum 50 MB.");
      return;
    }
    onAnalyze({ type: "upload", file });
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    handleFileUpload(file);
  };

  const steps = [
    "Fetching files...",
    "Parsing code...",
    "Building graph...",
    "Summarizing files...",
    "Generating overview...",
  ];

  return (
    <div className="input-panel">
      {/* Tab Switcher */}
      <div className="tab-switcher">
        <button
          className={`tab-btn ${activeTab === "github" ? "active" : ""}`}
          onClick={() => setActiveTab("github")}
          disabled={isLoading}
          id="tab-github"
        >
          <GitBranch size={16} />
          GitHub URL
        </button>
        <button
          className={`tab-btn ${activeTab === "upload" ? "active" : ""}`}
          onClick={() => setActiveTab("upload")}
          disabled={isLoading}
          id="tab-upload"
        >
          <Upload size={16} />
          Upload ZIP
        </button>
      </div>

      {/* GitHub URL Input */}
      {activeTab === "github" && (
        <form onSubmit={handleGithubSubmit} className="input-form">
          <div className="input-group">
            <GitBranch size={18} className="input-icon" />
            <input
              id="github-url-input"
              type="text"
              placeholder="https://github.com/owner/repo"
              value={githubUrl}
              onChange={(e) => setGithubUrl(e.target.value)}
              disabled={isLoading}
              className="url-input"
            />
          </div>
          <button
            id="analyze-btn"
            type="submit"
            disabled={isLoading || !githubUrl.trim()}
            className="analyze-btn"
          >
            {isLoading ? (
              <>
                <Loader2 size={18} className="spinner" />
                Analyzing...
              </>
            ) : (
              <>
                Analyze
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>
      )}

      {/* File Upload */}
      {activeTab === "upload" && (
        <div
          className={`drop-zone ${dragOver ? "drag-over" : ""}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
          onClick={() => !isLoading && fileInputRef.current?.click()}
          id="drop-zone"
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip"
            onChange={(e) => handleFileUpload(e.target.files[0])}
            className="file-input-hidden"
            id="file-upload-input"
          />
          <Upload size={32} className="drop-icon" />
          <p className="drop-text">
            {isLoading
              ? "Processing..."
              : "Drag & drop a .zip file or click to browse"}
          </p>
          <span className="drop-hint">Maximum file size: 50 MB</span>
        </div>
      )}

      {/* Loading Steps */}
      {isLoading && (
        <div className="loading-steps">
          {steps.map((step, i) => (
            <div
              key={i}
              className={`step ${
                loadingStep === i
                  ? "active"
                  : loadingStep > i
                  ? "done"
                  : ""
              }`}
            >
              <div className="step-dot" />
              <span>{step}</span>
            </div>
          ))}
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="error-banner" id="error-display">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}
