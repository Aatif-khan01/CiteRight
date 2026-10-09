package com.citeright.service;

import com.citeright.formatter.BibTeXFormatter;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Background service that automatically syncs the user's library
 * to a BibTeX file (~/.citeright/library.bib).
 * 
 * - Runs as a daemon thread
 * - Uses a 3-second debounce timer to batch rapid changes
 * - Writes atomically (temp file → rename) to prevent corruption
 * - Escapes LaTeX special characters for safe compilation
 */
public class BibSyncService {

    private static BibSyncService instance;
    private final Path bibFilePath;
    private final Path tempFilePath;
    private final BibTeXFormatter formatter;
    private Timer debounceTimer;
    private boolean enabled = true;

    private BibSyncService() {
        String home = System.getProperty("user.home");
        bibFilePath = Paths.get(home, ".citeright", "library.bib");
        tempFilePath = Paths.get(home, ".citeright", "library.bib.tmp");
        formatter = new BibTeXFormatter();

        System.out.println("[BibSync] Service initialized. Output: " + bibFilePath);
    }

    public static synchronized BibSyncService getInstance() {
        if (instance == null) {
            instance = new BibSyncService();
        }
        return instance;
    }

    /**
     * Notifies the service that the library has changed.
     * Triggers a debounced write (waits 3 seconds for more changes before writing).
     */
    public void notifyChange() {
        if (!enabled) return;

        // Cancel any pending timer
        if (debounceTimer != null) {
            debounceTimer.cancel();
        }

        debounceTimer = new Timer("BibSync-Debounce", true);
        debounceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                syncNow();
            }
        }, 3000); // 3-second debounce
    }

    /**
     * Immediately exports the entire library to the .bib file.
     * Called by the debounce timer and on application shutdown.
     */
    public synchronized void syncNow() {
        try {
            LibraryService libraryService = new LibraryService();
            List<Publication> publications = libraryService.getAllActivePublications();

            if (publications.isEmpty()) {
                System.out.println("[BibSync] Library is empty, skipping sync.");
                return;
            }

            StringBuilder bibContent = new StringBuilder();
            bibContent.append("% CiteRight Library Export\n");
            bibContent.append("% Auto-generated — do not edit manually\n");
            bibContent.append("% Total entries: ").append(publications.size()).append("\n\n");

            for (Publication pub : publications) {
                String entry = formatter.format(pub);
                entry = escapeLatexSpecials(entry);
                bibContent.append(entry).append("\n\n");
            }

            // Atomic write: write to temp file, then rename
            Files.writeString(tempFilePath, bibContent.toString());
            Files.move(tempFilePath, bibFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            System.out.println("[BibSync] Synced " + publications.size() + " entries to " + bibFilePath);

        } catch (Exception e) {
            System.err.println("[BibSync] Sync failed: " + e.getMessage());
            // Clean up temp file if it exists
            try { Files.deleteIfExists(tempFilePath); } catch (IOException ignored) {}
        }
    }

    /**
     * Escapes LaTeX special characters that could break compilation.
     * Note: We only escape characters OUTSIDE of BibTeX field values
     * (inside braces, most chars are already safe).
     */
    private String escapeLatexSpecials(String text) {
        if (text == null) return "";
        // These replacements are safe within BibTeX brace-delimited values
        // We avoid double-escaping by checking for existing backslashes
        text = text.replace("&", "\\&");
        text = text.replace("%", "\\%");
        text = text.replace("_", "\\_");
        text = text.replace("#", "\\#");
        // Fix double-escaping if the formatter already escaped
        text = text.replace("\\\\&", "\\&");
        text = text.replace("\\\\%", "\\%");
        text = text.replace("\\\\_", "\\_");
        return text;
    }

    /**
     * Returns the path to the synced .bib file.
     */
    public Path getBibFilePath() {
        return bibFilePath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println("[BibSync] " + (enabled ? "Enabled" : "Disabled"));
    }

    /**
     * Flushes any pending sync. Call this on application shutdown.
     */
    public void shutdown() {
        if (debounceTimer != null) {
            debounceTimer.cancel();
        }
        syncNow(); // Final flush
        System.out.println("[BibSync] Shutdown complete.");
    }
}
