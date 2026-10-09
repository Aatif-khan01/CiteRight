package com.citeright.service;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.nlp.TextPreprocessor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes the paper graph for structural gaps and research opportunities.
 *
 * Three types of analysis:
 * 1. Bridge edges — single connections between otherwise-separate clusters (research opportunities)
 * 2. Orphan papers — papers with zero connections (potential novelty or misclassification)
 * 3. Methodology overlap — papers sharing experimental methods despite different topics
 */
public class GapAnalyzer {

    /** Methodology keywords extracted from abstracts using pattern matching */
    private static final List<Pattern> METHOD_PATTERNS = List.of(
            Pattern.compile("\\b(fMRI|EEG|MEG|PET scan)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(CRISPR|PCR|Western blot|immunohistochemistry)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(Monte Carlo|Bayesian|regression|ANOVA|t-test|chi-square)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(deep learning|neural network|CNN|RNN|LSTM|transformer|GAN|VAE)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(survey|meta-analysis|systematic review|randomized controlled trial|RCT)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(finite element|CFD|molecular dynamics|DFT|density functional)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(qualitative|ethnography|grounded theory|case study|interview)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(reinforcement learning|Q-learning|policy gradient|reward)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(genome-wide association|GWAS|RNA-seq|proteomics|metabolomics)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(A/B test|user study|usability|eye-tracking|think-aloud)\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Result of gap analysis on the graph.
     */
    public static class GapAnalysisResult {
        private final Set<EdgeKey> bridgeEdges;          // Edges connecting separate communities
        private final Set<Integer> orphanPaperIndices;    // Papers with no connections
        private final List<MethodologyLink> methodLinks;  // Suggested methodology connections
        private final Map<Integer, Double> edgeBetweenness; // Edge index → betweenness score

        public GapAnalysisResult(Set<EdgeKey> bridgeEdges,
                                 Set<Integer> orphanPaperIndices,
                                 List<MethodologyLink> methodLinks,
                                 Map<Integer, Double> edgeBetweenness) {
            this.bridgeEdges = bridgeEdges;
            this.orphanPaperIndices = orphanPaperIndices;
            this.methodLinks = methodLinks;
            this.edgeBetweenness = edgeBetweenness;
        }

        public Set<EdgeKey> getBridgeEdges() { return bridgeEdges; }
        public Set<Integer> getOrphanPaperIndices() { return orphanPaperIndices; }
        public List<MethodologyLink> getMethodLinks() { return methodLinks; }
        public Map<Integer, Double> getEdgeBetweenness() { return edgeBetweenness; }
    }

    /** Simple pair key for edges */
    public static class EdgeKey {
        public final int a, b;
        public EdgeKey(int a, int b) { this.a = Math.min(a, b); this.b = Math.max(a, b); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey ek)) return false;
            return a == ek.a && b == ek.b;
        }

        @Override
        public int hashCode() { return Objects.hash(a, b); }
    }

    /** Suggested connection based on shared methodology */
    public static class MethodologyLink {
        public final int paperIndexA;
        public final int paperIndexB;
        public final String sharedMethod;

        public MethodologyLink(int a, int b, String method) {
            this.paperIndexA = a;
            this.paperIndexB = b;
            this.sharedMethod = method;
        }
    }

    /**
     * Performs gap analysis on the paper graph.
     *
     * @param entries    All library entries
     * @param edgePairs  Existing edges as pairs of paper indices (into the entries list)
     * @return Gap analysis result
     */
    public GapAnalysisResult analyze(List<LibraryEntry> entries, List<int[]> edgePairs) {
        int n = entries.size();

        // Build adjacency list
        Map<Integer, Set<Integer>> adjacency = new HashMap<>();
        for (int i = 0; i < n; i++) adjacency.put(i, new HashSet<>());
        for (int[] edge : edgePairs) {
            adjacency.get(edge[0]).add(edge[1]);
            adjacency.get(edge[1]).add(edge[0]);
        }

        // 1. Find orphans (degree 0)
        Set<Integer> orphans = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (adjacency.get(i).isEmpty()) {
                orphans.add(i);
            }
        }

        // 2. Compute edge betweenness centrality (Brandes' algorithm simplified)
        Map<Integer, Double> edgeBetweenness = computeEdgeBetweenness(n, adjacency, edgePairs);

        // 3. Identify bridge edges — top 10% by betweenness, or edges connecting different components
        Set<EdgeKey> bridgeEdges = new HashSet<>();
        if (!edgeBetweenness.isEmpty()) {
            double threshold = edgeBetweenness.values().stream()
                    .sorted(Comparator.reverseOrder())
                    .skip(Math.max(1, edgeBetweenness.size() / 10))
                    .findFirst().orElse(0.0);

            for (Map.Entry<Integer, Double> entry : edgeBetweenness.entrySet()) {
                if (entry.getValue() >= threshold) {
                    int[] edge = edgePairs.get(entry.getKey());
                    bridgeEdges.add(new EdgeKey(edge[0], edge[1]));
                }
            }
        }

        // Also add actual graph bridges (edges whose removal disconnects the graph)
        Set<EdgeKey> actualBridges = findActualBridges(n, adjacency);
        bridgeEdges.addAll(actualBridges);

        // 4. Methodology overlap detection
        List<MethodologyLink> methodLinks = detectMethodologyOverlap(entries, adjacency);

        System.out.println("[GapAnalyzer] Found " + orphans.size() + " orphans, "
                + bridgeEdges.size() + " bridge edges, "
                + methodLinks.size() + " methodology links");

        return new GapAnalysisResult(bridgeEdges, orphans, methodLinks, edgeBetweenness);
    }

    // ── Edge Betweenness (simplified Brandes) ────────────────────────────────

    private Map<Integer, Double> computeEdgeBetweenness(int n,
                                                         Map<Integer, Set<Integer>> adjacency,
                                                         List<int[]> edgePairs) {
        Map<Integer, Double> betweenness = new HashMap<>();
        for (int i = 0; i < edgePairs.size(); i++) betweenness.put(i, 0.0);

        // Index edges for lookup
        Map<EdgeKey, Integer> edgeIndex = new HashMap<>();
        for (int i = 0; i < edgePairs.size(); i++) {
            edgeIndex.put(new EdgeKey(edgePairs.get(i)[0], edgePairs.get(i)[1]), i);
        }

        // BFS from each node
        for (int s = 0; s < n; s++) {
            // BFS
            Queue<Integer> queue = new LinkedList<>();
            Deque<Integer> stack = new ArrayDeque<>();
            Map<Integer, List<Integer>> predecessors = new HashMap<>();
            Map<Integer, Integer> sigma = new HashMap<>(); // shortest path count
            Map<Integer, Integer> dist = new HashMap<>();

            for (int v = 0; v < n; v++) {
                predecessors.put(v, new ArrayList<>());
                sigma.put(v, 0);
                dist.put(v, -1);
            }
            sigma.put(s, 1);
            dist.put(s, 0);
            queue.add(s);

            while (!queue.isEmpty()) {
                int v = queue.poll();
                stack.push(v);
                for (int w : adjacency.getOrDefault(v, Set.of())) {
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            // Accumulate
            Map<Integer, Double> delta = new HashMap<>();
            for (int v = 0; v < n; v++) delta.put(v, 0.0);

            while (!stack.isEmpty()) {
                int w = stack.pop();
                for (int v : predecessors.get(w)) {
                    double c = (double) sigma.get(v) / sigma.get(w) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + c);

                    EdgeKey ek = new EdgeKey(v, w);
                    Integer idx = edgeIndex.get(ek);
                    if (idx != null) {
                        betweenness.put(idx, betweenness.get(idx) + c);
                    }
                }
            }
        }

        // Normalize (undirected: divide by 2)
        for (Map.Entry<Integer, Double> entry : betweenness.entrySet()) {
            entry.setValue(entry.getValue() / 2.0);
        }

        return betweenness;
    }

    // ── Actual bridge detection (Tarjan's bridge-finding) ────────────────────

    private Set<EdgeKey> findActualBridges(int n, Map<Integer, Set<Integer>> adjacency) {
        Set<EdgeKey> bridges = new HashSet<>();
        int[] disc = new int[n];
        int[] low = new int[n];
        boolean[] visited = new boolean[n];
        int[] timer = {0};

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                bridgeDFS(i, -1, disc, low, visited, timer, adjacency, bridges);
            }
        }
        return bridges;
    }

    private void bridgeDFS(int u, int parent, int[] disc, int[] low,
                           boolean[] visited, int[] timer,
                           Map<Integer, Set<Integer>> adjacency, Set<EdgeKey> bridges) {
        visited[u] = true;
        disc[u] = low[u] = timer[0]++;

        for (int v : adjacency.getOrDefault(u, Set.of())) {
            if (!visited[v]) {
                bridgeDFS(v, u, disc, low, visited, timer, adjacency, bridges);
                low[u] = Math.min(low[u], low[v]);
                if (low[v] > disc[u]) {
                    bridges.add(new EdgeKey(u, v));
                }
            } else if (v != parent) {
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }

    // ── Methodology overlap detection ────────────────────────────────────────

    private List<MethodologyLink> detectMethodologyOverlap(List<LibraryEntry> entries,
                                                           Map<Integer, Set<Integer>> adjacency) {
        // Extract methods for each paper
        List<Set<String>> paperMethods = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            String text = "";
            if (pub != null) {
                text = (pub.getTitle() != null ? pub.getTitle() : "") + " "
                        + (pub.getAbstractText() != null ? pub.getAbstractText() : "");
            }
            paperMethods.add(extractMethods(text));
        }

        // Find pairs sharing methods but NOT already connected
        List<MethodologyLink> links = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                // Skip if already connected
                if (adjacency.get(i).contains(j)) continue;

                // Check for shared methods
                Set<String> shared = new HashSet<>(paperMethods.get(i));
                shared.retainAll(paperMethods.get(j));

                if (!shared.isEmpty()) {
                    links.add(new MethodologyLink(i, j, String.join(", ", shared)));
                }
            }
        }

        // Limit to top 20 to avoid noise
        return links.stream().limit(20).collect(Collectors.toList());
    }

    private Set<String> extractMethods(String text) {
        Set<String> methods = new HashSet<>();
        for (Pattern p : METHOD_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                methods.add(m.group(1).toLowerCase());
            }
        }
        return methods;
    }
}
