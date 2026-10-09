package com.citeright.ui.graph;

import java.util.*;

/**
 * Deterministic, anchor-organised cluster layout that <b>freezes</b> on
 * convergence.
 *
 * <p>Three stages:
 * <ol>
 *   <li><b>Cluster placement</b> — cluster centres are arranged on a
 *       size-weighted ring around the world origin. Larger clusters get more
 *       angular span and radial distance so their boundaries never overlap.</li>
 *   <li><b>Anchor rings</b> — within each cluster the anchor paper sits at the
 *       centre; the remaining nodes are placed on concentric rings ordered by
 *       descending importance (important papers hug the anchor).</li>
 *   <li><b>Force refinement</b> — a low-temperature (≤80 iter) pass of node
 *       repulsion + edge springs + gentle cluster-centroid cohesion, solely to
 *       resolve the small overlaps the ring placement can't.</li>
 * </ol>
 *
 * <p>After {@link #layout()} completes, node velocities are zeroed and the
 * positions are the frozen source of truth — no idle physics ever runs.
 */
public final class HierarchicalClusterLayout {

    // ── Tunables (dramatically increased for clean spacing) ────────────────
    private static final double BASE_RING_RADIUS   = 450.0;   // was 220 — clusters were piling up
    private static final double RING_STEP          = 120.0;    // was 95  — nodes within a cluster too tight
    private static final double NODE_SPACING        = 110.0;   // was 70  — minimum gap between nodes
    private static final double CLUSTER_PADDING     = 180.0;   // was 130 — padding around cluster bounds

    private static final double REFINE_REPULSION   = 18000.0;  // was 9000  — push harder
    private static final double REFINE_SPRING      = 0.010;    // was 0.02  — weaker springs (don't collapse layout)
    private static final double REFINE_COHESION    = 0.006;    // was 0.012 — gentler pull to centroid
    private static final double REFINE_DAMPING     = 0.78;     // was 0.82
    private static final int    REFINE_ITERATIONS  = 80;       // was 60
    private static final double REFINE_REST_LENGTH = 250.0;    // was 180 — edge springs have a longer rest length

    private final GraphModel model;

    public HierarchicalClusterLayout(GraphModel model) {
        this.model = model;
    }

    /** Run the full layout. Positions become frozen on return. */
    public void layout() {
        Map<String, List<GraphNode>> clusters = model.getClusterNodeMap();
        if (clusters.isEmpty()) return;

        // Reset cached centres from prior builds.
        centers.clear();

        placeClusters(clusters);
        placeAnchoredRings(clusters);
        refine(clusters);
        separationPass();      // additional hard-constraint pass
        freeze();
    }

    // ── Stage 1: cluster centre placement ─────────────────────────────────

    private void placeClusters(Map<String, List<GraphNode>> clusters) {
        int k = clusters.size();
        List<String> labels = new ArrayList<>(clusters.keySet());

        // Each cluster gets an angular slice proportional to sqrt(size).
        double[] slice = new double[k];
        double sliceSum = 0;
        for (int i = 0; i < k; i++) {
            slice[i] = Math.sqrt(Math.max(1, clusters.get(labels.get(i)).size()));
            sliceSum += slice[i];
        }

        // If only one cluster, keep it at the origin.
        if (k == 1) {
            centerOf(clusters.get(labels.get(0))).setLocation(0, 0);
            centerOf(clusters.get(labels.get(0))).radius = clusterRadius(clusters.get(labels.get(0)).size());
            return;
        }

        // Sort clusters by size descending so big ones go first on the ring.
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> clusters.get(labels.get(b)).size() - clusters.get(labels.get(a)).size());

        double angle = -Math.PI / 2; // start at top
        for (int idx = 0; idx < k; idx++) {
            int i = order[idx];
            double frac = slice[i] / sliceSum;
            double arc = frac * 2 * Math.PI;
            double midAngle = angle + arc / 2;

            List<GraphNode> members = clusters.get(labels.get(i));
            double memberRadius = clusterRadius(members.size());

            // The cluster centre distance from origin scales with the cluster's
            // own radius so large clusters push themselves further out.
            double radius = BASE_RING_RADIUS + memberRadius * 0.60;

            double cx = Math.cos(midAngle) * radius;
            double cy = Math.sin(midAngle) * radius;

            ClusterCenter cc = centerOf(members);
            cc.setLocation(cx, cy);
            cc.radius = memberRadius;

            angle += arc;
        }

        // Push apart any clusters whose bounding circles overlap.
        List<List<GraphNode>> memberLists = new ArrayList<>(clusters.values());
        for (int pass = 0; pass < 5; pass++) {
            for (int i = 0; i < memberLists.size(); i++) {
                ClusterCenter ci = centerOf(memberLists.get(i));
                for (int j = i + 1; j < memberLists.size(); j++) {
                    ClusterCenter cj = centerOf(memberLists.get(j));
                    double dx = cj.cx - ci.cx;
                    double dy = cj.cy - ci.cy;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double minDist = ci.radius + cj.radius + 80; // 80px gap
                    if (dist < minDist && dist > 0.01) {
                        double push = (minDist - dist) / 2.0;
                        double nx = dx / dist;
                        double ny = dy / dist;
                        ci.cx -= nx * push;
                        ci.cy -= ny * push;
                        cj.cx += nx * push;
                        cj.cy += ny * push;
                    }
                }
            }
        }
    }

    /** Rough world-space radius a cluster needs to fit its members. */
    private double clusterRadius(int memberCount) {
        if (memberCount <= 1) return NODE_SPACING;
        double r = NODE_SPACING;
        int cap = 6;   // was 8 — fewer per ring = more spread
        int n = memberCount - 1;
        while (n > 0) {
            r += RING_STEP;
            n -= cap;
            cap += 4;   // was 6 — slower ring expansion
        }
        return r + CLUSTER_PADDING;
    }

    // ── Stage 2: anchor rings ──────────────────────────────────────────────

    private void placeAnchoredRings(Map<String, List<GraphNode>> clusters) {
        for (List<GraphNode> members : clusters.values()) {
            if (members.isEmpty()) continue;

            // Sort by importance desc (anchor first).
            List<GraphNode> sorted = new ArrayList<>(members);
            sorted.sort((a, b) -> Double.compare(b.importance, a.importance));

            GraphNode anchor = sorted.get(0);
            ClusterCenter cc = centerOf(members);

            anchor.x = cc.cx;
            anchor.y = cc.cy;

            // Distribute the rest on concentric rings, importance-sorted.
            int perRing = 6;   // was 8 — fewer per ring = wider arc spacing
            int placed = 0;
            int ring = 1;
            for (int idx = 1; idx < sorted.size(); idx++) {
                if (placed >= perRing) {
                    placed = 0;
                    ring++;
                    perRing += 4;  // was 6
                }
                double ringRadius = ring * RING_STEP + NODE_SPACING * 0.5;
                double frac = (perRing == 0) ? 0 : (double) placed / perRing;
                double ang = frac * 2 * Math.PI + goldenOffset(idx);
                GraphNode n = sorted.get(idx);
                n.x = cc.cx + Math.cos(ang) * ringRadius;
                n.y = cc.cy + Math.sin(ang) * ringRadius;
                placed++;
            }
        }
    }

    /** Small deterministic golden-angle jitter so rings don't line up perfectly. */
    private static double goldenOffset(int idx) {
        return idx * 2.39996; // golden angle in radians
    }

    // ── Stage 3: force refinement (overlaps only) ─────────────────────────

    private void refine(Map<String, List<GraphNode>> clusters) {
        List<GraphNode> nodes = model.getNodes();
        List<GraphEdge> edges = model.getEdges();
        if (nodes.isEmpty()) return;

        // Precompute cluster centroids (fixed targets for cohesion).
        Map<GraphNode, double[]> cohesionTarget = new HashMap<>();
        for (List<GraphNode> members : clusters.values()) {
            ClusterCenter cc = centerOf(members);
            for (GraphNode n : members) cohesionTarget.put(n, new double[]{cc.cx, cc.cy});
        }

        for (int iter = 0; iter < REFINE_ITERATIONS; iter++) {
            double temp = 1.0 - (double) iter / REFINE_ITERATIONS;

            // Repulsion (all pairs) — push overlapping nodes apart hard.
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode a = nodes.get(i);
                for (int j = i + 1; j < nodes.size(); j++) {
                    GraphNode b = nodes.get(j);
                    double dx = b.x - a.x;
                    double dy = b.y - a.y;
                    double distSq = dx * dx + dy * dy;
                    if (distSq < 0.01) {
                        dx = (a.hashCode() & 7) - 3.5;
                        dy = (b.hashCode() & 7) - 3.5;
                        distSq = dx * dx + dy * dy + 0.01;
                    }
                    double dist = Math.sqrt(distSq);
                    double minDist = NODE_SPACING;
                    if (dist < minDist * 3.0) {
                        double force = REFINE_REPULSION / distSq * temp;
                        double fx = (dx / dist) * force;
                        double fy = (dy / dist) * force;
                        a.vx -= fx; a.vy -= fy;
                        b.vx += fx; b.vy += fy;
                    }
                }
            }

            // Edge springs — keep connected nodes reasonably close, but don't
            // collapse the layout.
            for (GraphEdge e : edges) {
                GraphNode a = e.a;
                GraphNode b = e.b;
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double dist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
                // Only pull if distance exceeds rest length (don't compress).
                if (dist > REFINE_REST_LENGTH) {
                    double k = REFINE_SPRING * (0.3 + e.weight * 0.5) * temp;
                    double force = (dist - REFINE_REST_LENGTH) * k;
                    double fx = (dx / dist) * force;
                    double fy = (dy / dist) * force;
                    a.vx += fx; a.vy += fy;
                    b.vx -= fx; b.vy -= fy;
                }
            }

            // Cluster cohesion (gentle pull toward cluster centroid).
            for (GraphNode n : nodes) {
                double[] target = cohesionTarget.get(n);
                if (target == null) continue;
                double dx = target[0] - n.x;
                double dy = target[1] - n.y;
                n.vx += dx * REFINE_COHESION * temp;
                n.vy += dy * REFINE_COHESION * temp;
            }

            // Integrate.
            for (GraphNode n : nodes) {
                n.vx *= REFINE_DAMPING;
                n.vy *= REFINE_DAMPING;
                n.x += n.vx;
                n.y += n.vy;
            }
        }
    }

    // ── Stage 3b: hard separation pass ────────────────────────────────────

    /**
     * After force refinement, push any remaining overlapping nodes apart
     * with a hard constraint. This guarantees no two nodes are closer than
     * NODE_SPACING * 0.8 regardless of what the forces settled on.
     */
    private void separationPass() {
        List<GraphNode> nodes = model.getNodes();
        double minDist = NODE_SPACING * 0.8;
        for (int pass = 0; pass < 5; pass++) {
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode a = nodes.get(i);
                for (int j = i + 1; j < nodes.size(); j++) {
                    GraphNode b = nodes.get(j);
                    double dx = b.x - a.x;
                    double dy = b.y - a.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < minDist && dist > 0.01) {
                        double push = (minDist - dist) / 2.0;
                        double nx = dx / dist;
                        double ny = dy / dist;
                        a.x -= nx * push;
                        a.y -= ny * push;
                        b.x += nx * push;
                        b.y += ny * push;
                    }
                }
            }
        }
    }

    // ── Freeze ─────────────────────────────────────────────────────────────

    private void freeze() {
        for (GraphNode n : model.getNodes()) {
            n.vx = 0;
            n.vy = 0;
        }
    }

    // ── Cluster-centre holder ──────────────────────────────────────────────

    private final Map<List<GraphNode>, ClusterCenter> centers = new IdentityHashMap<>();

    private ClusterCenter centerOf(List<GraphNode> members) {
        return centers.computeIfAbsent(members, k -> new ClusterCenter());
    }

    private static final class ClusterCenter {
        double cx, cy;
        double radius = NODE_SPACING;
        void setLocation(double x, double y) { this.cx = x; this.cy = y; }
    }

    // ── Cluster bounding boxes (for routing + cluster-avoidance) ───────────

    /**
     * Recompute the axis-aligned bounding rectangle (world space) of each
     * cluster, padded. Used by {@link EdgeRouter} for cluster-avoidance and by
     * {@link GraphRenderer} to draw the glowing cluster boundary.
     */
    public Map<String, double[]> clusterBounds() {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<GraphNode>> e : model.getClusterNodeMap().entrySet()) {
            List<GraphNode> members = e.getValue();
            if (members.isEmpty()) continue;
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (GraphNode n : members) {
                minX = Math.min(minX, n.x); minY = Math.min(minY, n.y);
                maxX = Math.max(maxX, n.x); maxY = Math.max(maxY, n.y);
            }
            double pad = CLUSTER_PADDING * 0.6; // visual padding (smaller than layout padding)
            out.put(e.getKey(), new double[]{
                    minX - pad, minY - pad, maxX + pad, maxY + pad});
        }
        return out;
    }
}
