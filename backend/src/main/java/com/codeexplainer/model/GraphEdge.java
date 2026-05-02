package com.codeexplainer.model;

import java.util.Map;

/**
 * Graph edge compatible with React Flow frontend.
 */
public class GraphEdge {
    private String id;
    private String source;
    private String target;
    private boolean animated = true;
    private Map<String, Object> style;
    private String type = "smoothstep";

    public GraphEdge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public boolean isAnimated() { return animated; }
    public void setAnimated(boolean animated) { this.animated = animated; }

    public Map<String, Object> getStyle() { return style; }
    public void setStyle(Map<String, Object> style) { this.style = style; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
