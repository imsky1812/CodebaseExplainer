"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import ReactFlow, {
  Controls,
  MiniMap,
  Background,
  useNodesState,
  useEdgesState,
  MarkerType,
} from "reactflow";
import dagre from "dagre";
import "reactflow/dist/style.css";

/** Node dimensions for dagre layout */
const NODE_WIDTH = 160;
const NODE_HEIGHT = 40;

/**
 * Compute a dagre-based layout (left → right, organic flow like madge).
 */
function getLayoutedElements(nodes, edges) {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  dagreGraph.setGraph({
    rankdir: "LR",       // Left-to-Right flow like madge
    nodesep: 40,         // Vertical spacing
    ranksep: 80,         // Horizontal spacing between ranks
    marginx: 30,
    marginy: 30,
  });

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  const layoutedNodes = nodes.map((node) => {
    const pos = dagreGraph.node(node.id);
    return {
      ...node,
      position: {
        x: pos.x - NODE_WIDTH / 2,
        y: pos.y - NODE_HEIGHT / 2,
      },
    };
  });

  return { nodes: layoutedNodes, edges };
}

/**
 * DependencyGraph — Madge-style interactive dependency graph.
 *
 * Color coding:
 *   🔵 Blue border  = file has outgoing dependencies
 *   🟢 Green border = leaf node (no dependencies)
 *   🔴 Red border   = file is part of a circular dependency
 */
export default function DependencyGraph({
  graphData,
  onNodeClick,
  selectedNode,
  entryPoints = [],
  cycles = [],
}) {
  const cycleFiles = useMemo(() => new Set((cycles || []).flat()), [cycles]);
  const entrySet = useMemo(() => new Set(entryPoints || []), [entryPoints]);

  // Build a set of files that have outgoing edges (importers)
  const filesWithDeps = useMemo(() => {
    if (!graphData?.edges) return new Set();
    return new Set(graphData.edges.map((e) => e.source));
  }, [graphData]);

  const { layoutedNodes, layoutedEdges } = useMemo(() => {
    if (!graphData?.nodes) return { layoutedNodes: [], layoutedEdges: [] };

    const rawNodes = graphData.nodes.map((node) => {
      const isEntry = entrySet.has(node.id);
      const inCycle = cycleFiles.has(node.id);
      const hasDeps = filesWithDeps.has(node.id);
      const isSelected = selectedNode === node.id;

      // Color logic matching madge:
      //  - Red = circular dependency
      //  - Blue = has outgoing deps
      //  - Green = leaf (no deps)
      let borderColor = "#10b981"; // success green (leaf)
      if (inCycle) {
        borderColor = "#ef4444"; // red (circular)
      } else if (hasDeps) {
        borderColor = "#6366f1"; // electric indigo (has deps)
      }

      // Compact label: just the filename path
      const label = node.data?.label || node.id;

      return {
        ...node,
        data: {
          ...node.data,
          label: (
            <div style={{
              display: "flex",
              alignItems: "center",
              gap: "6px",
              fontFamily: "'Space Grotesk', sans-serif",
              fontSize: "12px",
              fontWeight: 600,
              color: "#1e293b",
              whiteSpace: "nowrap",
            }}>
              {isEntry && <span style={{ fontSize: "11px" }}>⚡</span>}
              {inCycle && <span style={{ fontSize: "11px" }}>⚠️</span>}
              <span>{label}</span>
            </div>
          ),
        },
        style: {
          background: "#e0e5ec",
          color: "#1e293b",
          border: isSelected
            ? `2px solid ${borderColor}`
            : `1px solid ${borderColor}`,
          borderRadius: "20px",        // Pill shape like madge
          padding: "6px 16px",
          fontSize: "12px",
          fontFamily: "'Space Grotesk', sans-serif",
          fontWeight: "600",
          minWidth: "auto",
          width: "auto",
          boxShadow: isSelected
            ? `inset 3px 3px 6px #a3b1c6, inset -3px -3px 6px #ffffff, 0 0 10px ${borderColor}44`
            : "3px 3px 6px #a3b1c6, -3px -3px 6px #ffffff",
          transition: "all 0.3s cubic-bezier(0.16, 1, 0.3, 1)",
          cursor: "pointer",
        },
      };
    });

    const rawEdges = (graphData.edges || []).map((edge) => {
      const isHighlighted =
        selectedNode === edge.source || selectedNode === edge.target;

      return {
        ...edge,
        type: "default",  // bezier curves like madge
        animated: false,
        style: {
          stroke: isHighlighted ? "#4f46e5" : "rgba(100, 116, 139, 0.25)",
          strokeWidth: isHighlighted ? 2.2 : 1.1,
          transition: "all 0.3s cubic-bezier(0.16, 1, 0.3, 1)",
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: isHighlighted ? "#4f46e5" : "rgba(100, 116, 139, 0.25)",
          width: 10,
          height: 10,
        },
      };
    });

    const { nodes: ln, edges: le } = getLayoutedElements(rawNodes, rawEdges);
    return { layoutedNodes: ln, layoutedEdges: le };
  }, [graphData, selectedNode, entrySet, cycleFiles, filesWithDeps]);

  const [nodes, setNodes, onNodesChange] = useNodesState(layoutedNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(layoutedEdges);

  useEffect(() => {
    setNodes(layoutedNodes);
    setEdges(layoutedEdges);
  }, [layoutedNodes, layoutedEdges, setNodes, setEdges]);

  const handleNodeClick = useCallback(
    (_, node) => {
      if (onNodeClick) {
        onNodeClick(node.id);
      }
    },
    [onNodeClick]
  );

  if (!graphData || !graphData.nodes || graphData.nodes.length === 0) {
    return (
      <div className="graph-empty" id="dependency-graph">
        <div className="graph-empty-content">
          <svg width="100" height="70" viewBox="0 0 100 70" fill="none">
            <rect x="2" y="22" width="28" height="18" rx="9" stroke="#6366f1" strokeWidth="1.5" fill="#e0e5ec" style={{ filter: "drop-shadow(3px 3px 6px #a3b1c6) drop-shadow(-3px -3px 6px #ffffff)" }} />
            <text x="16" y="34" fill="#1e293b" fontSize="7" fontWeight="600" textAnchor="middle">a.js</text>
            <rect x="42" y="4" width="28" height="18" rx="9" stroke="#6366f1" strokeWidth="1.5" fill="#e0e5ec" style={{ filter: "drop-shadow(3px 3px 6px #a3b1c6) drop-shadow(-3px -3px 6px #ffffff)" }} />
            <text x="56" y="16" fill="#1e293b" fontSize="7" fontWeight="600" textAnchor="middle">b.js</text>
            <rect x="42" y="40" width="28" height="18" rx="9" stroke="#10b981" strokeWidth="1.5" fill="#e0e5ec" style={{ filter: "drop-shadow(3px 3px 6px #a3b1c6) drop-shadow(-3px -3px 6px #ffffff)" }} />
            <text x="56" y="52" fill="#1e293b" fontSize="7" fontWeight="600" textAnchor="middle">c.js</text>
            <line x1="30" y1="28" x2="42" y2="16" stroke="rgba(100, 116, 139, 0.3)" strokeWidth="1.2" />
            <line x1="30" y1="34" x2="42" y2="46" stroke="rgba(100, 116, 139, 0.3)" strokeWidth="1.2" />
            <polygon points="40,15 42,13 42,17" fill="rgba(100, 116, 139, 0.3)" />
            <polygon points="40,47 42,45 42,49" fill="rgba(100, 116, 139, 0.3)" />
          </svg>
          <p style={{ color: "var(--text-secondary)", fontWeight: 500 }}>Analyze a repository to see the dependency graph</p>
        </div>
      </div>
    );
  }

  return (
    <div className="graph-container" id="dependency-graph">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        fitView
        fitViewOptions={{ padding: 0.25 }}
        minZoom={0.15}
        maxZoom={2.5}
        attributionPosition="bottom-left"
      >
        <Background color="rgba(163, 177, 198, 0.35)" gap={30} size={1} />
        <Controls className="graph-controls" showInteractive={false} />
        <MiniMap
          nodeColor={(node) => {
            const inCycle = cycleFiles.has(node.id);
            const hasDeps = filesWithDeps.has(node.id);
            if (inCycle) return "#ef4444";
            if (hasDeps) return "#6366f1";
            return "#10b981";
          }}
          maskColor="rgba(224, 229, 236, 0.65)"
          className="graph-minimap"
        />
      </ReactFlow>

      {/* Legend + Stats bar */}
      <div className="graph-stats">
        <span>{graphData.nodes.length} files</span>
        <span className="graph-stat-sep">•</span>
        <span>{graphData.edges.length} deps</span>
        {cycles.length > 0 && (
          <>
            <span className="graph-stat-sep">•</span>
            <span className="cycle-warning">⚠ {cycles.length} circular</span>
          </>
        )}
        <span className="graph-stat-sep">|</span>
        <span className="graph-legend-item">
          <span className="graph-legend-dot" style={{ background: "#6366f1" }} />
          has deps
        </span>
        <span className="graph-legend-item">
          <span className="graph-legend-dot" style={{ background: "#10b981" }} />
          leaf
        </span>
        {cycles.length > 0 && (
          <span className="graph-legend-item">
            <span className="graph-legend-dot" style={{ background: "#ef4444" }} />
            circular
          </span>
        )}
      </div>
    </div>
  );
}
