package com.citeright.ui.graph;

import java.util.*;

/**
 * Metro-map edge routing.
 *
 * <p>For every edge we compute a cubic Bézier path
 * {@code P0 (src boundary) → P1, P2 (control pts) → P3 (dst boundary)} that:
 * <ol>
 *   <li><b>Avoids nodes</b> — samples the curve and pushes the control points
 *       off any non-endpoint node whose circle it would pass through.</li>
 *   <li><b>Avoids foreign clusters</b> — nudges the curve around the bounding
 *       box of clusters neither endpoint belongs to.</li>
 *   <li><b>Bundles</b> — edges sharing an endpoint and a similar heading are
 *       pulled toward a shared mid control point so they merge mid-flight,
 *       fanning out only near the destination (airline-route look).</li>
 *   <li><b>Separates parallels</b> — same-pair / parallel edges get a small
 *       perpendicular offset so they stay distinct at the endpoints.</li>
 * </ol>
 *
 * <p>Paths are cached per layout generation; call {@link #invalidate()} when
 * node positions change (filter, drag-commit, re-layout).
 */
public final class EdgeRouter {

    /** A computed cubic Bézier path in world space. */
    public static final class Path {
        public final double x0, y0, x1, y1, x2, y2, x3, y3;
        public Path(double x0, double y0, double x1, double y1,
                    double x2, double y2, double x3, double y3) {
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.x2 = x2; this.y2 = y2;
            this.x3 = x3; this.y3 = y3;
        }
        /** Point on the curve at parameter t (0..1). */
        public double[] pointAt(double t) {
            double u = 1 - t;
            double x = u*u*u*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3;
            double y = u*u*u*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3;
            return new double[]{x, y};
        }
    }

    private static final double NODE_AVOID_MARGIN = 30.0;   // was 14 — bigger safe zone
    private static final double BUNDLE_PULL = 0.45;          // was 0.55 — gentler bundling
    private static final double PARALLEL_OFFSET = 12.0;      // was 10
    private static final int    AVOID_ITERATIONS = 3;        // multi-pass obstacle avoidance

    private final GraphModel model;
    private final HierarchicalClusterLayout layout;
    private final Map<GraphEdge, Path> cache = new IdentityHashMap<>();
    private boolean valid = false;

    public EdgeRouter(GraphModel model, HierarchicalClusterLayout layout) {
        this.model = model;
        this.layout = layout;
    }

    /** Mark cached paths stale; the next {@link #path(GraphEdge)} recomputes. */
    public void invalidate() {
        cache.clear();
        valid = false;
    }

    /** (Re)compute all paths in one pass so bundling sees its neighbours. */
    public void computeAll() {
        cache.clear();
        List<GraphEdge> edges = model.getEdges();
        Map<String, double[]> clusterBounds = layout.clusterBounds();
        Map<GraphEdge, Integer> parallelIndex = parallelIndices(edges);

        // First pass: base control points + perpendicular offset.
        for (GraphEdge e : edges) {
            cache.put(e, basePath(e, parallelIndex, clusterBounds));
        }
        // Multiple passes of obstacle avoidance for better results.
        for (int pass = 0; pass < AVOID_ITERATIONS; pass++) {
            for (GraphEdge e : edges) {
                Path p = cache.get(e);
                cache.put(e, avoidObstacles(e, p, clusterBounds));
            }
        }
        // Third pass: bundling (merge shared-heading edges).
        bundlePass();

        valid = true;
    }

    /** Get the path for an edge, computing all paths lazily if needed. */
    public Path path(GraphEdge edge) {
        if (!valid) computeAll();
        Path p = cache.get(edge);
        if (p == null) {
            p = basePath(edge, Map.of(), layout.clusterBounds());
            cache.put(edge, p);
        }
        return p;
    }

    // ── Base path ──────────────────────────────────────────────────────────

    private Path basePath(GraphEdge e, Map<GraphEdge, Integer> parallelIndex,
                          Map<String, double[]> clusterBounds) {
        GraphNode a = e.a;
        GraphNode b = e.b;

        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        double ux = dx / len;
        double uy = dy / len;
        // Perpendicular (left-hand normal).
        double nx = -uy;
        double ny = ux;

        // Parallel-edge perpendicular offset so duplicates don't overlap.
        Integer idx = parallelIndex.get(e);
        int pi = (idx == null) ? 0 : idx;
        int parity = (pi % 2 == 0) ? 1 : -1;
        double offset = (pi == 0) ? 0.0 : PARALLEL_OFFSET * parity * ((pi + 1) / 2);

        double ra = radiusOf(a);
        double rb = radiusOf(b);

        // Start/end on node boundaries, shifted by the parallel offset.
        double x0 = a.x + ux * ra + nx * offset;
        double y0 = a.y + uy * ra + ny * offset;
        double x3 = b.x - ux * rb + nx * offset;
        double y3 = b.y - uy * rb + ny * offset;

        // Base curve magnitude scales with chord length (proportional curvature).
        double curve = len * 0.15;  // was 0.18 — slightly less curvature for cleaner look
        // Deterministic sign.
        double sign = (stableHash(e) & 1) == 0 ? 1.0 : -1.0;

        // If endpoints are in different clusters, route around foreign clusters.
        double midShiftX = 0, midShiftY = 0;
        if (a.clusterLabel != null && !a.clusterLabel.equals(b.clusterLabel)) {
            double[] nudge = clusterAvoidance(a, b, clusterBounds);
            midShiftX = nudge[0];
            midShiftY = nudge[1];
        }

        double mx = (x0 + x3) / 2.0 + nx * curve * sign + midShiftX;
        double my = (y0 + y3) / 2.0 + ny * curve * sign + midShiftY;

        double x1 = x0 + (mx - x0) * 0.5;
        double y1 = y0 + (my - y0) * 0.5;
        double x2 = x3 + (mx - x3) * 0.5;
        double y2 = y3 + (my - y3) * 0.5;

        return new Path(x0, y0, x1, y1, x2, y2, x3, y3);
    }

    // ── Obstacle avoidance (nodes) ─────────────────────────────────────────

    private Path avoidObstacles(GraphEdge e, Path p, Map<String, double[]> clusterBounds) {
        List<GraphNode> obstacles = model.getNodes();
        double dispX = 0, dispY = 0;

        // Sample the curve at more points for better coverage (was 5, now 9).
        for (int s = 1; s <= 9; s++) {
            double t = s / 10.0;
            double[] pt = p.pointAt(t);
            for (GraphNode n : obstacles) {
                if (n == e.a || n == e.b) continue;
                double dx = pt[0] - n.x;
                double dy = pt[1] - n.y;
                double d = Math.sqrt(dx * dx + dy * dy);
                double safe = radiusOf(n) + NODE_AVOID_MARGIN;
                if (d < safe && d > 0.01) {
                    double push = (safe - d) / d;  // stronger push proportional to penetration
                    dispX += dx * push;
                    dispY += dy * push;
                }
            }
        }

        if (Math.abs(dispX) < 0.5 && Math.abs(dispY) < 0.5) return p;

        // Push both control points along the displacement.
        double scale = 0.35; // was 0.6 per push but we accumulate more now
        return new Path(
                p.x0, p.y0,
                p.x1 + dispX * scale, p.y1 + dispY * scale,
                p.x2 + dispX * scale, p.y2 + dispY * scale,
                p.x3, p.y3);
    }

    // ── Cluster avoidance (mid control-point nudge) ───────────────────────

    private double[] clusterAvoidance(GraphNode a, GraphNode b,
                                      Map<String, double[]> clusterBounds) {
        double ax = a.x, ay = a.y;
        double bx = b.x, by = b.y;
        double mx = (ax + bx) / 2.0;
        double my = (ay + by) / 2.0;
        double pushX = 0, pushY = 0;

        for (Map.Entry<String, double[]> en : clusterBounds.entrySet()) {
            String label = en.getKey();
            if (label.equals(a.clusterLabel) || label.equals(b.clusterLabel)) continue;
            double[] r = en.getValue();
            // If the midpoint is inside this foreign cluster's bounds, push out.
            if (mx > r[0] && mx < r[2] && my > r[1] && my < r[3]) {
                double dLeft  = mx - r[0];
                double dRight = r[2] - mx;
                double dTop   = my - r[1];
                double dBot   = r[3] - my;
                double min = Math.min(Math.min(dLeft, dRight), Math.min(dTop, dBot));
                double pad = 40; // was 20
                if (min == dLeft)       pushX -= (dLeft + pad);
                else if (min == dRight) pushX += (dRight + pad);
                else if (min == dTop)   pushY -= (dTop + pad);
                else                    pushY += (dBot + pad);
            }
        }
        return new double[]{pushX, pushY};
    }

    // ── Bundling ───────────────────────────────────────────────────────────

    private void bundlePass() {
        Map<String, List<GraphEdge>> byBundle = new HashMap<>();
        for (GraphEdge e : model.getEdges()) {
            if (e.bundleGroup == null) continue;
            byBundle.computeIfAbsent(e.bundleGroup, k -> new ArrayList<>()).add(e);
        }
        for (List<GraphEdge> bucket : byBundle.values()) {
            if (bucket.size() < 2) continue;
            double sx = 0, sy = 0;
            for (GraphEdge e : bucket) {
                Path p = cache.get(e);
                double mx = 0.5 * (p.x1 + p.x2);
                double my = 0.5 * (p.y1 + p.y2);
                sx += mx; sy += my;
            }
            sx /= bucket.size();
            sy /= bucket.size();
            for (GraphEdge e : bucket) {
                Path p = cache.get(e);
                double mx = 0.5 * (p.x1 + p.x2);
                double my = 0.5 * (p.y1 + p.y2);
                double nmx = mx + (sx - mx) * BUNDLE_PULL;
                double nmy = my + (sy - my) * BUNDLE_PULL;
                cache.put(e, new Path(
                        p.x0, p.y0,
                        p.x0 + (nmx - p.x0) * 0.5, p.y0 + (nmy - p.y0) * 0.5,
                        p.x3 + (nmx - p.x3) * 0.5, p.y3 + (nmy - p.y3) * 0.5,
                        p.x3, p.y3));
            }
        }
    }

    // ── Parallel-edge indexing ─────────────────────────────────────────────

    private Map<GraphEdge, Integer> parallelIndices(List<GraphEdge> edges) {
        Map<Long, List<GraphEdge>> pairs = new HashMap<>();
        for (GraphEdge e : edges) {
            int idA = e.a.paperId();
            int idB = e.b.paperId();
            int lo = Math.min(idA, idB);
            int hi = Math.max(idA, idB);
            long key = ((long) lo << 32) | (hi & 0xffffffffL);
            pairs.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        Map<GraphEdge, Integer> out = new IdentityHashMap<>();
        for (List<GraphEdge> bucket : pairs.values()) {
            for (int i = 0; i < bucket.size(); i++) out.put(bucket.get(i), i);
        }
        return out;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Visual node radius in world units (matches {@link NodeRenderer}). */
    private static double radiusOf(GraphNode n) {
        double base = 18.0 + n.importance * 16.0; // 18..34
        if (n.isAnchor) base += 4.0;
        return base;
    }

    private static int stableHash(GraphEdge e) {
        int h = e.a.paperId() * 31 + e.b.paperId();
        return h == Integer.MIN_VALUE ? 0 : Math.abs(h);
    }
}
