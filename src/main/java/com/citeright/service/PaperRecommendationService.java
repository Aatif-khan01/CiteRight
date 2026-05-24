package com.citeright.service;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.model.LibraryEntry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * "More Like This" — neural-only paper recommendation engine.
 *
 * Given a paper, finds the most semantically similar papers in the user's library
 * using BGE-M3 dense embeddings and cosine similarity.
 *
 * This feature is ONLY available when NeuralAvailability.isReady() is true.
 * There is no TF-IDF fallback — this is a neural-exclusive capability
 * that incentivizes BGE-M3 activation.
 */
public class PaperRecommendationService {

    private static final int DEFAULT_TOP_N = 10;

    /**
     * A recommendation result pairing a library entry with its similarity score.
     */
    public record ScoredRecommendation(LibraryEntry entry, double similarity) {
        /** Score as a percentage string, e.g. "87%" */
        public String similarityPercent() {
            return Math.round(similarity * 100) + "%";
        }
    }

    /**
     * Finds the top N most semantically similar papers to the given paper.
     *
     * @param targetEntry The paper to find recommendations for
     * @param allEntries  All library entries to search across
     * @param topN        Number of recommendations to return
     * @return List of scored recommendations, sorted by similarity (highest first)
     */
    public List<ScoredRecommendation> findSimilar(LibraryEntry targetEntry,
                                                   List<LibraryEntry> allEntries,
                                                   int topN) {
        if (!NeuralAvailability.isReady()) {
            return Collections.emptyList(); // Neural-only feature
        }

        if (targetEntry == null || allEntries == null || allEntries.size() <= 1) {
            return Collections.emptyList();
        }

        // Get target embedding
        float[] targetEmbedding = getEmbeddingForEntry(targetEntry);
        if (targetEmbedding == null) {
            System.err.println("[Recommendation] Cannot compute embedding for target paper.");
            return Collections.emptyList();
        }

        // Load all cached embeddings in one batch
        Map<Integer, float[]> cachedEmbeddings = NeuralAvailability.getCachedEmbeddings();

        // Score every other paper
        List<ScoredRecommendation> results = new ArrayList<>();
        for (LibraryEntry entry : allEntries) {
            // Skip the target paper itself
            if (entry.getId() == targetEntry.getId()) continue;

            float[] entryEmbedding = cachedEmbeddings.get(entry.getId());

            // On-the-fly computation for papers not yet indexed
            if (entryEmbedding == null) {
                entryEmbedding = getEmbeddingForEntry(entry);
            }

            if (entryEmbedding != null) {
                double similarity = BgeM3EmbeddingEngine.cosineSimilarity(targetEmbedding, entryEmbedding);
                if (similarity > 0.10) { // Low threshold — let the UI show only meaningful results
                    results.add(new ScoredRecommendation(entry, similarity));
                }
            }
        }

        // Sort by similarity descending and take top N
        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        System.out.println("[Recommendation] Found " + results.size() + " similar papers for \""
                + (targetEntry.getPublication() != null ? targetEntry.getPublication().getTitle() : "Unknown") + "\"");

        return results.stream().limit(topN).collect(Collectors.toList());
    }

    /**
     * Convenience method with default top N.
     */
    public List<ScoredRecommendation> findSimilar(LibraryEntry targetEntry, List<LibraryEntry> allEntries) {
        return findSimilar(targetEntry, allEntries, DEFAULT_TOP_N);
    }

    /**
     * Checks if the recommendation feature is available.
     * UI should use this to show/hide the "Find Similar Papers" button.
     */
    public static boolean isAvailable() {
        return NeuralAvailability.isReady();
    }

    /**
     * Gets or computes the embedding for a library entry.
     */
    private float[] getEmbeddingForEntry(LibraryEntry entry) {
        if (entry.getPublication() == null) return null;

        // Check if Publication already has an in-memory embedding
        float[] existing = entry.getPublication().getEmbedding();
        if (existing != null) return existing;

        // Build text and compute on the fly
        String title = entry.getPublication().getTitle();
        String abstractText = entry.getPublication().getAbstractText();
        String text = (title != null ? title : "") + " " + (abstractText != null ? abstractText : "");

        return NeuralAvailability.embed(text.trim());
    }
}
