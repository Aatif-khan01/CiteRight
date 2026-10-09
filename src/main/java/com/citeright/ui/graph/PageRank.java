package com.citeright.ui.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classic PageRank over the paper graph.
 *
 * <p>Edges are treated as directed when the relationship type is directed
 * (e.g. A EXTENDS B → "B recommends A"); undirected edges contribute in both
 * directions. Weights ({@link GraphEdge#weight}) scale the link mass, so
 * strong similarity links propagate more authority.
 *
 * <p>Returns a 0..1-normalised PageRank per node id (the node's DB id). A
 * paper that is frequently pointed to by other important papers scores high,
 * capturing "graph centrality" beyond raw citation counts.
 */
public final class PageRank {

    private static final double DAMPING = 0.85;
    private static final int ITERATIONS = 30;

    private PageRank() {}

    /**
     * @param nodes the graph nodes
     * @param edges the graph edges
     * @return map from {@link GraphNode#paperId()} to a 0..1 PageRank score.
     */
    public static Map<Integer, Double> compute(List<GraphNode> nodes, List<GraphEdge> edges) {
        int n = nodes.size();
        if (n == 0) return Map.of();

        Map<Integer, GraphNode> byId = new HashMap<>();
        for (GraphNode node : nodes) byId.put(node.paperId(), node);

        // Out-degree (weight sum) per source.
        Map<Integer, Double> outWeight = new HashMap<>();
        // Adjacency: source → (target → weight contributed)
        Map<Integer, Map<Integer, Double>> adj = new HashMap<>();

        for (GraphEdge e : edges) {
            if (e.a == null || e.b == null) continue;
            int src = e.a.paperId();
            int tgt = e.b.paperId();
            if (src == tgt) continue;

            addLink(adj, outWeight, src, tgt, e.weight);
            if (!e.type.isDirected()) {
                addLink(adj, outWeight, tgt, src, e.weight);
            }
        }

        // Initialise uniformly.
        Map<Integer, Double> rank = new HashMap<>();
        for (GraphNode node : nodes) rank.put(node.paperId(), 1.0 / n);

        // Iterate.
        for (int it = 0; it < ITERATIONS; it++) {
            Map<Integer, Double> next = new HashMap<>();
            double dangling = 0.0;
            for (GraphNode node : nodes) {
                int id = node.paperId();
                if (outWeight.getOrDefault(id, 0.0) == 0.0) {
                    dangling += rank.getOrDefault(id, 0.0);
                }
            }

            for (GraphNode node : nodes) {
                int id = node.paperId();
                double sum = 0.0;
                // Find inbound contributions.
                for (Map.Entry<Integer, Map<Integer, Double>> entry : adj.entrySet()) {
                    Double w = entry.getValue().get(id);
                    if (w != null) {
                        int other = entry.getKey();
                        double ow = outWeight.getOrDefault(other, 0.0);
                        if (ow > 0) {
                            sum += rank.getOrDefault(other, 0.0) * (w / ow);
                        }
                    }
                }
                // Dangling redistribution.
                double pr = (1.0 - DAMPING) / n + DAMPING * (sum + dangling / n);
                next.put(id, pr);
            }
            // Swap.
            rank = next;
        }

        // Normalise to 0..1 by the max.
        double max = 0.0;
        for (double v : rank.values()) max = Math.max(max, v);
        if (max <= 0) max = 1.0;
        Map<Integer, Double> norm = new HashMap<>();
        for (Map.Entry<Integer, Double> e : rank.entrySet()) {
            norm.put(e.getKey(), e.getValue() / max);
        }
        return norm;
    }

    private static void addLink(Map<Integer, Map<Integer, Double>> adj,
                                Map<Integer, Double> outWeight,
                                int src, int tgt, double w) {
        adj.computeIfAbsent(src, k -> new HashMap<>())
           .merge(tgt, w, Double::sum);
        outWeight.merge(src, w, Double::sum);
    }
}
