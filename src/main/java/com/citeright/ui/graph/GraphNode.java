package com.citeright.ui.graph;

import com.citeright.model.LibraryEntry;
import javafx.scene.paint.Color;

import java.util.Map;

/**
 * One paper in the graph.
 *
 * Promoted out of the legacy {@code PaperGraphPane.GraphNode} inner class so
 * the layout, routing and rendering layers can share a richer node model.
 *
 * <p>Positions ({@code x,y}) are in <em>world</em> space. Once the layout
 * converges they are frozen and only change on explicit structural edits
 * (filter / drag-commit). {@code vx,vy} are scratch velocity used during the
 * refinement phase and are zeroed afterwards.
 *
 * <p>{@code importance} is a 0..1 aggregate (citations + PageRank + semantic
 * influence + user activity + relationship confidence) computed once per
 * build; it drives node radius and label gating.
 */
public final class GraphNode {

    public final LibraryEntry entry;
    public final String title;
    public final int year;

    // Position (world space) — frozen after layout converges.
    public double x, y;
    public double vx, vy;

    // Cluster membership.
    public String clusterLabel = null;
    public Color   clusterColor = null;

    // Importance 0..1 (see GraphModel.importance()).
    public double importance = 0.0;

    // The anchor paper of this node's cluster (highest importance).
    public boolean isAnchor = false;

    // Sparse TF-IDF vector — shared with the explainer for "shared keywords".
    public Map<String, Double> termVector = null;

    // Interaction state (transient, not persisted).
    public boolean hovered   = false;
    public boolean selected  = false;
    public boolean dimmed    = false;

    public GraphNode(LibraryEntry entry, String title, int year, double x, double y) {
        this.entry = entry;
        this.title = title;
        this.year  = year;
        this.x = x;
        this.y = y;
    }

    /** The DB integer id (used as the embedding + relationship key). */
    public int paperId() {
        return entry != null ? entry.getId() : -1;
    }

    @Override
    public String toString() {
        return "GraphNode{" + title + " (" + year + ") imp=" + (int) (importance * 100) + "%}";
    }
}
