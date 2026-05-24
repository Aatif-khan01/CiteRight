package com.citeright.service;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.model.LibraryEntry;
import java.util.*;

/**
 * Detects duplicate library entries using a three-tier detection hierarchy:
 *
 * 1. DOI exact match → instant duplicate (100% confidence)
 * 2. Jaccard title similarity ≥ 0.85 + same year → likely duplicate
 * 3. BGE-M3 neural cosine similarity ≥ 0.90 → semantic duplicate (new!)
 *
 * The neural tier catches rephrased titles, translations, and title variations
 * that word-overlap methods miss entirely.
 */
public class DuplicateDetectionService {

    /** Neural similarity threshold for duplicate detection — high bar to avoid false positives */
    private static final double NEURAL_DUPLICATE_THRESHOLD = 0.90;

    /** Returns groups of duplicate entries */
    public List<List<LibraryEntry>> findDuplicates(List<LibraryEntry> entries) {
        List<List<LibraryEntry>> duplicateGroups = new ArrayList<>();
        boolean[] visited = new boolean[entries.size()];

        // Pre-load neural embeddings if available (single batch query)
        Map<Integer, float[]> cachedEmbeddings = null;
        boolean neuralReady = NeuralAvailability.isReady();
        if (neuralReady) {
            cachedEmbeddings = NeuralAvailability.getCachedEmbeddings();
            System.out.println("[DuplicateDetection] 🧠 Neural duplicate detection active (" + cachedEmbeddings.size() + " cached embeddings).");
        }

        for (int i = 0; i < entries.size(); i++) {
            if (visited[i]) continue;
            List<LibraryEntry> group = new ArrayList<>();
            group.add(entries.get(i));

            for (int j = i + 1; j < entries.size(); j++) {
                if (visited[j]) continue;
                if (isDuplicate(entries.get(i), entries.get(j), cachedEmbeddings, neuralReady)) {
                    group.add(entries.get(j));
                    visited[j] = true;
                }
            }
            if (group.size() > 1) {
                duplicateGroups.add(group);
                visited[i] = true;
            }
        }
        return duplicateGroups;
    }

    private boolean isDuplicate(LibraryEntry a, LibraryEntry b,
                                 Map<Integer, float[]> cachedEmbeddings, boolean neuralReady) {
        if (a.getPublication() == null || b.getPublication() == null) return false;

        // ── Tier 1: Exact DOI match (always wins) ───────────────────────────
        String doiA = a.getPublication().getDoi();
        String doiB = b.getPublication().getDoi();
        if (doiA != null && doiB != null && !doiA.isEmpty() && !doiB.isEmpty()) {
            if (doiA.equalsIgnoreCase(doiB)) return true;
        }

        // ── Tier 2: Jaccard title similarity + same year ────────────────────
        String titleA = normalize(a.getPublication().getTitle());
        String titleB = normalize(b.getPublication().getTitle());
        if (!titleA.isEmpty() && !titleB.isEmpty()) {
            double similarity = jaccardSimilarity(titleA, titleB);
            boolean sameYear = a.getPublication().getYear() == b.getPublication().getYear();
            if (similarity >= 0.85 && sameYear) return true;
        }

        // ── Tier 3: Neural semantic duplicate detection (BGE-M3) ─────────────
        if (neuralReady && cachedEmbeddings != null) {
            float[] embA = cachedEmbeddings.get(a.getId());
            float[] embB = cachedEmbeddings.get(b.getId());

            // On-the-fly computation for papers not yet cached
            if (embA == null) {
                String textA = buildText(a);
                embA = NeuralAvailability.embed(textA);
            }
            if (embB == null) {
                String textB = buildText(b);
                embB = NeuralAvailability.embed(textB);
            }

            if (embA != null && embB != null) {
                double neuralSim = BgeM3EmbeddingEngine.cosineSimilarity(embA, embB);
                if (neuralSim >= NEURAL_DUPLICATE_THRESHOLD) return true;
            }
        }

        return false;
    }

    private String buildText(LibraryEntry entry) {
        if (entry.getPublication() == null) return "";
        String title = entry.getPublication().getTitle();
        String abs = entry.getPublication().getAbstractText();
        return (title != null ? title : "") + " " + (abs != null ? abs : "");
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
    }
}
