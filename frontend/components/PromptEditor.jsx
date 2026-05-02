"use client";

import { useState, useEffect } from "react";
import { Settings, X, Save, RotateCcw, AlertTriangle } from "lucide-react";
import { getPrompts, updatePrompt } from "../lib/api";

/**
 * PromptEditor — Dev tools panel for viewing and editing LLM prompts.
 */
export default function PromptEditor() {
  const [isOpen, setIsOpen] = useState(false);
  const [prompts, setPrompts] = useState({});
  const [editedPrompts, setEditedPrompts] = useState({});
  const [saving, setSaving] = useState({});
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (isOpen) {
      loadPrompts();
    }
  }, [isOpen]);

  const loadPrompts = async () => {
    try {
      setError("");
      const data = await getPrompts();
      setPrompts(data.prompts || {});
      setEditedPrompts(data.prompts || {});
    } catch (err) {
      setError(err.message);
    }
  };

  const handleSave = async (key) => {
    setSaving((prev) => ({ ...prev, [key]: true }));
    setError("");
    setSuccess("");

    try {
      await updatePrompt(key, editedPrompts[key]);
      setPrompts((prev) => ({ ...prev, [key]: editedPrompts[key] }));
      setSuccess(`Prompt "${key}" saved successfully.`);
      setTimeout(() => setSuccess(""), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleReset = (key) => {
    setEditedPrompts((prev) => ({ ...prev, [key]: prompts[key] }));
  };

  const isModified = (key) => editedPrompts[key] !== prompts[key];

  if (!isOpen) {
    return (
      <button
        className="prompt-editor-trigger"
        onClick={() => setIsOpen(true)}
        title="Dev Tools — Edit LLM Prompts"
        id="btn-dev-tools"
      >
        <Settings size={18} />
      </button>
    );
  }

  return (
    <div className="prompt-editor-overlay" id="prompt-editor">
      <div className="prompt-editor-modal">
        {/* Header */}
        <div className="prompt-editor-header">
          <div className="prompt-editor-title">
            <Settings size={20} />
            <h2>Prompt Editor</h2>
          </div>
          <button
            className="close-btn"
            onClick={() => setIsOpen(false)}
            id="btn-close-prompts"
          >
            <X size={20} />
          </button>
        </div>

        {/* Warning */}
        <div className="prompt-warning">
          <AlertTriangle size={16} />
          <span>Modifying prompts affects all AI explanations.</span>
        </div>

        {/* Error / Success */}
        {error && <div className="prompt-error">{error}</div>}
        {success && <div className="prompt-success">{success}</div>}

        {/* Prompt List */}
        <div className="prompt-list">
          {Object.entries(editedPrompts).map(([key, value]) => (
            <div key={key} className="prompt-item">
              <div className="prompt-item-header">
                <label className="prompt-key">{key}</label>
                <div className="prompt-actions">
                  {isModified(key) && (
                    <button
                      className="prompt-action-btn reset"
                      onClick={() => handleReset(key)}
                      title="Reset to saved value"
                    >
                      <RotateCcw size={14} />
                    </button>
                  )}
                  <button
                    className="prompt-action-btn save"
                    onClick={() => handleSave(key)}
                    disabled={saving[key] || !isModified(key)}
                    title="Save changes"
                  >
                    <Save size={14} />
                    {saving[key] ? "Saving..." : "Save"}
                  </button>
                </div>
              </div>
              <textarea
                className="prompt-textarea"
                value={value}
                onChange={(e) =>
                  setEditedPrompts((prev) => ({
                    ...prev,
                    [key]: e.target.value,
                  }))
                }
                rows={6}
                id={`prompt-${key}`}
              />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
