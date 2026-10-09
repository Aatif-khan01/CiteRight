package com.citeright.ui.graph;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws {@link GraphEdge}s as styled cubic Bézier curves onto a JavaFX Canvas.
 *
 * <h3>Visual contract</h3>
 * <ul>
 *   <li><b>Inactive edges</b> (another node is selected, but this edge is not
 *       incident): rendered at ~15% opacity so the selected neighbourhood pops.</li>
 *   <li><b>Directed types</b>: filled arrowhead at the destination.</li>
 *   <li><b>Selected node mode</b>: all incident edges drawn at full opacity with
 *       an inline label — e.g. {@code "SUPPORTS · 87%"} or
 *       {@code "SUPPORTS · 87% · AI"} — positioned near the midpoint of the
 *       curve, shifted slightly to stay readable.</li>
 *   <li><b>Hover label</b>: single-edge inline label shown while the edge (or
 *       its incident node) is hovered.</li>
 *   <li><b>Flowing particles</b>: when an edge is selected (incident to the
 *       selected node), small dots march along the Bézier path driven by
 *       {@link GraphEdge#particlePhase} and the current {@code now} timestamp.</li>
 *   <li><b>RELATED (TF-IDF)</b> edges: thinner, lower opacity, no label unless
 *       hovered/selected.</li>
 * </ul>
 */
public final class EdgeRenderer {

    private static final Font  LABEL_FONT  = Font.font("System", FontWeight.BOLD, 9);
    private static final double ARROW_LEN  = 9.0;
    private static final double ARROW_HALF = 4.0;
    private static final int   PARTICLE_COUNT = 3;

    private EdgeRenderer() {}

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * Draw one edge.
     *
     * @param gc           canvas graphics context
     * @param edge         the edge to draw
     * @param path         pre-computed Bézier path from {@link EdgeRouter}
     * @param camera       camera for world↔screen transforms
     * @param selectedNode currently selected node, or {@code null}
     * @param nowSec       current time in seconds (drives particle animation)
     */
    public static void draw(GraphicsContext gc, GraphEdge edge, EdgeRouter.Path path,
                            GraphCamera camera, GraphNode selectedNode, double nowSec) {

        boolean incidentToSelected = selectedNode != null
                && (edge.a == selectedNode || edge.b == selectedNode);
        boolean dimmed = selectedNode != null && !incidentToSelected;

        double opacity = dimmed ? 0.13
                : (0.35 + edge.confidence * 0.65);

        Color baseColor = edge.type.getColor();
        Color drawColor = Theme.alpha(baseColor, opacity);

        // ── 1. Stroke width ────────────────────────────────────────────────
        double sw = strokeWidth(edge);
        gc.setLineWidth(sw);
        gc.setStroke(drawColor);

        // Screen-space control points.
        double x0 = camera.worldToScreenX(path.x0), y0 = camera.worldToScreenY(path.y0);
        double x1 = camera.worldToScreenX(path.x1), y1 = camera.worldToScreenY(path.y1);
        double x2 = camera.worldToScreenX(path.x2), y2 = camera.worldToScreenY(path.y2);
        double x3 = camera.worldToScreenX(path.x3), y3 = camera.worldToScreenY(path.y3);

        // ── 2. Glow stroke (for selected / curated edges) ──────────────────
        if (incidentToSelected && edge.type != RelationshipType.RELATED) {
            gc.setStroke(Theme.alpha(baseColor, 0.25));
            gc.setLineWidth(sw + 4);
            drawBezier(gc, x0, y0, x1, y1, x2, y2, x3, y3);
            gc.setStroke(drawColor);
            gc.setLineWidth(sw);
        }

        // ── 3. Main curve ─────────────────────────────────────────────────
        drawBezier(gc, x0, y0, x1, y1, x2, y2, x3, y3);

        // ── 4. Arrowhead (directed types) ─────────────────────────────────
        if (edge.type.isDirected() && !dimmed) {
            drawArrow(gc, x2, y2, x3, y3, drawColor, sw);
        }

        // ── 5. Flowing particles (on incident edges while node selected) ───
        if (incidentToSelected && edge.type != RelationshipType.RELATED) {
            drawParticles(gc, path, camera, edge, baseColor, opacity, nowSec);
        }

        // ── 6. Inline label ───────────────────────────────────────────────
        boolean showLabel = (incidentToSelected && edge.type != RelationshipType.RELATED)
                || edge.hovered;
        if (showLabel) {
            drawInlineLabel(gc, path, camera, edge, opacity);
        }
    }

    // ── Curve ───────────────────────────────────────────────────────────────

    private static void drawBezier(GraphicsContext gc,
                                   double x0, double y0,
                                   double x1, double y1,
                                   double x2, double y2,
                                   double x3, double y3) {
        gc.beginPath();
        gc.moveTo(x0, y0);
        gc.bezierCurveTo(x1, y1, x2, y2, x3, y3);
        gc.stroke();
    }

    // ── Arrowhead ───────────────────────────────────────────────────────────

    private static void drawArrow(GraphicsContext gc,
                                  double cpX, double cpY,
                                  double tipX, double tipY,
                                  Color color, double sw) {
        double dx = tipX - cpX;
        double dy = tipY - cpY;
        double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;

        double baseX = tipX - ux * ARROW_LEN;
        double baseY = tipY - uy * ARROW_LEN;

        gc.setFill(color);
        gc.beginPath();
        gc.moveTo(tipX, tipY);
        gc.lineTo(baseX + nx * ARROW_HALF, baseY + ny * ARROW_HALF);
        gc.lineTo(baseX - nx * ARROW_HALF, baseY - ny * ARROW_HALF);
        gc.closePath();
        gc.fill();
    }

    // ── Flowing particles ───────────────────────────────────────────────────

    private static void drawParticles(GraphicsContext gc, EdgeRouter.Path path,
                                      GraphCamera camera, GraphEdge edge,
                                      Color color, double opacity, double nowSec) {
        double speed = 0.18; // fraction of curve per second
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double t = (edge.particlePhase + (double) i / PARTICLE_COUNT
                    + nowSec * speed) % 1.0;
            double[] wp = path.pointAt(t);
            double sx = camera.worldToScreenX(wp[0]);
            double sy = camera.worldToScreenY(wp[1]);

            double pr = 2.5 + edge.confidence;
            // Fade in/out near ends.
            double fade = Math.min(t * 8, Math.min((1 - t) * 8, 1.0));
            gc.setFill(Theme.alpha(color.brighter(), opacity * fade * 0.9));
            gc.fillOval(sx - pr, sy - pr, pr * 2, pr * 2);
        }
    }

    // ── Inline label ────────────────────────────────────────────────────────

    private static void drawInlineLabel(GraphicsContext gc, EdgeRouter.Path path,
                                        GraphCamera camera, GraphEdge edge,
                                        double opacity) {
        // Sample midpoint of curve.
        double[] mid = path.pointAt(0.5);
        double sx = camera.worldToScreenX(mid[0]);
        double sy = camera.worldToScreenY(mid[1]);

        // Build label text.
        StringBuilder sb = new StringBuilder();
        sb.append(edge.type.getLabel().toUpperCase());
        sb.append(" · ").append((int) Math.round(edge.confidence * 100)).append("%");
        if (edge.isAISuggestion) sb.append(" · AI");
        String label = sb.toString();

        // Label pill background.
        double charW  = 5.0;
        double pillW  = label.length() * charW + 10;
        double pillH  = 14.0;
        double pillX  = sx - pillW / 2.0;
        double pillY  = sy - pillH / 2.0;

        gc.setFill(Color.color(0.05, 0.05, 0.12, 0.82 * opacity));
        fillRoundRect(gc, pillX, pillY, pillW, pillH, 4);

        // Label text.
        gc.setFont(LABEL_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Theme.alpha(edge.type.getColor().brighter(), opacity));
        gc.fillText(label, sx, pillY + pillH - 4);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static double strokeWidth(GraphEdge edge) {
        if (edge.type == RelationshipType.RELATED) {
            return 0.8 + edge.confidence * 1.2; // thin
        }
        return 1.5 + edge.confidence * 2.5;
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
