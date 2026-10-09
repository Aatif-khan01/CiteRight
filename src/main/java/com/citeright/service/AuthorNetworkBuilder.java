package com.citeright.service;

import com.citeright.model.Author;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a co-authorship network from the user's library.
 *
 * Nodes are authors (sized by paper count), edges are co-authorship ties.
 * Community detection via label propagation colors author clusters,
 * revealing rival groups and potential collaborators.
 *
 * Entirely in-memory — no new database tables required.
 */
public class AuthorNetworkBuilder {

    /** A node in the author network */
    public static class AuthorNode {
        public final String name;
        public final Set<Integer> paperIds = new HashSet<>(); // library entry IDs
        public int community = -1;  // Assigned by label propagation
        public double x, y;         // Layout position

        public AuthorNode(String name) {
            this.name = name;
        }

        public int getPaperCount() { return paperIds.size(); }
    }

    /** An edge in the author network */
    public static class CoAuthorEdge {
        public final String authorA;
        public final String authorB;
        public final int sharedPaperCount;

        public CoAuthorEdge(String a, String b, int count) {
            this.authorA = a;
            this.authorB = b;
            this.sharedPaperCount = count;
        }
    }

    /** Full result of network construction */
    public static class AuthorNetwork {
        private final Map<String, AuthorNode> nodes;
        private final List<CoAuthorEdge> edges;
        private final int communityCount;

        public AuthorNetwork(Map<String, AuthorNode> nodes, List<CoAuthorEdge> edges, int communityCount) {
            this.nodes = nodes;
            this.edges = edges;
            this.communityCount = communityCount;
        }

        public Map<String, AuthorNode> getNodes() { return nodes; }
        public List<CoAuthorEdge> getEdges() { return edges; }
        public int getCommunityCount() { return communityCount; }

        /** Authors appearing in the most papers — potential key collaborators */
        public List<AuthorNode> getTopAuthors(int limit) {
            return nodes.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getPaperCount(), a.getPaperCount()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        /** Authors who bridge two otherwise-separate communities */
        public List<AuthorNode> getBridgeAuthors() {
            // An author is a "bridge" if their co-authors span multiple communities
            List<AuthorNode> bridges = new ArrayList<>();
            Map<String, Set<Integer>> authorCommunities = new HashMap<>();

            for (CoAuthorEdge edge : edges) {
                AuthorNode a = nodes.get(edge.authorA);
                AuthorNode b = nodes.get(edge.authorB);
                if (a != null && b != null) {
                    authorCommunities.computeIfAbsent(edge.authorA, k -> new HashSet<>()).add(b.community);
                    authorCommunities.computeIfAbsent(edge.authorB, k -> new HashSet<>()).add(a.community);
                }
            }

            for (Map.Entry<String, Set<Integer>> entry : authorCommunities.entrySet()) {
                if (entry.getValue().size() >= 2) {
                    AuthorNode node = nodes.get(entry.getKey());
                    if (node != null) bridges.add(node);
                }
            }
            return bridges;
        }
    }

    /**
     * Builds the complete co-authorship network from the library.
     */
    public AuthorNetwork build(List<LibraryEntry> entries) {
        // Step 1: Collect all authors and their paper associations
        Map<String, AuthorNode> authorNodes = new LinkedHashMap<>();

        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            if (pub == null || pub.getAuthors() == null) continue;

            for (Author author : pub.getAuthors()) {
                String name = normalizeAuthorName(author.getName());
                if (name.isEmpty()) continue;
                authorNodes.computeIfAbsent(name, AuthorNode::new).paperIds.add(entry.getId());
            }
        }

        // Step 2: Build co-authorship edges
        // Two authors are connected if they co-authored at least one paper
        Map<String, Integer> edgeCounts = new HashMap<>(); // "A|B" → count
        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            if (pub == null || pub.getAuthors() == null) continue;

            List<String> names = pub.getAuthors().stream()
                    .map(a -> normalizeAuthorName(a.getName()))
                    .filter(n -> !n.isEmpty())
                    .collect(Collectors.toList());

            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String a = names.get(i);
                    String b = names.get(j);
                    String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
                    edgeCounts.merge(key, 1, Integer::sum);
                }
            }
        }

        List<CoAuthorEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : edgeCounts.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            edges.add(new CoAuthorEdge(parts[0], parts[1], entry.getValue()));
        }

        // Step 3: Community detection via label propagation
        int communityCount = labelPropagation(authorNodes, edges);

        // Step 4: Simple force-directed layout for author nodes
        layoutAuthors(authorNodes, edges);

        System.out.println("[AuthorNetwork] Built network: " + authorNodes.size() + " authors, "
                + edges.size() + " co-author edges, " + communityCount + " communities");

        return new AuthorNetwork(authorNodes, edges, communityCount);
    }

    // ── Label Propagation Community Detection ────────────────────────────────

    private int labelPropagation(Map<String, AuthorNode> nodes, List<CoAuthorEdge> edges) {
        // Initialize: each author in its own community
        List<String> names = new ArrayList<>(nodes.keySet());
        Map<String, Integer> labels = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            labels.put(names.get(i), i);
        }

        // Build adjacency with weights
        Map<String, Map<String, Integer>> adjacency = new HashMap<>();
        for (CoAuthorEdge edge : edges) {
            adjacency.computeIfAbsent(edge.authorA, k -> new HashMap<>()).put(edge.authorB, edge.sharedPaperCount);
            adjacency.computeIfAbsent(edge.authorB, k -> new HashMap<>()).put(edge.authorA, edge.sharedPaperCount);
        }

        // Iterate until convergence or max iterations
        Random rng = new Random(42);
        for (int iter = 0; iter < 50; iter++) {
            boolean changed = false;
            List<String> shuffled = new ArrayList<>(names);
            Collections.shuffle(shuffled, rng);

            for (String name : shuffled) {
                Map<String, Integer> neighbors = adjacency.getOrDefault(name, Map.of());
                if (neighbors.isEmpty()) continue;

                // Count weighted votes for each neighbor's label
                Map<Integer, Integer> votes = new HashMap<>();
                for (Map.Entry<String, Integer> neighbor : neighbors.entrySet()) {
                    int neighborLabel = labels.get(neighbor.getKey());
                    votes.merge(neighborLabel, neighbor.getValue(), Integer::sum);
                }

                // Pick label with most votes
                int bestLabel = votes.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(labels.get(name));

                if (bestLabel != labels.get(name)) {
                    labels.put(name, bestLabel);
                    changed = true;
                }
            }

            if (!changed) break;
        }

        // Assign to nodes and count communities
        Set<Integer> communities = new HashSet<>();
        for (Map.Entry<String, Integer> entry : labels.entrySet()) {
            AuthorNode node = nodes.get(entry.getKey());
            if (node != null) {
                node.community = entry.getValue();
                communities.add(entry.getValue());
            }
        }

        return communities.size();
    }

    // ── Simple layout for author nodes ───────────────────────────────────────

    private void layoutAuthors(Map<String, AuthorNode> nodes, List<CoAuthorEdge> edges) {
        Random rng = new Random(42);
        for (AuthorNode node : nodes.values()) {
            node.x = rng.nextDouble() * 600 - 300;
            node.y = rng.nextDouble() * 600 - 300;
        }

        // Quick force-directed layout (80 iterations)
        for (int iter = 0; iter < 80; iter++) {
            List<AuthorNode> nodeList = new ArrayList<>(nodes.values());

            // Repulsion
            for (int i = 0; i < nodeList.size(); i++) {
                for (int j = i + 1; j < nodeList.size(); j++) {
                    AuthorNode a = nodeList.get(i), b = nodeList.get(j);
                    double dx = b.x - a.x, dy = b.y - a.y;
                    double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                    double force = 5000.0 / (dist * dist);
                    double fx = (dx / dist) * force, fy = (dy / dist) * force;
                    a.x -= fx; a.y -= fy;
                    b.x += fx; b.y += fy;
                }
            }

            // Attraction along edges
            for (CoAuthorEdge edge : edges) {
                AuthorNode a = nodes.get(edge.authorA), b = nodes.get(edge.authorB);
                if (a == null || b == null) continue;
                double dx = b.x - a.x, dy = b.y - a.y;
                double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                double force = 0.005 * dist * edge.sharedPaperCount;
                double fx = (dx / dist) * force, fy = (dy / dist) * force;
                a.x += fx; a.y += fy;
                b.x -= fx; b.y -= fy;
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private String normalizeAuthorName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ");
    }
}
