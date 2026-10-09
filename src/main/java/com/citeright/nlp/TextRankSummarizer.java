package com.citeright.nlp;

import java.util.*;

/**
 * TextRank-based extractive summarizer.
 *
 * Works like Google's PageRank — but for sentences:
 * 1. Splits text into sentences
 * 2. Computes TF-IDF cosine similarity between every pair of sentences
 * 3. Builds a sentence "similarity graph"
 * 4. Scores each sentence by how similar it is to all others (centrality)
 * 5. Returns the top N sentences as a summary
 *
 * Completely local, zero dependencies, instant. No API keys, no internet.
 */
public class TextRankSummarizer {

    private static final int MAX_ITERATIONS = 30;
    private static final double DAMPING = 0.85;
    private static final double CONVERGENCE_THRESHOLD = 0.0001;

    /**
     * Summarizes the given text into at most maxSentences bullet points.
     *
     * @param text         The abstract or body text to summarize
     * @param maxSentences Maximum number of sentences to return
     * @return List of key sentences in their original order (not ranked order)
     */
    public static List<String> summarize(String text, int maxSentences) {
        if (text == null || text.isBlank()) return List.of("No abstract available.");

        // Step 1: Split into sentences
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= maxSentences) return sentences; // short text — return all

        // Step 2: Build TF-IDF vectors for each sentence using our existing engine
        // We use a mini in-memory TF-IDF model over just these sentences
        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(sentences);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (String s : sentences) {
            vectors.add(engine.computeTfIdfVector(s));
        }

        // Step 3: Build similarity matrix (NxN)
        int n = sentences.size();
        double[][] similarity = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    similarity[i][j] = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                }
            }
        }

        // Step 4: Normalize rows (so each row sums to 1 — required for PageRank)
        for (int i = 0; i < n; i++) {
            double rowSum = Arrays.stream(similarity[i]).sum();
            if (rowSum > 0) {
                for (int j = 0; j < n; j++) {
                    similarity[i][j] /= rowSum;
                }
            }
        }

        // Step 5: Power iteration (TextRank = PageRank on sentence graph)
        double[] scores = new double[n];
        Arrays.fill(scores, 1.0 / n);

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[] newScores = new double[n];
            for (int i = 0; i < n; i++) {
                newScores[i] = (1.0 - DAMPING) / n;
                for (int j = 0; j < n; j++) {
                    newScores[i] += DAMPING * similarity[j][i] * scores[j];
                }
            }
            // Check convergence
            double delta = 0;
            for (int i = 0; i < n; i++) delta += Math.abs(newScores[i] - scores[i]);
            scores = newScores;
            if (delta < CONVERGENCE_THRESHOLD) break;
        }

        // Step 6: Get top-N sentence indices, sorted by original position
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        final double[] finalScores = scores;
        Arrays.sort(indices, (a, b) -> Double.compare(finalScores[b], finalScores[a]));

        // Take top maxSentences, then restore original order
        List<Integer> topIndices = new ArrayList<>(Arrays.asList(indices).subList(0, maxSentences));
        Collections.sort(topIndices);

        List<String> result = new ArrayList<>();
        for (int idx : topIndices) {
            String s = sentences.get(idx).trim();
            if (!s.isEmpty()) result.add(s);
        }

        return result;
    }

    /**
     * Splits text into individual sentences using common delimiters.
     * Handles abbreviations (e.g., "et al.", "Fig.", "Eq.") to avoid false splits.
     */
    private static List<String> splitSentences(String text) {
        // Basic sentence splitting — handles common academic text patterns
        String[] parts = text.split("(?<=[.!?])\\s+(?=[A-Z])");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            // Skip very short fragments (likely abbreviation artifacts)
            if (trimmed.length() > 20) {
                sentences.add(trimmed);
            }
        }
        // Fallback: if splitting fails, just use the whole text as one sentence
        if (sentences.isEmpty()) sentences.add(text.trim());
        return sentences;
    }
}
