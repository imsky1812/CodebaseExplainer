"use client";

import { useState } from "react";
import {
  FileCode,
  Copy,
  Check,
  RefreshCw,
  BookOpen,
  ArrowUpRight,
  Layers,
} from "lucide-react";

/** Language badge colors */
const LANG_BADGE = {
  python: { bg: "#3776AB", label: "Python" },
  javascript: { bg: "#F7DF1E", label: "JavaScript", textDark: true },
  typescript: { bg: "#3178C6", label: "TypeScript" },
  java: { bg: "#ED8B00", label: "Java" },
  go: { bg: "#00ADD8", label: "Go" },
  cpp: { bg: "#00599C", label: "C++" },
  c: { bg: "#A8B9CC", label: "C" },
  csharp: { bg: "#239120", label: "C#" },
  ruby: { bg: "#CC342D", label: "Ruby" },
  rust: { bg: "#DEA584", label: "Rust" },
  unknown: { bg: "#6B7280", label: "Unknown" },
};

/**
 * ExplanationPanel — Shows AI-generated explanations for selected files.
 */
export default function ExplanationPanel({
  selectedFile,
  fileData,
  summary,
  architectureOverview,
  onExplainSimple,
  isExplaining,
}) {
  const [activeView, setActiveView] = useState("file"); // 'file' | 'architecture'
  const [copied, setCopied] = useState(false);

  const handleCopy = (text) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const lang = LANG_BADGE[fileData?.language] || LANG_BADGE.unknown;

  return (
    <div className="explanation-panel" id="explanation-panel">
      {/* View Tabs */}
      <div className="explanation-tabs">
        <button
          className={`explanation-tab ${activeView === "file" ? "active" : ""}`}
          onClick={() => setActiveView("file")}
          id="tab-file-explanation"
        >
          <FileCode size={14} />
          File Details
        </button>
        <button
          className={`explanation-tab ${
            activeView === "architecture" ? "active" : ""
          }`}
          onClick={() => setActiveView("architecture")}
          id="tab-architecture"
        >
          <Layers size={14} />
          Architecture
        </button>
      </div>

      {/* File Explanation View */}
      {activeView === "file" && (
        <div className="explanation-content">
          {!selectedFile ? (
            <div className="explanation-empty">
              <BookOpen size={48} className="empty-icon" />
              <h3>Select a file</h3>
              <p>
                Click on a file in the tree or a node in the graph to see its
                AI-generated explanation.
              </p>
            </div>
          ) : (
            <>
              {/* File header */}
              <div className="explanation-header">
                <div className="file-info">
                  <h3 className="file-name">{selectedFile.split("/").pop()}</h3>
                  <span className="file-path">{selectedFile}</span>
                </div>
                <span
                  className="language-badge"
                  style={{
                    backgroundColor: lang.bg,
                    color: lang.textDark ? "#000" : "#fff",
                  }}
                >
                  {lang.label}
                </span>
              </div>

              {/* Imports & Exports */}
              {fileData && (
                <div className="file-meta">
                  {fileData.imports?.length > 0 && (
                    <div className="meta-section">
                      <h4>
                        <ArrowUpRight size={14} /> Imports ({fileData.imports.length})
                      </h4>
                      <div className="meta-list">
                        {fileData.imports.map((imp, i) => (
                          <span key={i} className="meta-tag import-tag">
                            {imp}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {fileData.exports?.length > 0 && (
                    <div className="meta-section">
                      <h4>
                        <ArrowUpRight size={14} style={{ transform: "rotate(180deg)" }} /> Exports ({fileData.exports.length})
                      </h4>
                      <div className="meta-list">
                        {fileData.exports.map((exp, i) => (
                          <span key={i} className="meta-tag export-tag">
                            {exp}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {fileData.declarations?.length > 0 && (
                    <div className="meta-section">
                      <h4>Declarations ({fileData.declarations.length})</h4>
                      <div className="meta-list">
                        {fileData.declarations.map((dec, i) => (
                          <span key={i} className="meta-tag decl-tag">
                            {dec}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* AI Summary */}
              <div className="ai-summary">
                <div className="summary-header">
                  <h4>✨ AI Explanation</h4>
                  <div className="summary-actions">
                    <button
                      className="action-btn"
                      onClick={() => onExplainSimple?.(selectedFile)}
                      disabled={isExplaining}
                      title="Explain in simpler terms"
                      id="btn-simple-explain"
                    >
                      {isExplaining ? (
                        <RefreshCw size={14} className="spinner" />
                      ) : (
                        <RefreshCw size={14} />
                      )}
                      Simpler
                    </button>
                    <button
                      className="action-btn"
                      onClick={() => handleCopy(summary || "")}
                      title="Copy to clipboard"
                      id="btn-copy-explanation"
                    >
                      {copied ? <Check size={14} /> : <Copy size={14} />}
                      {copied ? "Copied" : "Copy"}
                    </button>
                  </div>
                </div>
                <div className="summary-body">
                  {summary ? (
                    <div className="summary-text">{summary}</div>
                  ) : (
                    <p className="summary-loading">
                      {isExplaining
                        ? "Generating explanation..."
                        : "No summary available for this file."}
                    </p>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* Architecture Overview */}
      {activeView === "architecture" && (
        <div className="explanation-content">
          <div className="ai-summary">
            <div className="summary-header">
              <h4>🏗️ Architecture Overview</h4>
              <button
                className="action-btn"
                onClick={() => handleCopy(architectureOverview || "")}
                id="btn-copy-architecture"
              >
                {copied ? <Check size={14} /> : <Copy size={14} />}
                {copied ? "Copied" : "Copy"}
              </button>
            </div>
            <div className="summary-body">
              {architectureOverview ? (
                <div className="summary-text">{architectureOverview}</div>
              ) : (
                <div className="explanation-empty">
                  <Layers size={48} className="empty-icon" />
                  <h3>No analysis yet</h3>
                  <p>Analyze a repository to see the architecture overview.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
