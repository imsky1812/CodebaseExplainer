package com.codeexplainer.dto;

/**
 * Request body for POST /api/explain.
 */
public class ExplainRequest {
    private String filename;
    private String content;
    private boolean simple;

    public ExplainRequest() {}

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isSimple() { return simple; }
    public void setSimple(boolean simple) { this.simple = simple; }
}
