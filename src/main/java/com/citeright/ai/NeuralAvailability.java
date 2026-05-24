package com.citeright.ai;

import com.citeright.database.PaperEmbeddingDAO;
import java.util.Map;

/**
 * Centralized readiness check for CiteRight's local neural AI engine.
 *
 * Instead of scattering {@code GeminiConfig.isBgeM3() && BgeM3EmbeddingEngine.getInstance().isLoaded()}
 * across every service, all consumers call {@code NeuralAvailability.isReady()}.
 *
 * This is intentionally a tiny utility — not a God-class, not a gateway,
 * not a service orchestrator. Just a single source of truth for
 * "can I use neural embeddings right now?"
 */
public final class NeuralAvailability {

    private NeuralAvailability() {} // utility class — no instances

    /**
     * Returns true if the BGE-M3 local neural engine is fully operational:
     *   1. User has opted in to BGE-M3 embeddings (config flag)
     *   2. ONNX model + tokenizer are downloaded
     *   3. Model is loaded into memory and ready for inference
     */
    public static boolean isReady() {
        return GeminiConfig.isBgeM3()
                && BgeM3EmbeddingEngine.getInstance().isModelDownloaded();
    }

    /**
     * Returns true if the BGE-M3 model files are downloaded but not yet loaded.
     * Useful for UI hints like "AI available — activate to enable neural features."
     */
    public static boolean isDownloadedButNotLoaded() {
        return GeminiConfig.isBgeM3()
                && BgeM3EmbeddingEngine.getInstance().isModelDownloaded()
                && !BgeM3EmbeddingEngine.getInstance().isLoaded();
    }

    /**
     * Convenience: loads all cached BGE-M3 paper embeddings in one fast batch.
     * Returns a map of paperId → float[1024] embedding vector.
     *
     * Callers should use this instead of creating their own PaperEmbeddingDAO instances.
     */
    public static Map<Integer, float[]> getCachedEmbeddings() {
        return new PaperEmbeddingDAO().getAllCachedEmbeddings("bge-m3", "v1");
    }

    /**
     * Computes a fresh embedding for the given text using the local BGE-M3 engine.
     * Returns null if the engine is not ready or inference fails.
     */
    public static float[] embed(String text) {
        if (!isReady()) return null;
        return BgeM3EmbeddingEngine.getInstance().getEmbedding(text);
    }
}
