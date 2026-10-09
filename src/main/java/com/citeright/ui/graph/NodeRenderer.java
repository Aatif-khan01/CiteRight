package com.citeright.ui.graph;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws individual {@link GraphNode}s onto a JavaFX Canvas.
 *
 * <p>All coordinates are in <em>screen space</em> (already transformed by
 * the caller via {@link GraphCamera}). The renderer is stateless — every
 * draw call is complete and self-contained.
 *
 * <h3>Node anatomy (centre-out)</h3>
 * <ol>
 *   <li>Dim overlay (when {@code node.dimmed}) — low-opacity fill cap.</li>
 *   <li>Outer glow halos ({@link Theme#drawNodeHalo}) on hover/select.</li>
 *   <li>Filled circle — base colour = cluster colour, inner radial
 *       highlight to give depth.</li>
 *   <li>Thin outer ring (cluster colour, slightly brighter).</li>
 *   <li>Gold inner ring on anchor nodes.</li>
 *   <li>Year label centred inside the circle.</li>
 *   <li>Title chip (≤2 lines, capped at 120 × zoom px wide) below
 *       the circle, hidden when zoomed out past the gating threshold.</li>
 * </ol>
 */
public final class NodeRenderer {

    // Base radius at importance=0; max at importance=1.
    private static final double BASE_RADIUS = 18.0;
    private static final double RADIUS_RANGE = 16.0; // 18..34
    private static final double ANCHOR_BONUS = 4.0;

    // Title chip is suppressed below this zoom level (handled by GraphRenderer).
    public static final double TITLE_ZOOM_THRESHOLD = 0.55;

    private static final Font YEAR_FONT  = Font.font("System", FontWeight.BOLD, 9);
    private static final Font TITLE_FONT = Font.font("System", FontWeight.NORMAL, 10);

    private NodeRenderer() {}

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * Draw a single node.
     *
     * @param gc      canvas graphics context
     * @param node    the node to draw
     * @param sx      screen X of node centre
     * @param sy      screen Y of node centre
     * @param zoom    current camera zoom (used to scale title chip)
     * @param showTitle whether to draw the title chip
     */
    public static void draw(GraphicsContext gc, GraphNode node,
                            double sx, double sy, double zoom, boolean showTitle) {
        double r = radius(node);

        Color base  = node.clusterColor != null ? node.clusterColor : Theme.ACCENT;
        Color ring  = base.brighter();
        Color inner = base.interpolate(Color.WHITE, 0.35);

        // ── 1. Glow halos (hover / select) ────────────────────────────────
        if (node.selected) {
            Theme.drawGlow(gc, sx, sy, r, base, new double[][]{
                    {3.0, 0.07}, {2.2, 0.14}, {1.6, 0.26}, {1.25, 0.35}
            });
        } else if (node.hovered) {
            Theme.drawNodeHalo(gc, sx, sy, r, base);
        }

        // ── 2. Shadow (elevation on hover/select) ─────────────────────────
        if (node.hovered || node.selected) {
            gc.setFill(Color.color(0, 0, 0, 0.3));
            gc.fillOval(sx - r + 2, sy - r + 4, r * 2, r * 2);
        }

        // ── 3. Fill circle ────────────────────────────────────────────────
        double alpha = node.dimmed ? 0.20 : 1.0;
        gc.setFill(Theme.alpha(base, 0.85 * alpha));
        gc.fillOval(sx - r, sy - r, r * 2, r * 2);

        // Inner radial highlight (lighter centre)
        gc.setFill(Theme.alpha(inner, 0.30 * alpha));
        double h = r * 0.55;
        gc.fillOval(sx - h, sy - r * 0.55, h * 2, h * 1.6);

        // ── 4. Outer ring ─────────────────────────────────────────────────
        double strokeWidth = node.selected ? 2.5 : (node.hovered ? 2.0 : 1.2);
        gc.setStroke(Theme.alpha(ring, 0.85 * alpha));
        gc.setLineWidth(strokeWidth);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);

        // ── 5. Anchor gold inner ring ─────────────────────────────────────
        if (node.isAnchor) {
            double ar = r * 0.60;
            gc.setStroke(Theme.alpha(Color.web("#f1c40f"), 0.80 * alpha));
            gc.setLineWidth(1.5);
            gc.strokeOval(sx - ar, sy - ar, ar * 2, ar * 2);
        }

        // ── 6. Year label ─────────────────────────────────────────────────
        if (node.year > 0 && r >= 14) {
            gc.setFill(Theme.alpha(Color.WHITE, 0.90 * alpha));
            gc.setFont(YEAR_FONT);
            gc.setTextAlign(TextAlignment.CENTER);
            String yearStr = String.valueOf(node.year);
            gc.fillText(yearStr, sx, sy + 3.5);
        }

        // ── 7. Title chip (below the circle) ─────────────────────────────
        if (showTitle && node.title != null && !node.title.isEmpty()) {
            drawTitleChip(gc, node, sx, sy + r + 4, zoom, alpha);
        }
    }

    // ── Title chip ─────────────────────────────────────────────────────────

    private static void drawTitleChip(GraphicsContext gc, GraphNode node,
                                      double cx, double topY, double zoom, double alpha) {
        String title = node.title;
        double maxW  = Math.max(80, 120 * zoom);
        double lineH = 12.0;

        // Wrap to at most 2 lines.
        String[] lines = wrapTitle(title, maxW, gc);

        double chipW = maxW + 8;
        double chipH = lines.length * lineH + 4;
        double chipX = cx - chipW / 2.0;
        double chipY = topY;

        // Pill background
        gc.setFill(Color.color(0.06, 0.06, 0.14, 0.72 * alpha));
        fillRoundRect(gc, chipX, chipY, chipW, chipH, 5);

        // Text lines
        gc.setFont(TITLE_FONT);
        gc.setTextAlign(TextAlignment.CENTER);

        Color textColor = node.selected
                ? Theme.alpha(Color.WHITE, alpha)
                : node.hovered
                ? Theme.alpha(Color.web("#c8d4ff"), alpha)
                : Theme.alpha(Color.web(Theme.TEXT_DIM_HEX), alpha * 0.85);
        gc.setFill(textColor);

        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], cx, chipY + lineH * (i + 1) - 1);
        }
    }

    /** Very lightweight greedy word-wrap (avoids full JavaFX text layout overhead). */
    private static String[] wrapTitle(String title, double maxW, GraphicsContext gc) {
        // Approximate: 5.5px per character at font size 10.
        double charW = 5.5;
        int charsPerLine = Math.max(12, (int) (maxW / charW));

        if (title.length() <= charsPerLine) return new String[]{title};

        // Find a break point near the middle.
        int mid = title.length() / 2;
        int breakAt = mid;
        for (int d = 0; d <= mid; d++) {
            if (mid - d >= 0 && title.charAt(mid - d) == ' ') { breakAt = mid - d; break; }
            if (mid + d < title.length() && title.charAt(mid + d) == ' ') { breakAt = mid + d; break; }
        }
        String l1 = title.substring(0, breakAt).trim();
        String l2 = title.substring(breakAt).trim();
        if (l2.length() > charsPerLine) l2 = l2.substring(0, charsPerLine - 1) + "…";
        return new String[]{l1, l2};
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** World-space radius for a node (used also by EdgeRouter for boundary calc). */
    public static double radius(GraphNode node) {
        double r = BASE_RADIUS + node.importance * RADIUS_RANGE;
        if (node.isAnchor) r += ANCHOR_BONUS;
        return r;
    }

    private static void fillRoundRect(GraphicsContext gc,
                                      double x, double y, double w, double h, double arc) {
        gc.beginPath();
        gc.moveTo(x + arc, y);
        gc.lineTo(x + w - arc, y);
        gc.quadraticCurveTo(x + w, y, x + w, y + arc);
        gc.lineTo(x + w, y + h - arc);
        gc.quadraticCurveTo(x + w, y + h, x + w - arc, y + h);
        gc.lineTo(x + arc, y + h);
        gc.quadraticCurveTo(x, y + h, x, y + h - arc);
        gc.lineTo(x, y + arc);
        gc.quadraticCurveTo(x, y, x + arc, y);
        gc.closePath();
        gc.fill();
    }
}
