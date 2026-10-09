package com.citeright.ai;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Manages AI configuration for CiteRight.
 * Supports two providers:
 *   1. Google Gemini (cloud, free tier) — default
 *   2. Ollama (local, 100% private)
 * 
 * Stores settings in ~/.citeright.config
 */
public class GeminiConfig {

    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".citeright.config");

    // Keys
    private static final String KEY_GEMINI_API    = "gemini.api.key";
    private static final String KEY_AI_PROVIDER   = "ai.provider";       // "gemini" or "ollama"
    private static final String KEY_OLLAMA_MODEL  = "ollama.model";      // e.g. "llama3.2"
    private static final String KEY_OLLAMA_URL    = "ollama.url";        // e.g. "http://localhost:11434"
    private static final String KEY_EMBEDDING_PROVIDER = "ai.embedding.provider"; // "tfidf" or "bgem3"
    private static final String KEY_EMBEDDING_PROMPTED = "ai.embedding.prompted"; // "true" if prompted once
    private static final String KEY_TOUR_COMPLETED = "tour.completed";

    // Defaults
    private static final String DEFAULT_PROVIDER    = "gemini";
    private static final String DEFAULT_OLLAMA_MODEL = "llama3.2";
    private static final String DEFAULT_OLLAMA_URL   = "http://localhost:11434";
    private static final String DEFAULT_EMBEDDING_PROVIDER = "tfidf";

    // ── Gemini API Key ──────────────────────────────────────────────────────

    public static String getApiKey() {
        return getProperty(KEY_GEMINI_API);
    }

    public static boolean saveApiKey(String apiKey) {
        return setProperty(KEY_GEMINI_API, apiKey != null ? apiKey.trim() : "");
    }

    public static boolean isConfigured() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }

    // ── AI Provider ─────────────────────────────────────────────────────────

    /** Returns "gemini" or "ollama" */
    public static String getProvider() {
        String provider = getProperty(KEY_AI_PROVIDER);
        return provider != null ? provider : DEFAULT_PROVIDER;
    }

    public static boolean setProvider(String provider) {
        return setProperty(KEY_AI_PROVIDER, provider);
    }

    public static boolean isGemini() {
        return "gemini".equalsIgnoreCase(getProvider());
    }

    public static boolean isOllama() {
        return "ollama".equalsIgnoreCase(getProvider());
    }

    // ── Ollama Settings ─────────────────────────────────────────────────────

    public static String getOllamaModel() {
        String model = getProperty(KEY_OLLAMA_MODEL);
        return model != null ? model : DEFAULT_OLLAMA_MODEL;
    }

    public static boolean setOllamaModel(String model) {
        return setProperty(KEY_OLLAMA_MODEL, model != null ? model.trim() : DEFAULT_OLLAMA_MODEL);
    }

    public static String getOllamaUrl() {
        String url = getProperty(KEY_OLLAMA_URL);
        return url != null ? url : DEFAULT_OLLAMA_URL;
    }

    public static boolean setOllamaUrl(String url) {
        return setProperty(KEY_OLLAMA_URL, url != null ? url.trim() : DEFAULT_OLLAMA_URL);
    }

    // ── Embedding Provider Settings ──────────────────────────────────────────

    public static String getEmbeddingProvider() {
        String provider = getProperty(KEY_EMBEDDING_PROVIDER);
        return provider != null ? provider : DEFAULT_EMBEDDING_PROVIDER;
    }

    public static boolean setEmbeddingProvider(String provider) {
        return setProperty(KEY_EMBEDDING_PROVIDER, provider != null ? provider.trim().toLowerCase() : DEFAULT_EMBEDDING_PROVIDER);
    }

    public static boolean isBgeM3() {
        return "bgem3".equalsIgnoreCase(getEmbeddingProvider());
    }

    public static boolean isEmbeddingPrompted() {
        return "true".equalsIgnoreCase(getProperty(KEY_EMBEDDING_PROMPTED));
    }

    public static boolean setEmbeddingPrompted(boolean prompted) {
        return setProperty(KEY_EMBEDDING_PROMPTED, String.valueOf(prompted));
    }

    // ── Tour Settings ────────────────────────────────────────────────────────

    public static boolean isTourCompleted() {
        return "true".equalsIgnoreCase(getProperty(KEY_TOUR_COMPLETED));
    }

    public static boolean setTourCompleted(boolean completed) {
        return setProperty(KEY_TOUR_COMPLETED, String.valueOf(completed));
    }

    // ── Internal Helpers ────────────────────────────────────────────────────

    private static String getProperty(String key) {
        if (!Files.exists(CONFIG_PATH)) return null;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            String val = props.getProperty(key);
            if (val != null && !val.trim().isEmpty()) return val.trim();
        } catch (IOException e) {
            System.err.println("[GeminiConfig] Failed to read config: " + e.getMessage());
        }
        return null;
    }

    private static boolean setProperty(String key, String value) {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) { /* overwrite */ }
        }
        props.setProperty(key, value);
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "CiteRight AI Configuration");
            return true;
        } catch (IOException e) {
            System.err.println("[GeminiConfig] Failed to save config: " + e.getMessage());
            return false;
        }
    }
}
