package com.citeright.ai;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the in-process execution of the BGE-M3 multilingual neural embedding model.
 * Uses Deep Java Library (DJL) and ONNX Runtime under the hood for native CPU inference.
 * Custom translator handles tokenization directly with OrtNDManager, preventing native Rust crashes.
 * Thread-safe execution wrapper.
 */
public class BgeM3EmbeddingEngine implements AutoCloseable {

    private static BgeM3EmbeddingEngine instance;

    private final Path modelDirectory;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private HuggingFaceTokenizer tokenizer;
    private boolean loaded = false;
    private volatile boolean loadFailed = false;
    private volatile String failureMessage = null;

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
     * Returns true if the native runtime permanently failed to load.
     * Once set, the engine will never attempt to load again (requires app restart).
     */
    public boolean isLoadFailed() { return loadFailed; }

    /**
     * Returns a human-readable description of why the engine failed to load,
     * or null if no failure has occurred.
     */
    public String getFailureMessage() { return failureMessage; }

    /**
     * Loads the model and tokenizer from ~/.citeright/models/bge-m3.
     * Synchronization guarantees thread safety during heavy JNI allocations.
     */
    public synchronized void loadModel() throws Exception {
        if (loaded) return;
        if (loadFailed) return; // Permanently failed — don't retry
        if (!isModelDownloaded()) {
            throw new java.io.FileNotFoundException("BGE-M3 model files are not fully downloaded yet.");
        }

        System.out.println("[BGE-M3 Engine] Initializing local ONNX runtime...");

        try {
            Path tokenizerPath = modelDirectory.resolve("tokenizer.json");
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optMaxLength(512)
                    .optTruncation(true)
                    .optPadding(false)
                    .build();

            Translator<String, float[]> translator = new Translator<>() {
                @Override
                public Batchifier getBatchifier() {
                    // Disable batchifier to prevent StackBatchifier and native RustLibrary tensorOf crashes
                    return null;
                }

                @Override
                public NDList processInput(TranslatorContext ctx, String input) {
                    Encoding encoding = tokenizer.encode(input != null ? input : "");
                    NDManager manager = ctx.getNDManager();

                    long[] ids = encoding.getIds();
                    long[] attentionMask = encoding.getAttentionMask();
                    ai.djl.ndarray.types.Shape shape = new ai.djl.ndarray.types.Shape(1, ids.length);

                    NDArray inputIdsArray = manager.create(ids, shape);
                    inputIdsArray.setName("input_ids");

                    NDArray attentionMaskArray = manager.create(attentionMask, shape);
                    attentionMaskArray.setName("attention_mask");

                    return new NDList(inputIdsArray, attentionMaskArray);
                }

                @Override
                public float[] processOutput(TranslatorContext ctx, NDList list) {
                    if (list == null || list.isEmpty()) {
                        return new float[1024];
                    }

                    NDArray output = list.get("sentence_embedding");
                    if (output == null) {
                        output = list.get(0);
                    }

                    float[] allFloats = output.toFloatArray();
                    if (allFloats == null || allFloats.length == 0) {
                        return new float[1024];
                    }

                    float[] raw;
                    if (allFloats.length >= 1024) {
                        // Extract [CLS] token embedding / sentence embedding (first 1024 dimensions)
                        raw = java.util.Arrays.copyOfRange(allFloats, 0, 1024);
                    } else {
                        raw = allFloats;
                    }

                    return normalizeVector(raw);
                }
            };

            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(modelDirectory)
                    .optEngine("OnnxRuntime")
                    .optTranslator(translator)
                    .build();

            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();
            this.loaded = true;

            System.out.println("[BGE-M3 Engine] ONNX Model and Tokenizer loaded successfully. Ready for semantic inference.");
        } catch (UnsatisfiedLinkError e) {
            // Native DLL failed to load — typically missing Visual C++ Redistributable
            loadFailed = true;
            failureMessage = "Native library failed to load. Please install the Visual C++ Redistributable from Microsoft: " + e.getMessage();
            System.err.println("[BGE-M3 Engine] PERMANENT FAILURE — Native library load failed: " + e.getMessage());
            System.err.println("[BGE-M3 Engine] Install Visual C++ Redistributable: https://aka.ms/vs/17/release/vc_redist.x64.exe");
            throw new RuntimeException(failureMessage, e);
        } catch (NoClassDefFoundError e) {
            // Class resolution failed — a dependency is missing or incompatible
            loadFailed = true;
            failureMessage = "Required AI runtime class not found: " + e.getMessage();
            System.err.println("[BGE-M3 Engine] PERMANENT FAILURE — Missing class: " + e.getMessage());
            throw new RuntimeException(failureMessage, e);
        } catch (ExceptionInInitializerError e) {
            // Static initializer in a native wrapper class failed
            loadFailed = true;
            failureMessage = "AI engine initialization failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            System.err.println("[BGE-M3 Engine] PERMANENT FAILURE — Initializer error: " + e.getMessage());
            throw new RuntimeException(failureMessage, e);
        }
    }

    /**
     * Computes the 1024-dimensional dense semantic vector for a text string.
     * Thread-safe via synchronized wrapping since Predictor is not thread-safe.
     */
    public synchronized float[] getEmbedding(String text) {
        if (loadFailed) return null; // Engine permanently failed — skip silently
        if (!loaded) {
            try {
                loadModel();
            } catch (Throwable e) {
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * L2 normalizes an embedding vector.
     */
    private static float[] normalizeVector(float[] vector) {
        if (vector == null || vector.length == 0) return vector;
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0.0) {
            float[] normalized = new float[vector.length];
            for (int i = 0; i < vector.length; i++) {
                normalized[i] = (float) (vector[i] / norm);
            }
            return normalized;
        }
        return vector;
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
        if (tokenizer != null) {
            tokenizer.close();
            tokenizer = null;
        }
        loaded = false;
        System.out.println("[BGE-M3 Engine] Resources released.");
    }
}

