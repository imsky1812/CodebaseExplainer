package com.codeexplainer.dto;

/**
 * Request body for POST /api/github.
 */
public class GitHubRequest {
    private String url;

    public GitHubRequest() {}

    public GitHubRequest(String url) {
        this.url = url;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
