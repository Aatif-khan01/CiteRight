package com.citeright.ui.graph;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Map;

/**
 * Canvas orchestrator for the Macro Paper Graph view.
 *
 * <p>Called on every animation frame (or on-demand when the layout is frozen).
 * Drawing order:
 * <ol>
 *   <li>Background fill + dot grid</li>
 *   <li>Cluster pass — translucent fill, padded adaptive boundary, outer glow,
 *       animated shimmer border, smart label badge</li>
 *   <li>Edge pass — delegates to {@link EdgeRenderer}</li>
 *   <li>Node pass — delegates to {@link NodeRenderer}</li>
 * </ol>
 *
 * <p>Zoom-level label gating: at {@code zoom < 0.55} only anchor + top-20%
 * importance nodes receive their title chips; at {@code zoom >= 0.8} all nodes
 * receive them.
 */
public final class GraphRenderer {

    private static final double DOT_SPACING  = 28.0; // world units between grid dots
    private static final double SHIMMER_SPEED = 0.4;  // radians per second

    private static final Font CLUSTER_TITLE_FONT   = Font.font("System", FontWeight.BOLD, 11);
    private static final Font CLUSTER_KEYWORD_FONT = Font.font("System", FontWeight.NORMAL, 9);
    private static final Font CLUSTER_COUNT_FONT   = Font.font("System", FontWeight.BOLD, 9);

    private final GraphModel             model;
    private final HierarchicalClusterLayout layout;
    private final EdgeRouter             router;
    private final GraphCamera            camera;

    public GraphRenderer(GraphModel model, HierarchicalClusterLayout layout,
                         EdgeRouter router, GraphCamera camera) {
        this.model  = model;
        this.layout = layout;
        this.router = router;
        this.camera = camera;
    }

    // ── Main redraw ─────────────────────────────────────────────────────────

    /**
     * Repaint the entire canvas.
     *
     * @param canvas       the canvas to paint
     * @param selectedNode currently selected node (may be {@code null})
     * @param hoveredNode  currently hovered node (may be {@code null})
     * @param previewNode  currently previewed node (single click, may be {@code null})
     * @param nowSec       current time in seconds (drives shimmer + particles)
     */
    public void redraw(Canvas canvas, GraphNode selectedNode, GraphNode hoveredNode, GraphNode previewNode, double nowSec) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        double zoom = camera.getZoom();

        // 1. Background.
        gc.setFill(Theme.BG);
        gc.fillRect(0, 0, W, H);

        // 2. Dot grid.
        drawDotGrid(gc, W, H);

        if (model.nodeCount() == 0) {
            drawEmptyState(gc, W, H);
            return;
        }

        // 3. Cluster pass.
        Map<String, double[]>     bounds   = layout.clusterBounds();
        Map<String, Color>         colors   = model.getClusterColors();
        Map<String, List<String>>  keywords = model.getClusterKeywords();
        Map<String, List<GraphNode>> nodeMap  = model.getClusterNodeMap();

        for (Map.Entry<String, double[]> entry : bounds.entrySet()) {
            String label = entry.getKey();
            double[] b   = entry.getValue();
            Color color  = colors.getOrDefault(label, Theme.ACCENT);
            List<String> kws  = keywords.get(label);
            List<GraphNode> members = nodeMap.get(label);
            int count = members != null ? members.size() : 0;
            drawCluster(gc, label, b, color, kws, count, nowSec, zoom);
        }

        // 4. Edge pass.
        for (GraphEdge edge : model.getEdges()) {
            EdgeRouter.Path path = router.path(edge);
            EdgeRenderer.draw(gc, edge, path, camera, selectedNode, nowSec);
        }

        // 5. Node pass.
        // Importance threshold for title gating.
        double titleThresh = zoom < NodeRenderer.TITLE_ZOOM_THRESHOLD ? 0.60
                : zoom < 0.8 ? 0.25
                : 0.0;

        for (GraphNode node : model.getNodes()) {
            double sx = camera.worldToScreenX(node.x);
            double sy = camera.worldToScreenY(node.y);
            boolean showTitle = node.isAnchor || node.importance >= titleThresh;
            NodeRenderer.draw(gc, node, sx, sy, zoom, showTitle);
        }

        // 6. Floating hover card (smooth preview without opening inspector).
        if (previewNode != null) {
            drawHoverCard(gc, previewNode, W, H);
        } else if (hoveredNode != null && !hoveredNode.selected) {
            drawHoverCard(gc, hoveredNode, W, H);
        }

        // 7. Mini-map (bottom right overlay).
        drawMiniMap(gc, W, H);
    }

    // ── Dot grid ────────────────────────────────────────────────────────────

    private void drawDotGrid(GraphicsContext gc, double W, double H) {
        double zoom = camera.getZoom();
        double spacingPx = DOT_SPACING * zoom;
        if (spacingPx < 8) return; // too dense to draw

        double offX = camera.centerX() % spacingPx;
        double offY = camera.centerY() % spacingPx;

        gc.setFill(Color.web(Theme.DOT_GRID_HEX, 0.7));
        double dotR = Math.max(0.5, zoom * 0.8);
        for (double x = offX; x < W; x += spacingPx) {
            for (double y = offY; y < H; y += spacingPx) {
                gc.fillOval(x - dotR, y - dotR, dotR * 2, dotR * 2);
            }
        }
    }

    // ── Empty state ─────────────────────────────────────────────────────────

    private static void drawEmptyState(GraphicsContext gc, double W, double H) {
        gc.setFill(Color.web(Theme.TEXT_FAINT_HEX));
        gc.setFont(Font.font("System", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("No papers in library yet.\nAdd papers to see the graph.", W / 2, H / 2);
    }

    // ── Cluster pass ────────────────────────────────────────────────────────

    private void drawCluster(GraphicsContext gc,
                             String label, double[] bounds,
                             Color color, List<String> keywords,
                             int memberCount, double nowSec, double zoom) {

        double wx0 = bounds[0], wy0 = bounds[1];
        double wx1 = bounds[2], wy1 = bounds[3];

        double sx0 = camera.worldToScreenX(wx0), sy0 = camera.worldToScreenY(wy0);
        double sx1 = camera.worldToScreenX(wx1), sy1 = camera.worldToScreenY(wy1);

        double sw = sx1 - sx0;
        double sh = sy1 - sy0;
        if (sw < 4 || sh < 4) return; // off-screen or too small

        double arc = Math.min(sw, sh) * 0.35;

        // a. Translucent fill.
        gc.setFill(Theme.alpha(color, 0.07));
        gc.fillRoundRect(sx0, sy0, sw, sh, arc, arc);

        // b. Shimmer border (phase-shifted dashed stroke).
        double phase = nowSec * SHIMMER_SPEED;
        double dashLen = 10.0;
        gc.setLineDashes(dashLen, dashLen * 0.6);
        gc.setLineDashOffset(phase * dashLen * 2);
        gc.setStroke(Theme.alpha(color, 0.30));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(sx0, sy0, sw, sh, arc, arc);
        gc.setLineDashes(null);

        // c. Outer glow (three-layer concentric rounded rects).
        double[] glowLayers = {0.08, 0.05, 0.03};
        double[] glowExpand = {8, 16, 28};
        for (int i = 0; i < glowLayers.length; i++) {
            double ex = glowExpand[i];
            gc.setStroke(Theme.alpha(color, glowLayers[i]));
            gc.setLineWidth(ex);
            gc.setLineDashes(null);
            gc.strokeRoundRect(sx0 - ex / 2, sy0 - ex / 2,
                    sw + ex, sh + ex, arc + ex / 2, arc + ex / 2);
        }
        gc.setLineWidth(1.0);

        // d. Smart label badge (top-centre of cluster).
        if (zoom > 0.35) {
            drawClusterBadge(gc, label, memberCount, keywords, sx0, sy0, sw, color);
        }
    }

    private static void drawClusterBadge(GraphicsContext gc,
                                         String clusterLabel, int count,
                                         List<String> keywords,
                                         double sx0, double sy0, double sw,
                                         Color color) {
        // Compose badge text.
        String title   = truncate(clusterLabel, 24);
        String countTxt = count + " paper" + (count != 1 ? "s" : "");
        String kwLine  = keywords != null && !keywords.isEmpty()
                ? String.join(" · ", keywords.subList(0, Math.min(3, keywords.size())))
                : "";

        double cx  = sx0 + sw / 2.0;
        double topY = sy0 - 2;

        double badgeW = Math.max(110, title.length() * 6.5 + 20);
        double badgeH = kwLine.isEmpty() ? 26 : 38;
        double badgeX = cx - badgeW / 2.0;
        double badgeY = topY - badgeH - 2;

        // Pill background.
        gc.setFill(Color.color(0.06, 0.07, 0.15, 0.88));
        fillRoundRect(gc, badgeX, badgeY, badgeW, badgeH, 6);

        // Coloured left accent bar.
        gc.setFill(Theme.alpha(color, 0.9));
        fillRoundRect(gc, badgeX, badgeY, 3, badgeH, 2);

        // Title line.
        gc.setFont(CLUSTER_TITLE_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.WHITE);
        gc.fillText(title, cx, badgeY + 13);

        // Count badge inline.
        gc.setFont(CLUSTER_COUNT_FONT);
        gc.setFill(Theme.alpha(color, 0.85));
        gc.fillText(countTxt, cx, badgeY + 24);

        // Keyword line.
        if (!kwLine.isEmpty()) {
            gc.setFont(CLUSTER_KEYWORD_FONT);
            gc.setFill(Color.web(Theme.TEXT_FAINT_HEX, 0.9));
            gc.fillText(kwLine, cx, badgeY + 35);
        }
    }

    // ── Floating Hover Card ──────────────────────────────────────────────────

    private void drawHoverCard(GraphicsContext gc, GraphNode node, double canvasW, double canvasH) {
        double sx = camera.worldToScreenX(node.x);
        double sy = camera.worldToScreenY(node.y);

        double cardW = 200.0;
        double cardH = 86.0;

        // Position above-right of node, flipping if near edges.
        double cardX = sx + 24;
        if (cardX + cardW > canvasW - 10) cardX = sx - cardW - 24;
        double cardY = sy - 40;
        if (cardY < 10) cardY = 10;
        if (cardY + cardH > canvasH - 10) cardY = canvasH - cardH - 10;

        // Card glass background + shadow.
        gc.setFill(Color.color(0.0, 0.0, 0.0, 0.45));
        fillRoundRect(gc, cardX + 3, cardY + 4, cardW, cardH, 8);

        gc.setFill(Color.color(0.07, 0.08, 0.18, 0.94));
        fillRoundRect(gc, cardX, cardY, cardW, cardH, 8);

        Color c = node.clusterColor != null ? node.clusterColor : Theme.ACCENT;
        gc.setStroke(Theme.alpha(c, 0.65));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(cardX, cardY, cardW, cardH, 8, 8);

        // Accent top bar.
        gc.setFill(c);
        fillRoundRect(gc, cardX, cardY, cardW, 3, 2);

        // Title.
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.WHITE);
        String title = truncate(node.title, 32);
        gc.fillText(title, cardX + 10, cardY + 20);

        // Authors / year.
        gc.setFont(Font.font("System", FontWeight.NORMAL, 9.5));
        gc.setFill(Color.web(Theme.TEXT_DIM_HEX));
        String meta = (node.entry != null && node.entry.getPublication() != null)
                ? node.entry.getPublication().getAuthorsShort() + " (" + node.year + ")"
                : "Year " + node.year;
        gc.fillText(truncate(meta, 35), cardX + 10, cardY + 36);

        // Topic / Citations row.
        gc.setFont(Font.font("System", FontWeight.BOLD, 9.0));
        gc.setFill(Theme.alpha(c, 0.9));
        String topic = node.clusterLabel != null ? truncate(node.clusterLabel, 18) : "Topic";
        gc.fillText("Topic: " + topic, cardX + 10, cardY + 54);

        int citCount = (node.entry != null && node.entry.getPublication() != null)
                ? node.entry.getPublication().getCitationCount() : 0;
        gc.setFill(Color.web("#2ecc71"));
        gc.fillText("Citations: " + citCount + " · Importance: " + (int)(node.importance * 100) + "%", cardX + 10, cardY + 70);
    }

    // ── Mini-Map ─────────────────────────────────────────────────────────────

    private void drawMiniMap(GraphicsContext gc, double W, double H) {
        double mmW = 160.0;
        double mmH = 110.0;
        double mmX = W - mmW - 14;
        double mmY = H - mmH - 14;

        // Container box with dark glass fill + border.
        gc.setFill(Color.color(0.06, 0.07, 0.16, 0.88));
        fillRoundRect(gc, mmX, mmY, mmW, mmH, 8);
        gc.setStroke(Color.web(Theme.BORDER_HEX, 0.8));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(mmX, mmY, mmW, mmH, 8, 8);

        // Title + zoom indicator inside mini map header.
        gc.setFont(Font.font("System", FontWeight.BOLD, 8.5));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web(Theme.TEXT_FAINT_HEX));
        gc.fillText("MINI-MAP", mmX + 8, mmY + 12);

        int zoomPct = (int) Math.round(camera.getZoom() * 100);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(zoomPct + "%", mmX + mmW - 8, mmY + 12);

        // Compute world bounding box of nodes.
        double minX = -400, maxX = 400, minY = -300, maxY = 300;
        for (GraphNode n : model.getNodes()) {
            if (n.x < minX) minX = n.x;
            if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.y > maxY) maxY = n.y;
        }
        double pad = 60;
        minX -= pad; maxX += pad; minY -= pad; maxY += pad;
        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        // Map area bounds inside mini-map.
        double mapX0 = mmX + 8, mapY0 = mmY + 18;
        double mapW  = mmW - 16, mapH  = mmH - 26;

        // Draw node dots in mini-map.
        for (GraphNode n : model.getNodes()) {
            double nx = mapX0 + ((n.x - minX) / rangeX) * mapW;
            double ny = mapY0 + ((n.y - minY) / rangeY) * mapH;
            Color c = n.clusterColor != null ? n.clusterColor : Theme.ACCENT;
            gc.setFill(n.selected ? Color.WHITE : Theme.alpha(c, 0.85));
            double r = n.isAnchor ? 2.5 : 1.8;
            gc.fillOval(nx - r, ny - r, r * 2, r * 2);
        }

        // Draw camera viewport rectangle.
        double camWorldX0 = camera.screenToWorldX(0);
        double camWorldY0 = camera.screenToWorldY(0);
        double camWorldX1 = camera.screenToWorldX(W);
        double camWorldY1 = camera.screenToWorldY(H);

        double vx0 = mapX0 + Math.max(0, Math.min(1, (camWorldX0 - minX) / rangeX)) * mapW;
        double vy0 = mapY0 + Math.max(0, Math.min(1, (camWorldY0 - minY) / rangeY)) * mapH;
        double vx1 = mapX0 + Math.max(0, Math.min(1, (camWorldX1 - minX) / rangeX)) * mapW;
        double vy1 = mapY0 + Math.max(0, Math.min(1, (camWorldY1 - minY) / rangeY)) * mapH;

        gc.setStroke(Color.web(Theme.ACCENT_HEX, 0.7));
        gc.setLineWidth(1.0);
        gc.strokeRect(vx0, vy0, Math.max(8, vx1 - vx0), Math.max(6, vy1 - vy0));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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
