package com.citeright.ui.graph;

import javafx.scene.paint.Color;

/**
 * Central visual identity for the Paper Graph.
 *
 * Keeps every renderer, panel and overlay reading from one palette so the
 * whole feature stays visually coherent. Dark premium base with a single
 * accent and a luminous cluster palette. Glass + glow helpers abstract the
 * repetitive Canvas "stack translucent ovals" idiom.
 */
public final class Theme {

    private Theme() {}

    // ── Base surfaces ───────────────────────────────────────────────────────
    public static final String BG_HEX       = "#0a0a18";
    public static final String PANEL_HEX    = "#121626";
    public static final String PANEL_2_HEX  = "#1a1a2e";
    public static final String BORDER_HEX   = "#2a2a3e";
    public static final String DOT_GRID_HEX = "#1a1a35";

    public static final Color  BG       = Color.web(BG_HEX);
    public static final Color  PANEL    = Color.web(PANEL_HEX);
    public static final Color  BORDER   = Color.web(BORDER_HEX);

    // ── Accent + text ──────────────────────────────────────────────────────
    public static final String ACCENT_HEX    = "#4a9cf7";
    public static final String ACCENT_2_HEX  = "#6c5ce7";
    public static final String TEXT_HEX      = "#ffffff";
    public static final String TEXT_DIM_HEX  = "#aaaacc";
    public static final String TEXT_FAINT_HEX = "#5a5a8a";

    public static final Color ACCENT   = Color.web(ACCENT_HEX);
    public static final Color TEXT     = Color.web(TEXT_HEX);
    public static final Color TEXT_DIM = Color.web(TEXT_DIM_HEX);

    /** Ego / focused seed node. */
    public static final Color EGO_COLOR      = Color.web("#00d2d3");
    /** "Drag-from" relationship source marker. */
    public static final Color SOURCE_COLOR   = Color.web("#f1c40f");
    /** Orphan / gap-analysis node. */
    public static final Color ORPHAN_COLOR   = Color.web("#e74c3c");

    // ── Cluster palette (luminous, distinct hues) ──────────────────────────
    public static final Color[] CLUSTER_COLORS = {
            Color.web("#4a9cf7"), Color.web("#ff6b9d"), Color.web("#2ecc71"),
            Color.web("#f1c40f"), Color.web("#9b59b6"), Color.web("#e67e22"),
            Color.web("#1abc9c"), Color.web("#e74c3c"), Color.web("#3498db"),
            Color.web("#fd79a8"), Color.web("#6c5ce7"), Color.web("#00cec9"),
            Color.web("#fdcb6e"), Color.web("#d63031"), Color.web("#74b9ff")
    };

    public static Color clusterColor(int index) {
        if (index < 0) return ACCENT;
        return CLUSTER_COLORS[index % CLUSTER_COLORS.length];
    }

    // ── Glass CSS for JavaFX panels ────────────────────────────────────────

    /** Translucent glass panel with a soft 1px border + drop shadow. */
    public static final String GLASS_PANEL =
            "-fx-background-color: rgba(18,22,38,0.85);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(74,108,247,0.18);" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 18, 0.2, 0, 4);";

    /** Slightly lighter glass used for hover cards / tooltips. */
    public static final String GLASS_CARD =
            "-fx-background-color: rgba(15,15,28,0.96);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: rgba(74,108,247,0.35);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;";

    /** Solid right-side inspector background. */
    public static final String INSPECTOR_BG =
            "-fx-background-color: #1a1a2e; " +
            "-fx-border-color: #2a2a3e; -fx-border-width: 0 0 0 1;";

    public static final String SIDEBAR_BG =
            "-fx-background-color: #121626;";

    // ── Canvas glow helpers ────────────────────────────────────────────────

    /**
     * Draws a soft radial glow (stacked translucent ovals) at the given centre.
     * Used for node halos, selected-edge bloom and cluster boundaries.
     *
     * @param layers array of (scaleFactor, alpha) pairs, outermost first.
     */
    public static void drawGlow(javafx.scene.canvas.GraphicsContext gc,
                                double cx, double cy, double radius,
                                Color color, double[][] layers) {
        for (double[] layer : layers) {
            double s = layer[0];
            double a = layer[1];
            gc.setFill(color.deriveColor(0, 1.0, 1.0, a));
            double r = radius * s;
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    /** Convenience: a three-layer warm halo for selected / hovered nodes. */
    public static void drawNodeHalo(javafx.scene.canvas.GraphicsContext gc,
                                    double cx, double cy, double radius, Color color) {
        drawGlow(gc, cx, cy, radius, color, new double[][]{
                {2.4, 0.06}, {1.8, 0.12}, {1.35, 0.22}
        });
    }

    /** Derive a translucent version of a colour. */
    public static Color alpha(Color c, double a) {
        return c.deriveColor(0, 1.0, 1.0, Math.max(0, Math.min(1, a)));
    }
}
