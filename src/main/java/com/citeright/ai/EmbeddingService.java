package com.citeright.ai;

import com.citeright.database.PaperEmbeddingDAO;

/**
 * Unified orchestrator and API gateway for CiteRight's local semantic intelligence.
 * Coordinates model downloading, local ONNX JNI execution, background queue scheduling, and SQLite vector caching.
 */
public class EmbeddingService {

    private static EmbeddingService instance;

    private final BgeM3ModelDownloader downloader;
    private final BgeM3EmbeddingEngine engine;
    private final EmbeddingQueueManager queueManager;
    private final PaperEmbeddingDAO embeddingDAO;

    private EmbeddingService() {
        this.downloader = new BgeM3ModelDownloader();
        this.engine = BgeM3EmbeddingEngine.getInstance();
        this.queueManager = EmbeddingQueueManager.getInstance();
        this.embeddingDAO = new PaperEmbeddingDAO();
    }

    public static synchronized EmbeddingService getInstance() {
        if (instance == null) {
            instance = new EmbeddingService();
        }
        return instance;
    }

    public BgeM3ModelDownloader getDownloader() { return downloader; }
    public BgeM3EmbeddingEngine getEngine() { return engine; }
    public EmbeddingQueueManager getQueueManager() { return queueManager; }
    public PaperEmbeddingDAO getEmbeddingDAO() { return embeddingDAO; }

    /**
     * Checks if BGE-M3 is downloaded and active.
     */
    public boolean isBgeM3Ready() {
        return downloader.isDownloaded() && engine.isLoaded();
    }

    /**
     * Asynchronously loads the local model and enqueues all unindexed papers in the database.
     * Prevents locking the main thread.
     */
    public void initializeEngine() {
        if (!downloader.isDownloaded()) return;
        new Thread(() -> {
            try {
                if (!engine.isLoaded()) {
                    engine.loadModel();
                }
                if (GeminiConfig.isBgeM3()) {
                    // Queue all papers that haven't been indexed yet
                    queueManager.enqueueAllUnindexedPapers();
                }
            } catch (Exception e) {
                System.err.println("[EmbeddingService] Failed to initialize neural engine: " + e.getMessage());
            }
        }, "SemanticAI-Initializer").start();
    }
}
