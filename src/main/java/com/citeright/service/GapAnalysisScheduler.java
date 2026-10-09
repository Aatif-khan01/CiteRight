package com.citeright.service;

import com.citeright.database.ResearchGapDAO;
import com.citeright.model.ResearchGap;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Debounced background scheduler for gap analysis.
 *
 * Triggers analysis automatically when the library changes (papers added/removed),
 * but debounces to avoid running analysis on every single import.
 *
 * Behavior:
 *   - On library change → schedule analysis after DEBOUNCE_DELAY
 *   - If another change comes within the delay → reset the timer
 *   - Only one analysis runs at a time (new triggers queue, not parallel)
 *   - Results are persisted to SQLite via ResearchGapDAO
 *   - Optionally notifies a UI callback when analysis completes
 */
public class GapAnalysisScheduler {

    /** Delay before running analysis after last trigger (milliseconds) */
    private static final long DEBOUNCE_DELAY_MS = 5000; // 5 seconds

    private final ResearchGapEngine engine;
    private final ResearchGapDAO gapDAO;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean analysisRunning;

    private ScheduledFuture<?> pendingAnalysis;
    private Consumer<List<ResearchGap>> completionCallback;

    public GapAnalysisScheduler() {
        this.engine = new ResearchGapEngine();
        this.gapDAO = new ResearchGapDAO();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GapAnalysis-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.analysisRunning = new AtomicBoolean(false);
    }

    /**
     * Set a callback that fires when analysis completes.
     * The callback receives the list of evaluated gaps.
     * Called on the scheduler thread — use Platform.runLater() for UI updates.
     */
    public void setCompletionCallback(Consumer<List<ResearchGap>> callback) {
        this.completionCallback = callback;
    }

    /**
     * Trigger a debounced analysis.
     * If called multiple times within DEBOUNCE_DELAY_MS, only the last trigger runs.
     */
    public synchronized void triggerAnalysis() {
        // Cancel any pending debounced trigger
        if (pendingAnalysis != null && !pendingAnalysis.isDone()) {
            pendingAnalysis.cancel(false);
        }

        // Schedule new analysis after debounce delay
        pendingAnalysis = scheduler.schedule(this::runAnalysis, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        System.out.println("[GapAnalysisScheduler] Analysis scheduled (debounce: " + DEBOUNCE_DELAY_MS + "ms)");
    }

    /**
     * Run analysis immediately (bypasses debounce).
     * Used for manual "Analyze Now" button clicks.
     */
    public void triggerImmediate() {
        if (analysisRunning.get()) {
            System.out.println("[GapAnalysisScheduler] Analysis already running, skipping immediate trigger");
            return;
        }
        scheduler.submit(this::runAnalysis);
    }

    /**
     * Get the underlying engine (for accessing cached results from UI).
     */
    public ResearchGapEngine getEngine() {
        return engine;
    }

    /**
     * Check if analysis is currently running.
     */
    public boolean isRunning() {
        return analysisRunning.get();
    }

    /**
     * Shutdown the scheduler.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        System.out.println("[GapAnalysisScheduler] Shutdown");
    }

    // ─── Internal ───────────────────────────────────────────────────────

    private void runAnalysis() {
        if (!analysisRunning.compareAndSet(false, true)) {
            System.out.println("[GapAnalysisScheduler] Analysis already running, skipping");
            return;
        }

        try {
            System.out.println("[GapAnalysisScheduler] Starting background gap analysis...");
            long start = System.currentTimeMillis();

            List<ResearchGap> results = engine.analyze();

            // Persist results
            if (!results.isEmpty()) {
                gapDAO.saveGaps(results);
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("[GapAnalysisScheduler] Analysis complete in %dms: %d gaps%n",
                elapsed, results.size());

            // Notify callback
            if (completionCallback != null) {
                try {
                    completionCallback.accept(results);
                } catch (Exception e) {
                    System.err.println("[GapAnalysisScheduler] Callback error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[GapAnalysisScheduler] Analysis failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            analysisRunning.set(false);
        }
    }
}
