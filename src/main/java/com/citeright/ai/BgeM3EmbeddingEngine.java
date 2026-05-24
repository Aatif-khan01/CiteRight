package com.citeright.ai;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the in-process execution of the BGE-M3 multilingual neural embedding model.
 * Uses Deep Java Library (DJL) and ONNX Runtime under the hood for native CPU inference.
 * Thread-safe execution wrapper.
 */
public class BgeM3EmbeddingEngine implements AutoCloseable {

    private static BgeM3EmbeddingEngine instance;

    private final Path modelDirectory;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean loaded = false;

    private BgeM3EmbeddingEngine() {
        String homeDir = System.getProperty("user.home");
        this.modelDirectory = Paths.get(homeDir, ".citeright", "models", "bge-m3");
    }

    public static synchronized BgeM3EmbeddingEngine getInstance() {
        if (instance == null) {
            instance = new BgeM3EmbeddingEngine();
        }
        return instance;
    }

    public boolean isModelDownloaded() {
        File modelFile = modelDirectory.resolve("model.onnx").toFile();
        File tokenizerFile = modelDirectory.resolve("tokenizer.json").toFile();
        return modelFile.exists() && tokenizerFile.exists();
    }

    public boolean isLoaded() { return loaded; }

    /**
     * Loads the model and tokenizer from ~/.citeright/models/bge-m3.
     * Synchronization guarantees thread safety during heavy JNI allocations.
     */
    public synchronized void loadModel() throws Exception {
        if (loaded) return;
        if (!isModelDownloaded()) {
            throw new java.io.FileNotFoundException("BGE-M3 model files are not fully downloaded yet.");
        }

        System.out.println("[BGE-M3 Engine] Initializing local ONNX runtime...");

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(modelDirectory)
                .optEngine("OnnxRuntime")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build();

        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();
        this.loaded = true;

        System.out.println("[BGE-M3 Engine] ONNX Model and Tokenizer loaded successfully. Ready for semantic inference.");
    }

    /**
     * Computes the 1024-dimensional dense semantic vector for a text string.
     * Thread-safe via synchronized wrapping since Predictor is not thread-safe.
     */
    public synchronized float[] getEmbedding(String text) {
        if (!loaded) {
            try {
                loadModel();
            } catch (Exception e) {
                System.err.println("[BGE-M3 Engine] Auto-load failed: " + e.getMessage());
                return null;
            }
        }

        if (text == null || text.isBlank()) {
            return new float[1024]; // return empty vector for empty input
        }

        try {
            // Trim / truncate excessively long text (BGE-M3 handles up to 8192 tokens,
            // but for safety and speed, we cap abstract-sized text processing)
            if (text.length() > 5000) {
                text = text.substring(0, 5000);
            }
            return predictor.predict(text);
        } catch (Exception e) {
            System.err.println("[BGE-M3 Engine] Inference failed for input: " + e.getMessage());
            return null;
        }
    }

    /**
     * Measures the semantic alignment of two embedding vectors using cosine similarity.
     * Clamped between 0.0 (no overlap) and 1.0 (conceptually identical).
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        double sim = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(0.0, Math.min(1.0, sim)); // clamp to [0.0, 1.0] range
    }

    @Override
    public synchronized void close() {
        if (predictor != null) {
            predictor.close();
            predictor = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        loaded = false;
        System.out.println("[BGE-M3 Engine] Resources released.");
    }
}
