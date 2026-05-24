package com.citeright.ai;

import com.citeright.database.PaperDAO;
import com.citeright.database.PaperEmbeddingDAO;
import com.citeright.database.SQLiteDatabaseManager;
import com.citeright.model.Publication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages a low-priority background worker queue that lazily indexes papers.
 * Prevents CPU lockups, maintains laptop battery health, and updates the local semantic index in the background.
 */
public class EmbeddingQueueManager {

    public interface QueueListener {
        void onQueueUpdated(int remainingSize, boolean isIndexing);
    }

    private static EmbeddingQueueManager instance;

    private final ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
    private final Set<Integer> activeSet = ConcurrentHashMap.newKeySet();
    private final java.util.List<QueueListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final PaperEmbeddingDAO embeddingDAO = new PaperEmbeddingDAO();
    private final PaperDAO paperDAO = new PaperDAO();
    private final BgeM3EmbeddingEngine engine = BgeM3EmbeddingEngine.getInstance();
    private final SQLiteDatabaseManager dbManager = SQLiteDatabaseManager.getInstance();

    private Thread backgroundThread;
    private volatile boolean running = true;
    private volatile boolean isIndexing = false;

    private EmbeddingQueueManager() {
        startWorker();
    }

    public static synchronized EmbeddingQueueManager getInstance() {
        if (instance == null) {
            instance = new EmbeddingQueueManager();
        }
        return instance;
    }

    public void addListener(QueueListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(QueueListener listener) {
        listeners.remove(listener);
    }

    private void notifyQueueUpdated() {
        int size = getQueueSize();
        boolean active = isIndexing();
        for (QueueListener l : listeners) {
            try {
                l.onQueueUpdated(size, active);
            } catch (Exception e) {
                System.err.println("[EmbeddingQueueManager] Error notifying queue listener: " + e.getMessage());
            }
        }
    }

    /**
     * Enqueues a paper ID to be indexed.
     */
    public void enqueue(int paperId) {
        if (activeSet.add(paperId)) {
            queue.add(paperId);
            System.out.println("[EmbeddingQueueManager] Enqueued paper " + paperId + " for semantic indexing.");
            notifyQueueUpdated();
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isIndexing() {
        return isIndexing;
    }

    /**
     * Finds all papers in the SQLite database that have not been embedded using BGE-M3 (v1) yet,
     * and batches them into the queue.
     */
    public synchronized void enqueueAllUnindexedPapers() {
        if (!dbManager.isAvailable()) return;

        String sql = """
            SELECT id FROM papers
            WHERE id NOT IN (
                SELECT paper_id FROM paper_embeddings
                WHERE model_name = 'bge-m3' AND model_version = 'v1'
            )
        """;

        int enqueuedCount = 0;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int paperId = rs.getInt("id");
                    enqueue(paperId);
                    enqueuedCount++;
                }
            }
        } catch (SQLException e) {
            System.err.println("[EmbeddingQueueManager] Error loading unindexed papers: " + e.getMessage());
        }

        if (enqueuedCount > 0) {
            System.out.println("[EmbeddingQueueManager] Enqueued " + enqueuedCount + " unindexed papers in background.");
        }
    }

    private void startWorker() {
        running = true;
        backgroundThread = new Thread(this::runIndexingLoop, "SemanticAI-Indexer");
        backgroundThread.setDaemon(true);
        backgroundThread.setPriority(Thread.MIN_PRIORITY); // Keep priority lowest so main thread stays perfectly fluid
        backgroundThread.start();
        System.out.println("[EmbeddingQueueManager] Background semantic indexing thread started.");
    }

    private void runIndexingLoop() {
        while (running) {
            try {
                // Background worker only indexes if BGE-M3 is active AND loaded
                if (GeminiConfig.isBgeM3() && engine.isLoaded() && !queue.isEmpty()) {
                    Integer paperId = queue.poll();
                    if (paperId != null) {
                        activeSet.remove(paperId);
                        isIndexing = true;
                        notifyQueueUpdated();
                        
                        try {
                            processPaper(paperId);
                        } catch (Exception e) {
                            System.err.println("[EmbeddingQueueManager] Failed to index paper " + paperId + ": " + e.getMessage());
                        }
                        
                        isIndexing = false;
                        notifyQueueUpdated();
                        // Cooldown sleep (100ms) to ensure CPU cores can throttle and cool down
                        Thread.sleep(100);
                    }
                } else {
                    isIndexing = false;
                    // Idle sleep
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                System.out.println("[EmbeddingQueueManager] Background thread interrupted.");
                break;
            } catch (Exception e) {
                System.err.println("[EmbeddingQueueManager] Indexing loop encountered error: " + e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {} // recovery sleep
            }
        }
    }

    private void processPaper(int paperId) {
        Publication pub = paperDAO.findById(paperId);
        if (pub == null) return;

        // Skip papers with no title
        String title = pub.getTitle();
        if (title == null || title.isBlank()) return;

        String abstractText = pub.getAbstractText() != null ? pub.getAbstractText() : "";
        
        // Build the text to embed: Title + Abstract
        String contentToEmbed = title + " " + abstractText;

        // Generate embedding via BGE-M3 local engine
        float[] vector = engine.getEmbedding(contentToEmbed);

        if (vector != null && vector.length == 1024) {
            // Save embedding vector in SQLite
            embeddingDAO.saveEmbedding(paperId, "bge-m3", "v1", vector);
            System.out.println("[EmbeddingQueueManager] Successfully cached embedding for paper " + paperId + ": \"" + title + "\"");
        } else {
            System.err.println("[EmbeddingQueueManager] Embedding calculation returned null or invalid length for paper " + paperId);
        }
    }

    /**
     * Clean shutdown of the worker thread.
     */
    public synchronized void shutdown() {
        running = false;
        if (backgroundThread != null) {
            backgroundThread.interrupt();
            backgroundThread = null;
        }
        System.out.println("[EmbeddingQueueManager] Background thread shutdown.");
    }
}
