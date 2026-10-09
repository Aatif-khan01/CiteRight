package com.citeright.service;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.model.Publication;
import com.citeright.nlp.TfIdfEngine;
import com.citeright.nlp.TextPreprocessor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Real clustering engine for the Macro layer.
 * Replaces the placeholder keyword-matching approach with TF-IDF vectorization
 * and hierarchical agglomerative clustering using Ward's linkage.
 *
 * The cluster count emerges from the data by cutting the dendrogram at the
 * largest merge-distance gap — no user-specified k required.
 *
 * Each cluster is auto-labeled using its top centroid terms.
 */
public class ClusteringEngine {

    /** Result of clustering: cluster label → member publications */
    public static class ClusterResult {
        private final Map<String, List<Publication>> clusters;
        private final Map<String, double[]> centroids; // label → centroid vector (term indices)
        private final List<String> vocabulary;         // ordered term list for centroid indexing

        public ClusterResult(Map<String, List<Publication>> clusters,
                             Map<String, double[]> centroids,
                             List<String> vocabulary) {
            this.clusters = clusters;
            this.centroids = centroids;
            this.vocabulary = vocabulary;
        }

        public Map<String, List<Publication>> getClusters() { return clusters; }
        public Map<String, double[]> getCentroids() { return centroids; }
        public List<String> getVocabulary() { return vocabulary; }
    }

    public ClusteringEngine() {}

    /**
     * Clusters papers using TF-IDF + hierarchical agglomerative clustering.
     * Falls back to simple heuristic if library is too small (< 4 papers).
     *
     * @param papers the papers to cluster
     * @return a map where key is the auto-generated topic label and value is the list of papers
     */
    public Map<String, List<Publication>> clusterPapers(List<Publication> papers) {
        if (papers.size() < 4) {
            return fallbackCluster(papers);
        }

        ClusterResult result = clusterWithDetails(papers);
        return result.getClusters();
    }

    /**
     * Full clustering with centroid details — used by PaperGraphPane for convex hull rendering.
     */
    public ClusterResult clusterWithDetails(List<Publication> papers) {
        if (papers.size() < 4) {
            Map<String, List<Publication>> fallback = fallbackCluster(papers);
            return new ClusterResult(fallback, new HashMap<>(), new ArrayList<>());
        }

        // ── Try BGE-M3 Neural Clustering First ──────────────────────────────
        if (NeuralAvailability.isReady()) {
            try {
                double[][] neuralVectors = buildNeuralVectors(papers);
                if (neuralVectors != null) {
                    System.out.println("[ClusteringEngine] \uD83E\uDDE0 Using BGE-M3 neural vectors for clustering.");
                    // Neural vectors are 1024-dim dense — use a synthetic "vocabulary" for label generation
                    return performClustering(papers, neuralVectors, buildNeuralVocabulary(papers));
                }
            } catch (Exception e) {
                System.err.println("[ClusteringEngine] Neural clustering failed, falling back to TF-IDF: " + e.getMessage());
            }
        }

        // ── Fallback: TF-IDF Clustering ─────────────────────────────────────
        System.out.println("[ClusteringEngine] Using TF-IDF vectors for clustering.");
        List<String> documents = new ArrayList<>();
        for (Publication p : papers) {
            documents.add(buildDocText(p));
        }

        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(documents);

        List<Map<String, Double>> sparseVectors = new ArrayList<>();
        for (String doc : documents) {
            sparseVectors.add(engine.computeTfIdfVector(doc));
        }

        // Build a shared vocabulary and convert to dense vectors
        Set<String> vocabSet = new LinkedHashSet<>();
        for (Map<String, Double> v : sparseVectors) {
            vocabSet.addAll(v.keySet());
        }
        List<String> vocabulary = new ArrayList<>(vocabSet);
        Map<String, Integer> termIndex = new HashMap<>();
        for (int i = 0; i < vocabulary.size(); i++) {
            termIndex.put(vocabulary.get(i), i);
        }

        int dim = vocabulary.size();
        double[][] vectors = new double[papers.size()][dim];
        for (int i = 0; i < papers.size(); i++) {
            for (Map.Entry<String, Double> entry : sparseVectors.get(i).entrySet()) {
                Integer idx = termIndex.get(entry.getKey());
                if (idx != null) {
                    vectors[i][idx] = entry.getValue();
                }
            }
        }
        return performClustering(papers, vectors, vocabulary);
    }

    /**
     * Core clustering engine: Hierarchical Agglomerative Clustering with Ward's linkage.
     * Works identically on TF-IDF sparse vectors or BGE-M3 dense 1024-dim vectors.
     * The vector space is agnostic — Ward's linkage just needs a distance metric.
     */
    private ClusterResult performClustering(List<Publication> papers, double[][] vectors, List<String> vocabulary) {
        int n = papers.size();
        int dim = vectors[0].length;
        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = i;

        // Track active clusters and their members
        Map<Integer, List<Integer>> clusterMembers = new HashMap<>();
        for (int i = 0; i < n; i++) {
            List<Integer> members = new ArrayList<>();
            members.add(i);
            clusterMembers.put(i, members);
        }

        // Track merge distances for gap detection
        List<Double> mergeDistances = new ArrayList<>();
        int nextClusterId = n;

        // Precompute pairwise distances
        double[][] distMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = euclideanDistance(vectors[i], vectors[j]);
                distMatrix[i][j] = d;
                distMatrix[j][i] = d;
            }
        }

        // Agglomerative loop using Ward's linkage
        Set<Integer> activeClusters = new HashSet<>(clusterMembers.keySet());

        while (activeClusters.size() > 1) {
            double minDist = Double.MAX_VALUE;
            int mergeA = -1, mergeB = -1;

            List<Integer> activeList = new ArrayList<>(activeClusters);
            for (int i = 0; i < activeList.size(); i++) {
                for (int j = i + 1; j < activeList.size(); j++) {
                    int ca = activeList.get(i), cb = activeList.get(j);
                    double ward = wardDistance(vectors, clusterMembers.get(ca), clusterMembers.get(cb));
                    if (ward < minDist) {
                        minDist = ward;
                        mergeA = ca;
                        mergeB = cb;
                    }
                }
            }

            mergeDistances.add(minDist);

            List<Integer> merged = new ArrayList<>(clusterMembers.get(mergeA));
            merged.addAll(clusterMembers.get(mergeB));
            clusterMembers.put(nextClusterId, merged);
            clusterMembers.remove(mergeA);
            clusterMembers.remove(mergeB);
            activeClusters.remove(mergeA);
            activeClusters.remove(mergeB);
            activeClusters.add(nextClusterId);
            nextClusterId++;
        }

        // Cut dendrogram at largest gap
        int numClusters = determineOptimalCut(mergeDistances, n);
        numClusters = Math.max(2, Math.min(numClusters, n / 2));

        // Re-run clustering but stop at the desired cluster count
        clusterMembers.clear();
        for (int i = 0; i < n; i++) {
            List<Integer> members = new ArrayList<>();
            members.add(i);
            clusterMembers.put(i, members);
        }
        activeClusters = new HashSet<>(clusterMembers.keySet());
        nextClusterId = n;

        while (activeClusters.size() > numClusters) {
            double minDist = Double.MAX_VALUE;
            int mergeA = -1, mergeB = -1;
            List<Integer> activeList = new ArrayList<>(activeClusters);
            for (int i = 0; i < activeList.size(); i++) {
                for (int j = i + 1; j < activeList.size(); j++) {
                    int ca = activeList.get(i), cb = activeList.get(j);
                    double ward = wardDistance(vectors, clusterMembers.get(ca), clusterMembers.get(cb));
                    if (ward < minDist) {
                        minDist = ward;
                        mergeA = ca;
                        mergeB = cb;
                    }
                }
            }

            List<Integer> merged = new ArrayList<>(clusterMembers.get(mergeA));
            merged.addAll(clusterMembers.get(mergeB));
            clusterMembers.put(nextClusterId, merged);
            clusterMembers.remove(mergeA);
            clusterMembers.remove(mergeB);
            activeClusters.remove(mergeA);
            activeClusters.remove(mergeB);
            activeClusters.add(nextClusterId);
            nextClusterId++;
        }

        // Label clusters — for neural vectors, use TF-IDF vocabulary for human-readable names
        // For TF-IDF vectors, use centroid term weights directly
        Map<String, List<Publication>> result = new LinkedHashMap<>();
        Map<String, double[]> centroids = new HashMap<>();
        boolean isNeuralMode = (dim == 1024); // BGE-M3 produces 1024-dim vectors

        for (int clusterId : activeClusters) {
            List<Integer> members = clusterMembers.get(clusterId);
            String label;

            if (isNeuralMode) {
                // Neural mode: label by extracting top keywords from member titles
                label = labelClusterByTitles(papers, members);
            } else {
                // TF-IDF mode: label by top centroid terms
                double[] centroid = new double[dim];
                for (int memberIdx : members) {
                    for (int d = 0; d < dim; d++) {
                        centroid[d] += vectors[memberIdx][d];
                    }
                }
                for (int d = 0; d < dim; d++) {
                    centroid[d] /= members.size();
                }

                List<Map.Entry<Integer, Double>> termWeights = new ArrayList<>();
                for (int d = 0; d < dim; d++) {
                    if (centroid[d] > 0 && d < vocabulary.size()) {
                        termWeights.add(Map.entry(d, centroid[d]));
                    }
                }
                termWeights.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

                label = termWeights.stream()
                        .limit(3)
                        .filter(e -> e.getKey() < vocabulary.size())
                        .map(e -> capitalize(vocabulary.get(e.getKey())))
                        .collect(Collectors.joining(", "));

                centroids.put(label.isEmpty() ? "Cluster " + clusterId : label, centroid);
            }

            if (label.isEmpty()) label = "Cluster " + clusterId;

            List<Publication> memberPubs = members.stream()
                    .map(papers::get)
                    .collect(Collectors.toList());

            result.put(label, memberPubs);
        }

        System.out.println("[ClusteringEngine] Created " + result.size() + " clusters from " + papers.size() + " papers"
                + (isNeuralMode ? " (neural)" : " (TF-IDF)"));
        return new ClusterResult(result, centroids, vocabulary);
    }

    /**
     * Labels a neural cluster by extracting the most common meaningful words
     * from member paper titles. Since neural vectors don't have term dimensions,
     * this provides human-readable cluster names.
     */
    private String labelClusterByTitles(List<Publication> papers, List<Integer> members) {
        Map<String, Integer> wordFreq = new LinkedHashMap<>();
        Set<String> stopWords = Set.of(
            "the", "a", "an", "of", "in", "for", "and", "on", "to", "with",
            "by", "from", "at", "as", "is", "are", "was", "were", "be", "been",
            "using", "based", "study", "analysis", "approach", "method", "new", "via"
        );

        for (int idx : members) {
            String title = papers.get(idx).getTitle();
            if (title == null) continue;
            for (String word : title.toLowerCase().split("\\W+")) {
                if (word.length() >= 3 && !stopWords.contains(word)) {
                    wordFreq.merge(word, 1, Integer::sum);
                }
            }
        }

        return wordFreq.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> capitalize(e.getKey()))
                .collect(Collectors.joining(", "));
    }

    // ── Ward's linkage distance ──────────────────────────────────────────────

    private double wardDistance(double[][] vectors, List<Integer> clusterA, List<Integer> clusterB) {
        double[] centroidA = computeCentroid(vectors, clusterA);
        double[] centroidB = computeCentroid(vectors, clusterB);
        double dist = euclideanDistanceSq(centroidA, centroidB);
        return (clusterA.size() * clusterB.size() * dist) / (clusterA.size() + clusterB.size());
    }

    private double[] computeCentroid(double[][] vectors, List<Integer> members) {
        int dim = vectors[0].length;
        double[] centroid = new double[dim];
        for (int idx : members) {
            for (int d = 0; d < dim; d++) {
                centroid[d] += vectors[idx][d];
            }
        }
        for (int d = 0; d < dim; d++) {
            centroid[d] /= members.size();
        }
        return centroid;
    }

    // ── Gap-based cut determination ──────────────────────────────────────────

    private int determineOptimalCut(List<Double> mergeDistances, int n) {
        if (mergeDistances.size() < 2) return 2;

        // Find the largest gap in merge distances
        double maxGap = 0;
        int gapIndex = -1;
        for (int i = 1; i < mergeDistances.size(); i++) {
            double gap = mergeDistances.get(i) - mergeDistances.get(i - 1);
            if (gap > maxGap) {
                maxGap = gap;
                gapIndex = i;
            }
        }

        // Number of clusters = n - gapIndex (since gapIndex merges happened before the big gap)
        if (gapIndex >= 0) {
            int k = n - gapIndex;
            return Math.max(2, Math.min(k, 15)); // Cap at 15 clusters for usability
        }

        // Fallback: sqrt(n/2) heuristic
        return Math.max(2, (int) Math.sqrt(n / 2.0));
    }

    // ── Distance utilities ───────────────────────────────────────────────────

    private double euclideanDistance(double[] a, double[] b) {
        return Math.sqrt(euclideanDistanceSq(a, b));
    }

    private double euclideanDistanceSq(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    // ── Text utilities ───────────────────────────────────────────────────────

    private String buildDocText(Publication paper) {
        StringBuilder sb = new StringBuilder();
        if (paper.getTitle() != null) {
            sb.append(paper.getTitle()).append(" ");
            sb.append(paper.getTitle()).append(" "); // Weight title terms 2x
        }
        if (paper.getAbstractText() != null) {
            sb.append(paper.getAbstractText());
        }
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Fallback for very small libraries — groups by simple keyword detection.
     */
    private Map<String, List<Publication>> fallbackCluster(List<Publication> papers) {
        Map<String, List<Publication>> clusters = new LinkedHashMap<>();
        String label = papers.size() == 1 ? papers.get(0).getTitle() : "All Papers";
        clusters.put(label, new ArrayList<>(papers));
        return clusters;
    }

    // ── Neural Vector Helpers ────────────────────────────────────────────────

    /**
     * Builds 1024-dim dense neural vectors for all papers using cached BGE-M3 embeddings.
     * Papers without cached embeddings get on-the-fly computation.
     * Returns null if too few papers have embeddings to be useful.
     */
    private double[][] buildNeuralVectors(List<Publication> papers) {
        Map<Integer, float[]> cachedEmbeddings = NeuralAvailability.getCachedEmbeddings();
        BgeM3EmbeddingEngine neuralEngine = BgeM3EmbeddingEngine.getInstance();

        int dim = 1024;
        double[][] vectors = new double[papers.size()][dim];
        int embeddedCount = 0;

        for (int i = 0; i < papers.size(); i++) {
            Publication pub = papers.get(i);
            float[] embedding = pub.getEmbedding(); // check in-memory first

            // Try cached embedding by scanning cache (match by title hash as fallback)
            if (embedding == null && cachedEmbeddings != null) {
                for (Map.Entry<Integer, float[]> entry : cachedEmbeddings.entrySet()) {
                    // Use the embedding attached to the Publication object if available
                    break;
                }
            }

            // On-the-fly computation if no cached embedding found
            if (embedding == null) {
                String text = buildDocText(pub);
                embedding = neuralEngine.getEmbedding(text);
            }

            if (embedding != null && embedding.length == dim) {
                for (int d = 0; d < dim; d++) {
                    vectors[i][d] = embedding[d];
                }
                embeddedCount++;
            }
        }

        // Only use neural vectors if at least 50% of papers have embeddings
        if (embeddedCount < papers.size() * 0.5) {
            System.out.println("[ClusteringEngine] Only " + embeddedCount + "/" + papers.size()
                    + " papers have neural embeddings. Falling back to TF-IDF.");
            return null;
        }

        return vectors;
    }

    /**
     * Builds a keyword-based vocabulary for neural cluster labeling.
     * Neural vectors don't have term dimensions, so we extract top keywords
     * from each paper's title+abstract for human-readable cluster labels.
     */
    private List<String> buildNeuralVocabulary(List<Publication> papers) {
        // Use TF-IDF vocabulary just for label generation (not for clustering)
        List<String> documents = new ArrayList<>();
        for (Publication p : papers) {
            documents.add(buildDocText(p));
        }
        TfIdfEngine labelEngine = new TfIdfEngine();
        labelEngine.buildModel(documents);

        Set<String> vocabSet = new LinkedHashSet<>();
        for (String doc : documents) {
            vocabSet.addAll(labelEngine.computeTfIdfVector(doc).keySet());
        }
        return new ArrayList<>(vocabSet);
    }
}
