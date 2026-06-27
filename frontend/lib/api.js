/**
 * API client for the CodeBase Explainer backend.
 */

const getBaseUrl = () => {
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  if (typeof window !== "undefined") {
    // If running in browser and not on localhost, use the current origin
    if (window.location.hostname !== "localhost" && window.location.hostname !== "127.0.0.1") {
      return window.location.origin;
    }
  }
  return "http://localhost:8000";
};

const BASE_URL = getBaseUrl();

/**
 * Generic fetch wrapper with error handling.
 */
async function apiFetch(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;

  try {
    const response = await fetch(url, {
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
      ...options,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(
        errorData.detail || `API error: ${response.status} ${response.statusText}`
      );
    }

    return await response.json();
  } catch (error) {
    if (error.name === "TypeError" && error.message.includes("fetch")) {
      throw new Error(
        "Cannot connect to the backend server. Is it running on " + BASE_URL + "?"
      );
    }
    throw error;
  }
}

/**
 * Analyze a GitHub repository.
 * @param {string} url - GitHub repository URL
 * @returns {Promise<Object>} Analysis results
 */
export async function analyzeGithub(url) {
  return apiFetch("/api/github", {
    method: "POST",
    body: JSON.stringify({ url }),
  });
}

/**
 * Analyze an uploaded ZIP file.
 * @param {File} file - ZIP file to upload
 * @returns {Promise<Object>} Analysis results
 */
export async function analyzeUpload(file) {
  const formData = new FormData();
  formData.append("file", file);

  const url = `${BASE_URL}/api/upload`;
  const response = await fetch(url, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(
      errorData.detail || `Upload failed: ${response.status} ${response.statusText}`
    );
  }

  return response.json();
}

/**
 * Get an AI explanation for a specific file.
 * @param {string} filename - File name
 * @param {string} content - File content
 * @param {boolean} simple - Use simple explanation mode
 * @returns {Promise<Object>} Explanation result
 */
export async function explainFile(filename, content, simple = false) {
  return apiFetch("/api/explain", {
    method: "POST",
    body: JSON.stringify({ filename, content, simple }),
  });
}

/**
 * Get all configured LLM prompts.
 * @returns {Promise<Object>} Prompts data
 */
export async function getPrompts() {
  return apiFetch("/api/prompts");
}

/**
 * Update a specific prompt.
 * @param {string} key - Prompt key
 * @param {string} value - New prompt value
 * @returns {Promise<Object>} Update result
 */
export async function updatePrompt(key, value) {
  return apiFetch(`/api/prompts/${key}`, {
    method: "PUT",
    body: JSON.stringify({ value }),
  });
}
