package com.citeright.service;

import java.util.*;

/**
 * Multi-signal similarity calculator.
 *
 * Instead of relying solely on BGE-M3 embeddings, combines multiple signals:
 *
 *   Final Similarity = 0.45 × BGE-M3 cosine similarity
 *                    + 0.20 × Citation network overlap
 *                    + 0.15 × Shared methods score
 *                    + 0.10 × Shared tasks score
 *                    + 0.10 × Shared keywords score
 *
 * Gracefully degrades: if embeddings are unavailable, redistributes weight
 * to other signals. This ensures the system works even without the neural model.
 */
public class HybridSimilarityCalculator {

    // Default weights
    private static final double W_EMBEDDING = 0.45;
    private static final double W_CITATION = 0.20;
    private static final double W_METHODS = 0.15;
    private static final double W_TASKS = 0.10;
    private static final double W_KEYWORDS = 0.10;

    /**
     * Compute hybrid similarity between two papers.
     *
     * @param embeddingA   BGE-M3 vector for paper A (nullable)
     * @param embeddingB   BGE-M3 vector for paper B (nullable)
     * @param neighborsA   citation neighbors of paper A (paper IDs)
     * @param neighborsB   citation neighbors of paper B (paper IDs)
     * @param methodsA     extracted methods for paper A
     * @param methodsB     extracted methods for paper B
     * @param tasksA       extracted tasks for paper A
     * @param tasksB       extracted tasks for paper B
     * @param keywordsA    keywords for paper A
     * @param keywordsB    keywords for paper B
     * @return similarity score (0.0–1.0)
     */
    public double computeSimilarity(
            float[] embeddingA, float[] embeddingB,
            Set<Integer> neighborsA, Set<Integer> neighborsB,
            Set<String> methodsA, Set<String> methodsB,
            Set<String> tasksA, Set<String> tasksB,
            Set<String> keywordsA, Set<String> keywordsB) {

        boolean hasEmbeddings = embeddingA != null && embeddingB != null
                && embeddingA.length > 0 && embeddingB.length > 0;

        // Compute individual signals
        double embSim = hasEmbeddings ? cosineSimilarity(embeddingA, embeddingB) : 0.0;
        double citSim = jaccardSimilarity(neighborsA, neighborsB);
        double methSim = jaccardSimilarity(methodsA, methodsB);
        double taskSim = jaccardSimilarity(tasksA, tasksB);
        double kwSim = jaccardSimilarity(keywordsA, keywordsB);

        // Apply weights — redistribute embedding weight if unavailable
        if (hasEmbeddings) {
            return W_EMBEDDING * embSim
                 + W_CITATION * citSim
                 + W_METHODS * methSim
                 + W_TASKS * taskSim
                 + W_KEYWORDS * kwSim;
        } else {
            // Redistribute embedding weight proportionally to other signals
            double total = W_CITATION + W_METHODS + W_TASKS + W_KEYWORDS;
            return (W_CITATION / total) * citSim
                 + (W_METHODS / total) * methSim
                 + (W_TASKS / total) * taskSim
                 + (W_KEYWORDS / total) * kwSim;
        }
    }

    /**
     * Compute similarity between two clusters using centroids and aggregated entities.
     *
     * @param centroidA        cluster A centroid embedding (nullable)
     * @param centroidB        cluster B centroid embedding (nullable)
     * @param clusterEntitiesA aggregated entity set for cluster A
     * @param clusterEntitiesB aggregated entity set for cluster B
     * @param edgesBetween     number of citation edges between clusters
     * @param sizeA            number of papers in cluster A
     * @param sizeB            number of papers in cluster B
     * @return cluster-level similarity score (0.0–1.0)
     */
    public double computeClusterSimilarity(
            float[] centroidA, float[] centroidB,
            Set<String> clusterEntitiesA, Set<String> clusterEntitiesB,
            int edgesBetween, int sizeA, int sizeB) {

        boolean hasCentroids = centroidA != null && centroidB != null
                && centroidA.length > 0 && centroidB.length > 0;

        double embSim = hasCentroids ? cosineSimilarity(centroidA, centroidB) : 0.0;
        double entityOverlap = jaccardSimilarity(clusterEntitiesA, clusterEntitiesB);
        double connectionDensity = (sizeA > 0 && sizeB > 0)
                ? (double) edgesBetween / (sizeA * sizeB) : 0.0;

        if (hasCentroids) {
            return 0.50 * embSim + 0.30 * entityOverlap + 0.20 * connectionDensity;
        } else {
            return 0.60 * entityOverlap + 0.40 * connectionDensity;
        }
    }

    /**
     * Compute similarity between a query embedding and all papers.
     * Used for Idea Similarity Search.
     *
     * @param queryEmbedding   the query's BGE-M3 embedding
     * @param paperEmbeddings  map of paperId → embedding
     * @param topK             number of top results to return
     * @return sorted list of [paperId, similarity] pairs, highest first
     */
    public List<int[]> findSimilarPapers(float[] queryEmbedding,
                                          Map<Integer, float[]> paperEmbeddings,
                                          int topK) {
        if (queryEmbedding == null || paperEmbeddings.isEmpty()) {
            return Collections.emptyList();
        }

        // Compute similarity for all papers
        List<double[]> scores = new ArrayList<>();
        for (Map.Entry<Integer, float[]> entry : paperEmbeddings.entrySet()) {
            double sim = cosineSimilarity(queryEmbedding, entry.getValue());
            scores.add(new double[]{entry.getKey(), sim});
        }

        // Sort by similarity descending and take top K
        scores.sort((a, b) -> Double.compare(b[1], a[1]));

        List<int[]> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scores.size()); i++) {
            results.add(new int[]{(int) scores.get(i)[0], (int) (scores.get(i)[1] * 1000)});
        }
        return results;
    }

    // ─── Core similarity functions ──────────────────────────────────────

    /**
     * Cosine similarity between two vectors.
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 0 ? dot / denom : 0.0;
    }

    /**
     * Jaccard similarity between two sets.
     */
    public static <T> double jaccardSimilarity(Set<T> a, Set<T> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;

        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<T> union = new HashSet<>(a);
        union.addAll(b);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Count shared elements between two sets.
     */
    public static <T> int sharedCount(Set<T> a, Set<T> b) {
        if (a == null || b == null) return 0;
        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection.size();
    }
}
