package com.citeright.ui.graph;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.database.PaperEmbeddingDAO;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;

import java.util.*;

/**
 * Explains <em>why</em> two papers are connected.
 *
 * <p>The single biggest trust-builder in the graph: instead of just drawing a
 * line, we tell the researcher what the connection is made of. We are honest
 * about the data we actually have:
 * <ul>
 *   <li><b>Similarity %</b> — the edge weight (hybrid semantic + author + tag).</li>
 *   <li><b>Shared keywords</b> — top overlapping TF-IDF terms (from the model).</li>
 *   <li><b>Shared authors</b> — Jaccard overlap of author names (Yes / count).</li>
 *   <li><b>Shared context</b> — number of common graph neighbours. Labelled
 *       "shared citations" once a real CITES graph exists; for now it is a
 *       "shared neighbourhood" proxy.</li>
 *   <li><b>Embedding similarity</b> — BGE-M3 cosine if neural is available.</li>
 *   <li><b>Reason</b> — stored AI/user reasoning, else a one-line auto summary.</li>
 * </ul>
 */
public final class ConnectionExplainer {

    private final GraphModel model;
    private PaperEmbeddingDAO embeddingDAO;

    public ConnectionExplainer(GraphModel model) {
        this.model = model;
        try {
            this.embeddingDAO = new PaperEmbeddingDAO();
        } catch (Exception ignored) {
            this.embeddingDAO = null;
        }
    }

    /** Structured explanation of an edge. */
    public static final class Explanation {
        public final GraphEdge edge;
        public final int similarityPct;
        public final List<String> sharedKeywords;
        public final boolean shareAuthors;
        public final int sharedContextCount;
        public final int embeddingPct;       // -1 if unavailable
        public final String reason;

        Explanation(GraphEdge edge, int similarityPct, List<String> sharedKeywords,
                    boolean shareAuthors, int sharedContextCount, int embeddingPct,
                    String reason) {
            this.edge = edge;
            this.similarityPct = similarityPct;
            this.sharedKeywords = sharedKeywords;
            this.shareAuthors = shareAuthors;
            this.sharedContextCount = sharedContextCount;
            this.embeddingPct = embeddingPct;
            this.reason = reason;
        }

        public boolean hasEmbedding() { return embeddingPct >= 0; }

        /** One-line headline used for inline edge labels. */
        public String headline() {
            StringBuilder sb = new StringBuilder();
            sb.append(edge.type.getLabel());
            sb.append(" · ").append(similarityPct).append("%");
            if (edge.isAISuggestion) sb.append(" · AI");
            return sb.toString();
        }
    }

    public Explanation explain(GraphEdge edge) {
        GraphNode a = edge.a;
        GraphNode b = edge.b;

        int simPct = (int) Math.round(edge.weight * 100);

        List<String> keywords = edge.sharedTerms;
        if (keywords == null || keywords.isEmpty()) {
            keywords = model.sharedTerms(a, b, 3);
        }
        if (keywords == null) keywords = List.of();

        boolean shareAuthors = shareAuthors(a, b);

        int sharedCtx = sharedNeighbours(a, b);

        int embPct = embeddingSimilarityPct(a, b);

        String reason = edge.reasoning;
        if (reason == null || reason.isBlank()) {
            reason = autoReason(edge, keywords, shareAuthors, simPct);
        }

        return new Explanation(edge, simPct, keywords, shareAuthors,
                sharedCtx, embPct, reason);
    }

    // ── Signal computations ────────────────────────────────────────────────

    private boolean shareAuthors(GraphNode a, GraphNode b) {
        Set<String> na = authorNames(a);
        Set<String> nb = authorNames(b);
        for (String s : na) if (nb.contains(s)) return true;
        return false;
    }

    private int sharedNeighbours(GraphNode a, GraphNode b) {
        Set<GraphNode> na = new HashSet<>();
        for (GraphEdge e : model.getEdges()) {
            if (e.type == RelationshipType.RELATED) continue; // ignore weak sims
            if (e.a == a && e.b != b) na.add(e.b);
            if (e.b == a && e.a != b) na.add(e.a);
        }
        int count = 0;
        for (GraphNode n : na) {
            for (GraphEdge e : model.getEdges()) {
                if (e.type == RelationshipType.RELATED) continue;
                if ((e.a == b && e.b == n) || (e.b == b && e.a == n)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private int embeddingSimilarityPct(GraphNode a, GraphNode b) {
        if (!NeuralAvailability.isReady() || embeddingDAO == null) return -1;
        try {
            Map<Integer, float[]> cache = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
            float[] va = cache.get(a.paperId());
            float[] vb = cache.get(b.paperId());
            if (va == null || vb == null) return -1;
            double cos = BgeM3EmbeddingEngine.cosineSimilarity(va, vb);
            return (int) Math.round(cos * 100);
        } catch (Exception e) {
            return -1;
        }
    }

    private String autoReason(GraphEdge edge, List<String> keywords,
                              boolean shareAuthors, int simPct) {
        StringBuilder sb = new StringBuilder();
        switch (edge.type) {
            case SUPPORTS:     sb.append("Supports"); break;
            case EXTENDS:      sb.append("Extends"); break;
            case METHODOLOGY:  sb.append("Shares methodology with"); break;
            case COMPARES:     sb.append("Compares with"); break;
            case CONTRADICTS:  sb.append("Contradicts"); break;
            case CITES:        sb.append("Cites"); break;
            case RELATED:
            default:           sb.append("Semantically related to"); break;
        }
        sb.append(" \"").append(truncate(edge.b.title, 40)).append("\".");
        List<String> parts = new ArrayList<>();
        if (!keywords.isEmpty()) parts.add("shared: " + String.join(", ", keywords));
        if (shareAuthors) parts.add("shared authors");
        if (simPct >= 70) parts.add("strong overlap");
        if (!parts.isEmpty()) {
            sb.append(" (").append(String.join("; ", parts)).append(")");
        }
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static Set<String> authorNames(GraphNode n) {
        Set<String> names = new HashSet<>();
        Publication p = n.entry != null ? n.entry.getPublication() : null;
        if (p != null && p.getAuthors() != null) {
            for (var a : p.getAuthors()) {
                if (a.getName() != null) names.add(a.getName().toLowerCase());
            }
        }
        return names;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @SuppressWarnings("unused")
    private static double authorJaccard(LibraryEntry a, LibraryEntry b) {
        Set<String> sa = new HashSet<>();
        Set<String> sb = new HashSet<>();
        if (a.getPublication() != null && a.getPublication().getAuthors() != null) {
            for (var au : a.getPublication().getAuthors())
                if (au.getName() != null) sa.add(au.getName().toLowerCase());
        }
        if (b.getPublication() != null && b.getPublication().getAuthors() != null) {
            for (var au : b.getPublication().getAuthors())
                if (au.getName() != null) sb.add(au.getName().toLowerCase());
        }
        Set<String> inter = new HashSet<>(sa); inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa); union.addAll(sb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }
}
