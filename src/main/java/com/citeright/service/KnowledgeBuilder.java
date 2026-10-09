package com.citeright.service;

import com.citeright.database.*;
import com.citeright.model.*;
import com.citeright.nlp.EntityExtractor;
import com.citeright.nlp.EntityExtractor.EntityType;
import com.citeright.nlp.VenueQualityProvider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a KnowledgeContext from the user's library in a single pass.
 *
 * Pipeline:
 *   1. Load all LibraryEntries from LibraryDAO
 *   2. Load all cached embeddings from PaperEmbeddingDAO
 *   3. Run entity extraction (regex + keyword dictionaries) on title + abstract
 *   4. Compute venue quality scores via VenueQualityProvider
 *   5. Run ClusteringEngine for topic clusters + centroids
 *   6. Build citation graph adjacency from PaperRelationshipDAO
 *   7. Aggregate entities per cluster → clusterEntities
 *   8. Compute year distributions per cluster → clusterYearDistribution
 *   9. Cache extracted entities in paper_entities SQLite table
 *   10. Return fully populated KnowledgeContext
 */
public class KnowledgeBuilder {

    private final LibraryDAO libraryDAO;
    private final PaperEmbeddingDAO embeddingDAO;
    private final PaperRelationshipDAO relationshipDAO;
    private final EntityDAO entityDAO;
    private final ClusteringEngine clusteringEngine;
    private final EntityExtractor entityExtractor;
    private final VenueQualityProvider venueQualityProvider;

    public KnowledgeBuilder() {
        this.libraryDAO = new LibraryDAO();
        this.embeddingDAO = new PaperEmbeddingDAO();
        this.relationshipDAO = new PaperRelationshipDAO();
        this.entityDAO = new EntityDAO();
        this.clusteringEngine = new ClusteringEngine();
        this.entityExtractor = new EntityExtractor();
        this.venueQualityProvider = new VenueQualityProvider.DictionaryProvider();
    }

    /**
     * Build a complete KnowledgeContext from the current library state.
     *
     * @return populated KnowledgeContext ready for gap analysis
     */
    public KnowledgeContext build() {
        long startTime = System.currentTimeMillis();
        KnowledgeContext ctx = new KnowledgeContext();

        // ── Step 1: Load all library entries ────────────────────────────
        List<LibraryEntry> entries = libraryDAO.getAll(null, null, null);
        ctx.setEntries(entries);
        System.out.println("[KnowledgeBuilder] Loaded " + entries.size() + " library entries");

        if (entries.isEmpty()) {
            return ctx;
        }

        // Build paperId index for fast lookup
        Map<Integer, LibraryEntry> entryById = new HashMap<>();
        List<Publication> publications = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            if (entry.getPublication() != null) {
                entryById.put(entry.getId(), entry);
                publications.add(entry.getPublication());
            }
        }

        // ── Step 2: Load cached embeddings ─────────────────────────────
        Map<Integer, float[]> embeddings = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
        ctx.setEmbeddings(embeddings);

        // ── Step 3: Extract entities ───────────────────────────────────
        Map<Integer, Set<String>> paperMethods = new HashMap<>();
        Map<Integer, Set<String>> paperDomains = new HashMap<>();
        Map<Integer, Set<String>> paperTasks = new HashMap<>();
        Map<Integer, Set<String>> paperDatasets = new HashMap<>();
        Map<Integer, Set<String>> paperAlgorithms = new HashMap<>();
        Map<Integer, Set<String>> paperKeywords = new HashMap<>();

        // First try to load from cache
        Map<Integer, Map<EntityType, Set<String>>> cachedEntities = entityDAO.getAllEntities();

        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            if (pub == null) continue;
            int id = entry.getId();

            Map<EntityType, Set<String>> entities;
            if (cachedEntities.containsKey(id)) {
                entities = cachedEntities.get(id);
            } else {
                // Extract fresh and cache
                entities = entityExtractor.extractFromPaper(pub.getTitle(), pub.getAbstractText());
                entityDAO.saveEntities(id, entities);
            }

            paperMethods.put(id, entities.getOrDefault(EntityType.METHOD, Collections.emptySet()));
            paperDomains.put(id, entities.getOrDefault(EntityType.DOMAIN, Collections.emptySet()));
            paperTasks.put(id, entities.getOrDefault(EntityType.TASK, Collections.emptySet()));
            paperDatasets.put(id, entities.getOrDefault(EntityType.DATASET, Collections.emptySet()));
            paperAlgorithms.put(id, entities.getOrDefault(EntityType.ALGORITHM, Collections.emptySet()));

            // Keywords from tags
            Set<String> keywords = new HashSet<>();
            if (pub.getTags() != null) {
                for (Tag tag : pub.getTags()) {
                    keywords.add(tag.getName().toLowerCase());
                }
            }
            paperKeywords.put(id, keywords);
        }

        ctx.setPaperMethods(paperMethods);
        ctx.setPaperDomains(paperDomains);
        ctx.setPaperTasks(paperTasks);
        ctx.setPaperDatasets(paperDatasets);
        ctx.setPaperAlgorithms(paperAlgorithms);
        ctx.setPaperKeywords(paperKeywords);

        // ── Step 4: Venue quality scores ───────────────────────────────
        Map<Integer, Double> venueQualities = new HashMap<>();
        Map<Integer, Integer> citationCounts = new HashMap<>();
        Map<Integer, Integer> paperYears = new HashMap<>();

        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            if (pub == null) continue;
            int id = entry.getId();
            venueQualities.put(id, venueQualityProvider.getQuality(pub.getVenue()));
            citationCounts.put(id, pub.getCitationCount());
            if (pub.getYear() > 0) {
                paperYears.put(id, pub.getYear());
            }
        }

        ctx.setVenueQualities(venueQualities);
        ctx.setCitationCounts(citationCounts);
        ctx.setPaperYears(paperYears);

        // ── Step 5: Cluster papers ─────────────────────────────────────
        try {
            ClusteringEngine.ClusterResult clusterResult = clusteringEngine.clusterWithDetails(publications);
            ctx.setClusters(clusterResult.getClusters());

            // Convert TF-IDF centroids to float[] for compatibility
            Map<String, float[]> centroids = new HashMap<>();
            for (Map.Entry<String, double[]> entry2 : clusterResult.getCentroids().entrySet()) {
                double[] d = entry2.getValue();
                float[] f = new float[d.length];
                for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
                centroids.put(entry2.getKey(), f);
            }
            ctx.setClusterCentroids(centroids);
        } catch (Exception e) {
            System.err.println("[KnowledgeBuilder] Clustering failed: " + e.getMessage());
            ctx.setClusters(new LinkedHashMap<>());
        }

        // ── Step 6: Build citation graph ───────────────────────────────
        try {
            List<PaperRelationship> rels = relationshipDAO.getAll();
            Map<Integer, Set<Integer>> adjacency = new HashMap<>();
            List<int[]> edgePairs = new ArrayList<>();

            for (PaperRelationship rel : rels) {
                if (rel.isDismissed()) continue;
                int src = rel.getSourcePaperId();
                int tgt = rel.getTargetPaperId();
                adjacency.computeIfAbsent(src, k -> new HashSet<>()).add(tgt);
                adjacency.computeIfAbsent(tgt, k -> new HashSet<>()).add(src);
                edgePairs.add(new int[]{src, tgt});
            }

            ctx.setAdjacency(adjacency);
            ctx.setEdgePairs(edgePairs);
        } catch (Exception e) {
            System.err.println("[KnowledgeBuilder] Graph build failed: " + e.getMessage());
        }

        // ── Step 7: Aggregate cluster entities ─────────────────────────
        Map<String, Set<String>> clusterEntities = new HashMap<>();
        for (Map.Entry<String, List<Publication>> clusterEntry : ctx.getClusters().entrySet()) {
            Set<String> allEntities = new HashSet<>();
            for (Publication pub : clusterEntry.getValue()) {
                // Find the LibraryEntry ID for this publication
                for (LibraryEntry entry : entries) {
                    if (entry.getPublication() != null &&
                        pub.getPaperId() != null &&
                        pub.getPaperId().equals(entry.getPublication().getPaperId())) {
                        allEntities.addAll(ctx.getAllEntities(entry.getId()));
                        break;
                    }
                }
            }
            clusterEntities.put(clusterEntry.getKey(), allEntities);
        }
        ctx.setClusterEntities(clusterEntities);

        // ── Step 8: Cluster year distributions ─────────────────────────
        Map<String, Map<Integer, Integer>> clusterYearDist = new HashMap<>();
        for (Map.Entry<String, List<Publication>> clusterEntry : ctx.getClusters().entrySet()) {
            Map<Integer, Integer> yearDist = new TreeMap<>();
            for (Publication pub : clusterEntry.getValue()) {
                if (pub.getYear() > 0) {
                    yearDist.merge(pub.getYear(), 1, Integer::sum);
                }
            }
            clusterYearDist.put(clusterEntry.getKey(), yearDist);
        }
        ctx.setClusterYearDistribution(clusterYearDist);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("[KnowledgeBuilder] Knowledge context built in %dms: %d papers, %d clusters, %d embeddings, %d entity-enriched papers%n",
            elapsed, entries.size(), ctx.getClusterCount(), embeddings.size(),
            paperMethods.values().stream().filter(s -> !s.isEmpty()).count());

        return ctx;
    }
}
