package com.codeexplainer.model;

import java.util.List;
import java.util.Map;

/**
 * Graph node compatible with React Flow frontend.
 */
public class GraphNode {
    private String id;
    private String type = "default";
    private Map<String, Object> data;
    private Map<String, Integer> position;
    private Map<String, Object> style;

    public GraphNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public Map<String, Integer> getPosition() { return position; }
    public void setPosition(Map<String, Integer> position) { this.position = position; }

    public Map<String, Object> getStyle() { return style; }
    public void setStyle(Map<String, Object> style) { this.style = style; }
}
