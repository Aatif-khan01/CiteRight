package com.citeright.ui.graph;

import java.util.List;

/**
 * One connection between two papers.
 *
 * Direction: {@code a} → {@code b} for directed relationship types
 * ({@link RelationshipType#isDirected()}). For undirected similarity edges
 * (RELATED) the order carries no meaning.
 *
 * <ul>
 *   <li>{@code type} — semantic category, drives colour / arrow.</li>
 *   <li>{@code weight} — visual weight (0..1), typically the similarity score
 *       for RELATED edges and the confidence for curated/AI edges.</li>
 *   <li>{@code confidence} — provenance confidence for curated/AI edges.</li>
 *   <li>{@code bundleGroup} — routing hint so parallel/overlapping edges share
 *       a bundle and only fan out near their endpoints.</li>
 *   <li>{@code sharedTerms} — top overlapping TF-IDF terms, surfaced by the
 *       connection explainer ("shared keywords").</li>
 *   <li>{@code particlePhase} — animated phase offset for the flowing-particle
 *       effect on selected edges.</li>
 * </ul>
 */
public final class GraphEdge {

    public final GraphNode a;
    public final GraphNode b;
    public final RelationshipType type;

    public double weight;
    public double confidence = 1.0;

    public boolean isAISuggestion = false;
    public int     relationshipId = -1;
    public String  reasoning = null;

    public String bundleGroup = null;
    public List<String> sharedTerms = null;

    // Animated particle phase (0..1), randomised per build.
    public double particlePhase = Math.random();

    // Interaction state (transient).
    public boolean hovered  = false;
    public boolean selected = false;
    public boolean dimmed   = false;

    public GraphEdge(GraphNode a, GraphNode b, double weight, RelationshipType type) {
        this.a = a;
        this.b = b;
        this.weight = weight;
        this.type = type;
        this.confidence = weight; // sensible default for similarity edges
    }

    /** Does this edge touch the given node? */
    public boolean touches(GraphNode n) {
        return a == n || b == n;
    }

    /** The "other" endpoint relative to {@code n}, or {@code null}. */
    public GraphNode other(GraphNode n) {
        if (a == n) return b;
        if (b == n) return a;
        return null;
    }

    @Override
    public String toString() {
        return type + " " + a.title + " → " + b.title + " (" + (int) (weight * 100) + "%)";
    }
}
