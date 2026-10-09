package com.citeright.ui.graph;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.database.PaperEmbeddingDAO;
import com.citeright.database.PaperRelationshipDAO;
import com.citeright.model.LibraryEntry;
import com.citeright.model.PaperRelationship;
import com.citeright.model.Publication;
import com.citeright.nlp.TfIdfEngine;
import com.citeright.service.ClusteringEngine;
import com.citeright.service.ClusteringEngine.ClusterResult;
import com.citeright.service.LibraryService;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The assembled, immutable-for-a-build graph state.
 *
 * <p>{@link #rebuild()} runs the full Macro pipeline against the user's
 * library and produces nodes, edges, clusters, anchors, importance scores,
 * bundle groups and per-cluster keyword sets. It deliberately does <em>not</em>
 * position the nodes — that is the layout layer's job ({@link
 * HierarchicalClusterLayout}). This separation lets filters re-run cheaply by
 * toggling visibility without redoing clustering.
 *
 * <p>The similarity logic is ported verbatim from the legacy graph's proven
 * hybrid: TF-IDF cosine OR BGE-M3 neural (remapped [0.65,1.0]→[0,1]), then
 * blended 0.75·semantic + 0.15·authorJaccard + 0.10·tagJaccard.
 */
public final class GraphModel {

    private final LibraryService libraryService;
    private final ClusteringEngine clusteringEngine = new ClusteringEngine();
    private final PaperRelationshipDAO relationshipDAO = new PaperRelationshipDAO();

    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Map<String, List<GraphNode>> clusterNodeMap = new LinkedHashMap<>();
    private final Map<String, List<String>> clusterKeywords = new LinkedHashMap<>();
    private final Map<String, Color> clusterColors = new LinkedHashMap<>();

    // Filter state — mutating these and calling rebuild() re-applies them.
    private double similarityThreshold = 0.08;
    private boolean showSimilarity = true;
    private boolean showAI = false;
    private boolean showCurated = true;
    private final Set<RelationshipType> typeFilter = EnumSet.noneOf(RelationshipType.class);

    // Reused across builds to compute shared-keyword explanations.
    private final Map<Integer, Map<String, Double>> tfidfVectors = new HashMap<>();
    private final TfIdfEngine tfidf = new TfIdfEngine();

    public GraphModel(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public List<GraphNode> getNodes() { return nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public Map<String, List<GraphNode>> getClusterNodeMap() { return clusterNodeMap; }
    public Map<String, List<String>> getClusterKeywords() { return clusterKeywords; }
    public Map<String, Color> getClusterColors() { return clusterColors; }

    public GraphNode nodeFor(int paperId) {
        for (GraphNode n : nodes) if (n.paperId() == paperId) return n;
        return null;
    }

    /** Edges touching the given node (either endpoint), unfiltered copy. */
    public List<GraphEdge> edgesFor(GraphNode node) {
        List<GraphEdge> out = new ArrayList<>();
        for (GraphEdge e : edges) if (e.touches(node)) out.add(e);
        return out;
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }
    public int clusterCount() { return clusterNodeMap.size(); }

    // ── Filters ────────────────────────────────────────────────────────────

    public void setSimilarityThreshold(double v) { this.similarityThreshold = v; }
    public void setShowSimilarity(boolean v) { this.showSimilarity = v; }
    public void setShowAI(boolean v) { this.showAI = v; }
    public void setShowCurated(boolean v) { this.showCurated = v; }

    /** Empty set means "all types allowed". */
    public void setTypeFilter(Set<RelationshipType> types) {
        this.typeFilter.clear();
        this.typeFilter.addAll(types);
    }

    // ── Build ──────────────────────────────────────────────────────────────

    /**
     * Rebuild the whole model from the library. Safe to call off the FX thread.
     */
    public void rebuild() {
        nodes.clear();
        edges.clear();
        clusterNodeMap.clear();
        clusterKeywords.clear();
        clusterColors.clear();
        tfidfVectors.clear();

        List<LibraryEntry> entries = libraryService.getAllLibraryPapers();
        if (entries == null || entries.isEmpty()) return;

        // Keep only entries with a publication.
        List<LibraryEntry> valid = entries.stream()
                .filter(e -> e.getPublication() != null)
                .collect(Collectors.toList());
        if (valid.isEmpty()) return;

        // 1) Cluster.
        List<Publication> pubs = valid.stream()
                .map(LibraryEntry::getPublication)
                .collect(Collectors.toList());
        ClusterResult clusterResult = clusteringEngine.clusterWithDetails(pubs);
        Map<String, List<Publication>> clusters = clusterResult.getClusters();

        // 2) Build TF-IDF model once (used for similarity + shared keywords + labels).
        List<String> docs = new ArrayList<>();
        Map<Integer, String> docForId = new HashMap<>();
        for (LibraryEntry e : valid) {
            String doc = buildDocText(e);
            docs.add(doc);
            docForId.put(e.getId(), doc);
        }
        if (!docs.isEmpty()) tfidf.buildModel(docs);

        // 3) Create nodes per cluster.
        int colorIdx = 0;
        for (Map.Entry<String, List<Publication>> cluster : clusters.entrySet()) {
            String label = cluster.getKey();
            Color color = Theme.clusterColor(colorIdx++);
            clusterColors.put(label, color);

            List<GraphNode> clusterNodes = new ArrayList<>();
            for (Publication pub : cluster.getValue()) {
                LibraryEntry matchEntry = findEntry(valid, pub);
                if (matchEntry == null) continue;
                String title = pub.getTitle() != null ? pub.getTitle() : "Untitled";
                GraphNode node = new GraphNode(matchEntry, title, pub.getYear(), 0, 0);
                node.clusterLabel = label;
                node.clusterColor = color;
                // Stash the sparse TF-IDF vector for shared-keyword explanations.
                String doc = docForId.get(matchEntry.getId());
                if (doc != null) {
                    Map<String, Double> vec = tfidf.computeTfIdfVector(doc);
                    node.termVector = vec;
                    tfidfVectors.put(matchEntry.getId(), vec);
                }
                nodes.add(node);
                clusterNodes.add(node);
            }
            clusterNodeMap.put(label, clusterNodes);
        }

        // 4) Compute PageRank (needs edges first — we compute similarity edges now).
        addSimilarityEdges(valid);
        addRelationshipEdges(valid);

        // 5) Importance + anchors (needs PageRank + edges).
        Map<Integer, Double> pageRank = PageRank.compute(nodes, edges);
        computeImportance(valid, pageRank);
        assignAnchors();

        // 6) Bundle groups (for edge routing).
        assignBundleGroups();

        // 7) Per-cluster top keywords (TF-IDF centroid top terms).
        computeClusterKeywords(clusterResult);
    }

    // ── Similarity edges (hybrid TF-IDF / BGE-M3) ──────────────────────────

    private void addSimilarityEdges(List<LibraryEntry> valid) {
        if (!showSimilarity || valid.size() < 2) return;

        boolean useBge = NeuralAvailability.isReady();
        Map<Integer, float[]> cached = null;
        BgeM3EmbeddingEngine neuralEngine = null;
        PaperEmbeddingDAO embeddingDAO = null;
        if (useBge) {
            neuralEngine = BgeM3EmbeddingEngine.getInstance();
            embeddingDAO = new PaperEmbeddingDAO();
            cached = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
            if (cached == null) cached = new HashMap<>();
        }

        for (int i = 0; i < valid.size(); i++) {
            GraphNode a = nodeFor(valid.get(i).getId());
            if (a == null) continue;
            for (int j = i + 1; j < valid.size(); j++) {
                GraphNode b = nodeFor(valid.get(j).getId());
                if (b == null) continue;

                double sim;
                if (useBge) {
                    float[] va = getEmbedding(valid.get(i), cached, neuralEngine, embeddingDAO);
                    float[] vb = getEmbedding(valid.get(j), cached, neuralEngine, embeddingDAO);
                    if (va != null && vb != null) {
                        double neural = BgeM3EmbeddingEngine.cosineSimilarity(va, vb);
                        sim = normalizeNeural(neural);
                    } else {
                        sim = tfidfSim(valid.get(i), valid.get(j));
                    }
                } else {
                    sim = tfidfSim(valid.get(i), valid.get(j));
                }
                sim = hybrid(sim, valid.get(i), valid.get(j));

                if (sim >= similarityThreshold) {
                    GraphEdge edge = new GraphEdge(a, b, sim, RelationshipType.RELATED);
                    edge.weight = sim;
                    edge.confidence = sim;
                    edges.add(edge);
                }
            }
        }
    }
    private double normalizeNeural(double rawSim) {
        if (rawSim <= 0.40) return 0.0;
        double normalized = (rawSim - 0.40) / 0.60;
        return Math.pow(normalized, 2.0);
    }

    private double tfidfSim(LibraryEntry a, LibraryEntry b) {
        Map<String, Double> va = tfidfVectors.get(a.getId());
        Map<String, Double> vb = tfidfVectors.get(b.getId());
        if (va == null || vb == null) return 0.0;
        return TfIdfEngine.cosineSimilarity(va, vb);
    }

    private float[] getEmbedding(LibraryEntry entry, Map<Integer, float[]> cached,
                                 BgeM3EmbeddingEngine engine, PaperEmbeddingDAO dao) {
        float[] v = cached.get(entry.getId());
        if (v != null) return v;
        if (engine == null) return null;
        v = engine.getEmbedding(buildDocText(entry));
        if (v != null) {
            dao.saveEmbedding(entry.getId(), "bge-m3", "v1", v);
            cached.put(entry.getId(), v);
        }
        return v;
    }

    /** 0.75·semantic + 0.15·authorJaccard + 0.10·tagJaccard. */
    private double hybrid(double semantic, LibraryEntry a, LibraryEntry b) {
        return 0.75 * semantic + 0.15 * authorJaccard(a, b) + 0.10 * tagJaccard(a, b);
    }

    private static double authorJaccard(LibraryEntry a, LibraryEntry b) {
        Set<String> setA = authorNames(a);
        Set<String> setB = authorNames(b);
        return jaccard(setA, setB);
    }

    private static double tagJaccard(LibraryEntry a, LibraryEntry b) {
        Set<String> setA = new HashSet<>();
        Set<String> setB = new HashSet<>();
        if (a.getTags() != null) a.getTags().forEach(t -> setA.add(t.getName().toLowerCase()));
        if (b.getTags() != null) b.getTags().forEach(t -> setB.add(t.getName().toLowerCase()));
        return jaccard(setA, setB);
    }

    private static Set<String> authorNames(LibraryEntry e) {
        Set<String> names = new HashSet<>();
        Publication p = e.getPublication();
        if (p != null && p.getAuthors() != null) {
            for (var a : p.getAuthors()) {
                if (a.getName() != null) names.add(a.getName().toLowerCase());
            }
        }
        return names;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    // ── Curated / AI relationship edges ────────────────────────────────────

    private void addRelationshipEdges(List<LibraryEntry> valid) {
        // Gather all relationships touching any visible node (dedup by id).
        Map<Integer, PaperRelationship> byId = new LinkedHashMap<>();
        for (LibraryEntry entry : valid) {
            List<PaperRelationship> rels = relationshipDAO.getByPaperId(entry.getId());
            if (rels == null) continue;
            for (PaperRelationship r : rels) {
                if (!r.isDismissed()) byId.put(r.getId(), r);
            }
        }
        for (PaperRelationship rel : byId.values()) {
            GraphNode src = nodeFor(rel.getSourcePaperId());
            GraphNode tgt = nodeFor(rel.getTargetPaperId());
            if (src == null || tgt == null) continue;

            boolean isAI = rel.getSource() == PaperRelationship.Source.AI_SUGGESTED;
            if (isAI && !showAI) continue;
            if (!isAI && !showCurated) continue;

            RelationshipType type = RelationshipType.fromString(rel.getRelationshipType());
            if (!typeFilter.isEmpty() && !typeFilter.contains(type) && type != RelationshipType.RELATED) {
                // Type filter active and this type is not selected → skip.
                // (RELATED edges are governed by the similarity toggle, not type filter.)
                continue;
            }

            // Replace any RELATED edge between the same pair so the curated
            // relationship takes visual precedence.
            removeRelatedEdgeBetween(src, tgt);

            GraphEdge edge = new GraphEdge(src, tgt, Math.max(0.1, rel.getConfidence()), type);
            edge.weight = Math.max(0.1, rel.getConfidence());
            edge.confidence = rel.getConfidence();
            edge.isAISuggestion = isAI;
            edge.relationshipId = rel.getId();
            edge.reasoning = rel.getReasoning();
            edge.sharedTerms = sharedTerms(src, tgt, 3);
            edges.add(edge);
        }
    }

    private void removeRelatedEdgeBetween(GraphNode a, GraphNode b) {
        edges.removeIf(e -> e.type == RelationshipType.RELATED && e.touches(a) && e.other(a) == b);
    }

    /** Top overlapping high-weight TF-IDF terms between two nodes. */
    public List<String> sharedTerms(GraphNode a, GraphNode b, int limit) {
        Map<String, Double> va = a.termVector;
        Map<String, Double> vb = b.termVector;
        if (va == null || vb == null) return List.of();
        List<Map.Entry<String, Double>> shared = new ArrayList<>();
        for (Map.Entry<String, Double> ea : va.entrySet()) {
            Double wb = vb.get(ea.getKey());
            if (wb != null) {
                shared.add(new AbstractMap.SimpleEntry<>(ea.getKey(), Math.min(ea.getValue(), wb)));
            }
        }
        shared.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, shared.size()); i++) {
            out.add(capitalize(shared.get(i).getKey()));
        }
        return out;
    }

    // ── Importance + anchors ───────────────────────────────────────────────

    /**
     * importance = 0.35·citations + 0.25·pageRank + 0.20·semantic
     *            + 0.10·userActivity + 0.10·relConfidence   (all normalised 0..1)
     */
    private void computeImportance(List<LibraryEntry> valid, Map<Integer, Double> pageRank) {
        // Normalisation bases.
        double maxCitations = 1.0;
        for (GraphNode n : nodes) {
            int c = n.entry.getPublication().getCitationCount();
            if (c > maxCitations) maxCitations = c;
        }
        double maxSemantic = 0.0;
        Map<Integer, Double> semanticSum = new HashMap<>();
        for (GraphEdge e : edges) {
            if (e.type != RelationshipType.RELATED) continue;
            semanticSum.merge(e.a.paperId(), e.weight, Double::sum);
            semanticSum.merge(e.b.paperId(), e.weight, Double::sum);
        }
        for (double v : semanticSum.values()) maxSemantic = Math.max(maxSemantic, v);

        LocalDateTime now = LocalDateTime.now();
        for (GraphNode n : nodes) {
            Publication p = n.entry.getPublication();
            double normCit = p.getCitationCount() / maxCitations;
            double pr = pageRank.getOrDefault(n.paperId(), 0.0);
            double sem = maxSemantic > 0 ? semanticSum.getOrDefault(n.paperId(), 0.0) / maxSemantic : 0.0;
            double activity = userActivity(n.entry, now);
            double relConf = meanIncidentConfidence(n);

            n.importance = clamp01(0.35 * normCit + 0.25 * pr + 0.20 * sem + 0.10 * activity + 0.10 * relConf);
        }
    }

    /** 0..1 aggregate of favorite, read status, notes presence, recency. */
    private static double userActivity(LibraryEntry e, LocalDateTime now) {
        double score = 0.0;
        if (e.isFavorite()) score += 0.3;
        LibraryEntry.ReadStatus rs = e.getReadStatus();
        if (rs != null) {
            switch (rs) {
                case READ:    score += 0.4; break;
                case READING: score += 0.2; break;
                default:      break;
            }
        }
        if (e.getNotes() != null && !e.getNotes().isBlank()) score += 0.2;
        if (e.getAddedAt() != null) {
            long days = ChronoUnit.DAYS.between(e.getAddedAt(), now);
            // Added in last 90 days → up to +0.1, decaying.
            if (days >= 0 && days <= 90) score += 0.1 * (1.0 - days / 90.0);
        }
        return clamp01(score);
    }

    private double meanIncidentConfidence(GraphNode node) {
        double sum = 0;
        int count = 0;
        for (GraphEdge e : edges) {
            if (e.touches(node)) {
                sum += e.confidence;
                count++;
            }
        }
        return count == 0 ? 0.0 : clamp01(sum / count);
    }

    private void assignAnchors() {
        for (List<GraphNode> members : clusterNodeMap.values()) {
            GraphNode anchor = null;
            for (GraphNode n : members) {
                if (anchor == null || n.importance > anchor.importance) anchor = n;
            }
            if (anchor != null) anchor.isAnchor = true;
        }
    }

    // ── Bundling ───────────────────────────────────────────────────────────

    /**
     * Group edges that share an endpoint and travel in a similar direction so
     * the {@link EdgeRouter} can merge them mid-flight. Group key = endpoint id
     * + 8-way direction bucket.
     */
    private void assignBundleGroups() {
        Map<String, List<GraphEdge>> buckets = new HashMap<>();
        for (GraphEdge e : edges) {
            // Buckets are keyed by the lower-id endpoint + a direction octant.
            // Direction is unknown until layout runs; we approximate with a
            // cluster-pair key which is stable pre-layout.
            int idA = e.a.paperId();
            int idB = e.b.paperId();
            String pair = idA < idB ? idA + "-" + idB : idB + "-" + idA;
            String key = pair + "|" + (e.a.clusterLabel != null ? e.a.clusterLabel : "?")
                    + "->" + (e.b.clusterLabel != null ? e.b.clusterLabel : "?");
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (List<GraphEdge> bucket : buckets.values()) {
            String gid = "bundle-" + System.identityHashCode(bucket);
            for (GraphEdge e : bucket) e.bundleGroup = gid;
        }
    }

    // ── Cluster keywords (for smart labels) ────────────────────────────────

    private void computeClusterKeywords(ClusterResult result) {
        Map<String, double[]> centroids = result.getCentroids();
        List<String> vocab = result.getVocabulary();
        boolean tfidfCentroids = centroids != null && !centroids.isEmpty()
                && vocab != null && !vocab.isEmpty();

        for (String label : clusterNodeMap.keySet()) {
            List<String> keywords = new ArrayList<>();
            if (tfidfCentroids && centroids.containsKey(label)) {
                double[] centroid = centroids.get(label);
                List<Map.Entry<String, Double>> terms = new ArrayList<>();
                for (int i = 0; i < Math.min(vocab.size(), centroid.length); i++) {
                    if (centroid[i] > 0) {
                        terms.add(new AbstractMap.SimpleEntry<>(vocab.get(i), centroid[i]));
                    }
                }
                terms.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
                for (int i = 0; i < Math.min(3, terms.size()); i++) {
                    keywords.add(capitalize(terms.get(i).getKey()));
                }
            } else {
                // Neural mode or no centroids: derive from member titles.
                List<GraphNode> members = clusterNodeMap.get(label);
                Map<String, Integer> freq = new HashMap<>();
                for (GraphNode n : members) {
                    if (n.title == null) continue;
                    for (String w : n.title.toLowerCase().split("\\W+")) {
                        if (w.length() <= 3 || isStopWord(w)) continue;
                        freq.merge(w, 1, Integer::sum);
                    }
                }
                freq.entrySet().stream()
                        .sorted((x, y) -> Integer.compare(y.getValue(), x.getValue()))
                        .limit(3)
                        .forEach(en -> keywords.add(capitalize(en.getKey())));
            }
            clusterKeywords.put(label, keywords);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String buildDocText(LibraryEntry entry) {
        Publication pub = entry.getPublication();
        if (pub == null) return "";
        StringBuilder sb = new StringBuilder();
        
        if (pub.getTitle() != null && !pub.getTitle().isEmpty()) {
            // Emphasize title
            sb.append("Title: ").append(pub.getTitle()).append(". ").append(pub.getTitle()).append("\n");
        }
        
        Set<String> authors = authorNames(entry);
        if (!authors.isEmpty()) {
            sb.append("Authors: ").append(String.join(", ", authors)).append("\n");
        }
        
        if (entry.getTags() != null && !entry.getTags().isEmpty()) {
            List<String> tags = new ArrayList<>();
            entry.getTags().forEach(t -> tags.add(t.getName()));
            sb.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }

        if (pub.getAbstractText() != null && !pub.getAbstractText().isEmpty()) {
            sb.append("Abstract: ").append(pub.getAbstractText());
        }
        return sb.toString();
    }

    private static LibraryEntry findEntry(List<LibraryEntry> entries, Publication pub) {
        for (LibraryEntry e : entries) if (e.getPublication() == pub) return e;
        return null;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "using",
            "based", "their", "are", "via", "towards", "through", "between",
            "within", "over", "under", "when", "where", "which", "while", "will",
            "can", "may", "such", "than", "then", "also", "these", "those");

    private static boolean isStopWord(String w) { return STOP.contains(w); }
}
