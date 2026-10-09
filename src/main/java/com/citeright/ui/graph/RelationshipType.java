package com.citeright.ui.graph;

import javafx.scene.paint.Color;

/**
 * Typed relationship semantics for the Paper Graph.
 *
 * Replaces the loose string convention ("SUPPORTS", "TFIDF", ...) used by the
 * legacy graph with an enum that carries its own visual identity (colour,
 * glow, arrow style). {@link #fromString(String)} keeps full backward
 * compatibility with whatever is already persisted in the database and with
 * the AI suggestion service's string outputs.
 *
 * Two non-semantic categories are included for routing/display purposes:
 *  - {@link #RELATED} : the soft TF-IDF similarity edges (formerly "TFIDF").
 *  - {@link #CITES}   : placeholder for a future citation-chain import.
 */
public enum RelationshipType {

    SUPPORTS     ("Supports",        "#2ecc71", true),
    EXTENDS      ("Extends",         "#4a9cf7", true),
    METHODOLOGY  ("Methodology",     "#9b59b6", true),
    COMPARES     ("Compares",        "#e67e22", true),
    CONTRADICTS  ("Contradicts",     "#e74c3c", true),
    CITES        ("Cites",           "#7f8c8d", true),
    RELATED      ("Related",         "#3a4a6a", false);

    private final String label;
    private final Color color;
    private final boolean directed;

    RelationshipType(String label, String hex, boolean directed) {
        this.label = label;
        this.color = Color.web(hex);
        this.directed = directed;
    }

    /** Human label for legends / inspector. */
    public String getLabel() { return label; }

    /** Base colour for the edge stroke. */
    public Color getColor() { return color; }

    /** A brighter variant used for glow / bloom strokes. */
    public Color getGlow() { return color.brighter(); }

    /** Whether the edge carries direction (drawn with an arrowhead). */
    public boolean isDirected() { return directed; }

    /**
     * Map a stored/AI-produced type string to an enum value.
     * Unknown / blank / "TFIDF" / null all map to {@link #RELATED} so the
     * graph keeps rendering even with legacy data.
     */
    public static RelationshipType fromString(String raw) {
        if (raw == null) return RELATED;
        String s = raw.trim().toUpperCase();
        switch (s) {
            case "SUPPORTS":     return SUPPORTS;
            case "EXTENDS":      return EXTENDS;
            case "METHODOLOGY":  return METHODOLOGY;
            case "COMPARES":     return COMPARES;
            case "CONTRADICTS":  return CONTRADICTS;
            case "CITES":
            case "CITED_BY":
            case "CITATION":     return CITES;
            case "TFIDF":
            case "SIMILAR":
            case "RELATED":
            case "":
            default:             return RELATED;
        }
    }
}
