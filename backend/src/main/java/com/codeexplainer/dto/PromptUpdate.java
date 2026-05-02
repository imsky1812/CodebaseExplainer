package com.codeexplainer.dto;

/**
 * Request body for PUT /api/prompts/{key}.
 */
public class PromptUpdate {
    private String value;

    public PromptUpdate() {}

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
