"use client";

import { useState, useMemo } from "react";
import {
  ChevronRight,
  ChevronDown,
  FileCode,
  Folder,
  FolderOpen,
  Zap,
  AlertTriangle,
} from "lucide-react";

/** Language → color mapping for badges */
const LANG_COLORS = {
  python: { bg: "rgba(55, 118, 171, 0.08)", text: "#0369a1", label: "PY" },
  javascript: { bg: "rgba(240, 219, 79, 0.08)", text: "#854d0e", label: "JS" },
  typescript: { bg: "rgba(49, 120, 198, 0.08)", text: "#1d4ed8", label: "TS" },
  java: { bg: "rgba(237, 139, 0, 0.08)", text: "#c2410c", label: "JV" },
  go: { bg: "rgba(0, 173, 216, 0.08)", text: "#0891b2", label: "GO" },
  cpp: { bg: "rgba(0, 89, 156, 0.08)", text: "#4338ca", label: "C++" },
  c: { bg: "rgba(95, 107, 124, 0.08)", text: "#334155", label: "C" },
  csharp: { bg: "rgba(35, 145, 32, 0.08)", text: "#15803d", label: "C#" },
  ruby: { bg: "rgba(204, 52, 45, 0.08)", text: "#b91c1c", label: "RB" },
  rust: { bg: "rgba(222, 165, 132, 0.08)", text: "#be123c", label: "RS" },
  unknown: { bg: "rgba(107, 114, 128, 0.08)", text: "#475569", label: "?" },
};

/**
 * Build a tree structure from flat file paths.
 */
function buildTree(files, entryPoints = [], cycles = []) {
  const root = { name: "", children: {}, files: [] };
  const cycleFiles = new Set(cycles.flat());

  for (const [filepath, data] of Object.entries(files)) {
    const parts = filepath.split("/");
    let current = root;

    for (let i = 0; i < parts.length - 1; i++) {
      if (!current.children[parts[i]]) {
        current.children[parts[i]] = { name: parts[i], children: {}, files: [] };
      }
      current = current.children[parts[i]];
    }

    current.files.push({
      name: parts[parts.length - 1],
      path: filepath,
      language: data?.language || "unknown",
      isEntryPoint: entryPoints.includes(filepath),
      inCycle: cycleFiles.has(filepath),
    });
  }

  return root;
}

/** Recursive tree node component */
function TreeNode({ node, level = 0, onFileClick, selectedFile }) {
  const [expanded, setExpanded] = useState(level < 2);
  const dirs = Object.values(node.children);
  const hasContent = dirs.length > 0 || node.files.length > 0;

  if (!hasContent && level > 0) return null;

  return (
    <div className="tree-node">
      {level > 0 && (
        <div
          className="tree-dir"
          style={{ paddingLeft: `${level * 16}px` }}
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? (
            <ChevronDown size={14} className="tree-chevron" />
          ) : (
            <ChevronRight size={14} className="tree-chevron" />
          )}
          {expanded ? (
            <FolderOpen size={16} className="tree-folder-icon" />
          ) : (
            <Folder size={16} className="tree-folder-icon" />
          )}
          <span className="tree-dir-name">{node.name}</span>
        </div>
      )}

      {(expanded || level === 0) && (
        <>
          {dirs.map((child) => (
            <TreeNode
              key={child.name}
              node={child}
              level={level + 1}
              onFileClick={onFileClick}
              selectedFile={selectedFile}
            />
          ))}
          {node.files.map((file) => {
            const langStyle = LANG_COLORS[file.language] || LANG_COLORS.unknown;
            const isSelected = selectedFile === file.path;
            return (
              <div
                key={file.path}
                className={`tree-file ${isSelected ? "selected" : ""}`}
                style={{ paddingLeft: `${(level + 1) * 16}px` }}
                onClick={() => onFileClick(file.path)}
                title={file.path}
                id={`file-${file.path.replace(/[/\\. ]/g, "-")}`}
              >
                <FileCode size={14} className="tree-file-icon" />
                <span className="tree-file-name">{file.name}</span>
                <span
                  className="lang-badge"
                  style={{
                    backgroundColor: langStyle.bg,
                    color: langStyle.text,
                  }}
                >
                  {langStyle.label}
                </span>
                {file.isEntryPoint && (
                  <Zap
                    size={14}
                    className="entry-point-icon"
                    title="Entry point"
                  />
                )}
                {file.inCycle && (
                  <AlertTriangle
                    size={14}
                    className="cycle-icon"
                    title="Circular dependency"
                  />
                )}
              </div>
            );
          })}
        </>
      )}
    </div>
  );
}

/**
 * FileTree — Collapsible file tree with language badges and entry point indicators.
 */
export default function FileTree({
  parsedFiles,
  entryPoints = [],
  cycles = [],
  onFileClick,
  selectedFile,
}) {
  const tree = useMemo(
    () => buildTree(parsedFiles, entryPoints, cycles),
    [parsedFiles, entryPoints, cycles]
  );

  const fileCount = Object.keys(parsedFiles).length;

  return (
    <div className="file-tree" id="file-tree">
      <div className="file-tree-header">
        <h3>Files</h3>
        <span className="file-count">{fileCount} files</span>
      </div>
      <div className="file-tree-body">
        {fileCount === 0 ? (
          <p className="tree-empty">No files to display</p>
        ) : (
          <TreeNode
            node={tree}
            onFileClick={onFileClick}
            selectedFile={selectedFile}
          />
        )}
      </div>
      <div className="tree-legend">
        <span className="legend-item">
          <Zap size={12} className="entry-point-icon" /> Entry Point
        </span>
        <span className="legend-item">
          <AlertTriangle size={12} className="cycle-icon" /> Circular Dep
        </span>
      </div>
    </div>
  );
}
