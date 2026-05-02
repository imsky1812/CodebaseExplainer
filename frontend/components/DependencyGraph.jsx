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
      let borderColor = "#4ade80"; // green (leaf)
      if (inCycle) {
        borderColor = "#f87171"; // red (circular)
      } else if (hasDeps) {
        borderColor = "#60a5fa"; // blue (has deps)
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
              fontFamily: "'Inter', sans-serif",
              fontSize: "12px",
              fontWeight: 600,
              color: "#e8e8f0",
              whiteSpace: "nowrap",
            }}>
              {isEntry && <span style={{ fontSize: "11px" }}>⚡</span>}
              {inCycle && <span style={{ fontSize: "11px" }}>⚠️</span>}
              <span>{label}</span>
            </div>
          ),
        },
        style: {
          background: isSelected
            ? "rgba(40, 42, 58, 0.95)"
            : "rgba(28, 30, 44, 0.92)",
          color: "#e8e8f0",
          border: isSelected
            ? `2.5px solid ${borderColor}`
            : `1.5px solid ${borderColor}`,
          borderRadius: "20px",        // Pill shape like madge
          padding: "6px 16px",
          fontSize: "12px",
          fontWeight: "600",
          minWidth: "auto",
          width: "auto",
          boxShadow: isSelected
            ? `0 0 12px ${borderColor}55, 0 2px 8px rgba(0,0,0,0.4)`
            : "0 2px 6px rgba(0,0,0,0.3)",
          transition: "all 0.25s ease",
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
          stroke: isHighlighted ? "#a5b4fc" : "rgba(160, 165, 190, 0.45)",
          strokeWidth: isHighlighted ? 2 : 1.2,
          transition: "all 0.25s ease",
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: isHighlighted ? "#a5b4fc" : "rgba(160, 165, 190, 0.5)",
          width: 12,
          height: 12,
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
            <rect x="2" y="22" width="28" height="18" rx="9" stroke="#60a5fa" strokeWidth="1.5" fill="rgba(28,30,44,0.8)" />
            <text x="16" y="34" fill="#e8e8f0" fontSize="7" fontWeight="600" textAnchor="middle">a.js</text>
            <rect x="42" y="4" width="28" height="18" rx="9" stroke="#60a5fa" strokeWidth="1.5" fill="rgba(28,30,44,0.8)" />
            <text x="56" y="16" fill="#e8e8f0" fontSize="7" fontWeight="600" textAnchor="middle">b.js</text>
            <rect x="42" y="40" width="28" height="18" rx="9" stroke="#4ade80" strokeWidth="1.5" fill="rgba(28,30,44,0.8)" />
            <text x="56" y="52" fill="#e8e8f0" fontSize="7" fontWeight="600" textAnchor="middle">c.js</text>
            <line x1="30" y1="28" x2="42" y2="16" stroke="rgba(160,165,190,0.5)" strokeWidth="1.2" />
            <line x1="30" y1="34" x2="42" y2="46" stroke="rgba(160,165,190,0.5)" strokeWidth="1.2" />
            <polygon points="40,15 42,13 42,17" fill="rgba(160,165,190,0.5)" />
            <polygon points="40,47 42,45 42,49" fill="rgba(160,165,190,0.5)" />
          </svg>
          <p>Analyze a repository to see the dependency graph</p>
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
        <Background color="rgba(255,255,255,0.03)" gap={30} size={1} />
        <Controls className="graph-controls" showInteractive={false} />
        <MiniMap
          nodeColor={(node) => {
            const inCycle = cycleFiles.has(node.id);
            const hasDeps = filesWithDeps.has(node.id);
            if (inCycle) return "#f87171";
            if (hasDeps) return "#60a5fa";
            return "#4ade80";
          }}
          maskColor="rgba(10, 12, 20, 0.8)"
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
          <span className="graph-legend-dot" style={{ background: "#60a5fa" }} />
          has deps
        </span>
        <span className="graph-legend-item">
          <span className="graph-legend-dot" style={{ background: "#4ade80" }} />
          leaf
        </span>
        {cycles.length > 0 && (
          <span className="graph-legend-item">
            <span className="graph-legend-dot" style={{ background: "#f87171" }} />
            circular
          </span>
        )}
      </div>
    </div>
  );
}
