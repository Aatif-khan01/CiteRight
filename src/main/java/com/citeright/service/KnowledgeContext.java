package com.citeright.service;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;

import java.util.*;

/**
 * Shared pre-computed knowledge representation consumed by all gap analyzers.
 *
 * Internally organized as explicit graph sub-structures:
 *   - Entity Graph: what each paper is about (methods, domains, tasks, datasets, algorithms, keywords)
 *   - Citation Graph: how papers relate (adjacency, edge pairs)
 *   - Topic Graph: how clusters relate (clusters, centroids, cluster entities)
 *   - Temporal Graph: how research evolves (paper years, cluster year distributions)
 *
 * Built once by KnowledgeBuilder, then consumed read-only by all analyzers in parallel.
 */
public class KnowledgeContext {

    // ─── Metadata ───────────────────────────────────────────────────────
    private List<LibraryEntry> entries;
    private Map<Integer, float[]> embeddings;          // paperId → BGE-M3 vector
    private Map<Integer, Integer> citationCounts;       // paperId → citation count
    private Map<Integer, Double> venueQualities;        // paperId → venue quality score (0–1.0)

    // ─── Entity Graph ───────────────────────────────────────────────────
    private Map<Integer, Set<String>> paperMethods;
    private Map<Integer, Set<String>> paperDomains;
    private Map<Integer, Set<String>> paperTasks;
    private Map<Integer, Set<String>> paperDatasets;
    private Map<Integer, Set<String>> paperAlgorithms;
    private Map<Integer, Set<String>> paperKeywords;

    // ─── Citation Graph ─────────────────────────────────────────────────
    private Map<Integer, Set<Integer>> adjacency;       // paperId → connected paper IDs
    private List<int[]> edgePairs;

    // ─── Topic Graph ────────────────────────────────────────────────────
    private Map<String, List<Publication>> clusters;     // cluster label → member publications
    private Map<String, float[]> clusterCentroids;       // cluster label → centroid embedding
    private Map<String, Set<String>> clusterEntities;    // cluster label → aggregated entities

    // ─── Temporal Graph ─────────────────────────────────────────────────
    private Map<Integer, Integer> paperYears;                              // paperId → year
    private Map<String, Map<Integer, Integer>> clusterYearDistribution;   // cluster → year → count

    public KnowledgeContext() {
        this.entries = new ArrayList<>();
        this.embeddings = new HashMap<>();
        this.citationCounts = new HashMap<>();
        this.venueQualities = new HashMap<>();

        this.paperMethods = new HashMap<>();
        this.paperDomains = new HashMap<>();
        this.paperTasks = new HashMap<>();
        this.paperDatasets = new HashMap<>();
        this.paperAlgorithms = new HashMap<>();
        this.paperKeywords = new HashMap<>();

        this.adjacency = new HashMap<>();
        this.edgePairs = new ArrayList<>();

        this.clusters = new LinkedHashMap<>();
        this.clusterCentroids = new HashMap<>();
        this.clusterEntities = new HashMap<>();

        this.paperYears = new HashMap<>();
        this.clusterYearDistribution = new HashMap<>();
    }

    // ─── Convenience queries ────────────────────────────────────────────

    /** Total number of papers in the library */
    public int getPaperCount() {
        return entries.size();
    }

    /** Total number of clusters */
    public int getClusterCount() {
        return clusters.size();
    }

    /** Get all entity types for a paper as a single merged set */
    public Set<String> getAllEntities(int paperId) {
        Set<String> all = new HashSet<>();
        all.addAll(paperMethods.getOrDefault(paperId, Collections.emptySet()));
        all.addAll(paperDomains.getOrDefault(paperId, Collections.emptySet()));
        all.addAll(paperTasks.getOrDefault(paperId, Collections.emptySet()));
        all.addAll(paperDatasets.getOrDefault(paperId, Collections.emptySet()));
        all.addAll(paperAlgorithms.getOrDefault(paperId, Collections.emptySet()));
        all.addAll(paperKeywords.getOrDefault(paperId, Collections.emptySet()));
        return all;
    }

    /** Get papers in a specific cluster as LibraryEntry IDs */
    public List<Integer> getPaperIdsInCluster(String clusterLabel) {
        List<Publication> pubs = clusters.getOrDefault(clusterLabel, Collections.emptyList());
        List<Integer> ids = new ArrayList<>();
        for (Publication pub : pubs) {
            for (LibraryEntry entry : entries) {
                if (entry.getPublication() != null &&
                    pub.getPaperId() != null &&
                    pub.getPaperId().equals(entry.getPublication().getPaperId())) {
                    ids.add(entry.getId());
                    break;
                }
            }
        }
        return ids;
    }

    /** Find which cluster a paper belongs to */
    public String getClusterForPaper(Publication pub) {
        for (Map.Entry<String, List<Publication>> entry : clusters.entrySet()) {
            if (entry.getValue().contains(pub)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ─── Getters and setters ────────────────────────────────────────────

    public List<LibraryEntry> getEntries() { return entries; }
    public void setEntries(List<LibraryEntry> entries) { this.entries = entries; }

    public Map<Integer, float[]> getEmbeddings() { return embeddings; }
    public void setEmbeddings(Map<Integer, float[]> embeddings) { this.embeddings = embeddings; }

    public Map<Integer, Integer> getCitationCounts() { return citationCounts; }
    public void setCitationCounts(Map<Integer, Integer> citationCounts) { this.citationCounts = citationCounts; }

    public Map<Integer, Double> getVenueQualities() { return venueQualities; }
    public void setVenueQualities(Map<Integer, Double> venueQualities) { this.venueQualities = venueQualities; }

    public Map<Integer, Set<String>> getPaperMethods() { return paperMethods; }
    public void setPaperMethods(Map<Integer, Set<String>> paperMethods) { this.paperMethods = paperMethods; }

    public Map<Integer, Set<String>> getPaperDomains() { return paperDomains; }
    public void setPaperDomains(Map<Integer, Set<String>> paperDomains) { this.paperDomains = paperDomains; }

    public Map<Integer, Set<String>> getPaperTasks() { return paperTasks; }
    public void setPaperTasks(Map<Integer, Set<String>> paperTasks) { this.paperTasks = paperTasks; }

    public Map<Integer, Set<String>> getPaperDatasets() { return paperDatasets; }
    public void setPaperDatasets(Map<Integer, Set<String>> paperDatasets) { this.paperDatasets = paperDatasets; }

    public Map<Integer, Set<String>> getPaperAlgorithms() { return paperAlgorithms; }
    public void setPaperAlgorithms(Map<Integer, Set<String>> paperAlgorithms) { this.paperAlgorithms = paperAlgorithms; }

    public Map<Integer, Set<String>> getPaperKeywords() { return paperKeywords; }
    public void setPaperKeywords(Map<Integer, Set<String>> paperKeywords) { this.paperKeywords = paperKeywords; }

    public Map<Integer, Set<Integer>> getAdjacency() { return adjacency; }
    public void setAdjacency(Map<Integer, Set<Integer>> adjacency) { this.adjacency = adjacency; }

    public List<int[]> getEdgePairs() { return edgePairs; }
    public void setEdgePairs(List<int[]> edgePairs) { this.edgePairs = edgePairs; }

    public Map<String, List<Publication>> getClusters() { return clusters; }
    public void setClusters(Map<String, List<Publication>> clusters) { this.clusters = clusters; }

    public Map<String, float[]> getClusterCentroids() { return clusterCentroids; }
    public void setClusterCentroids(Map<String, float[]> clusterCentroids) { this.clusterCentroids = clusterCentroids; }

    public Map<String, Set<String>> getClusterEntities() { return clusterEntities; }
    public void setClusterEntities(Map<String, Set<String>> clusterEntities) { this.clusterEntities = clusterEntities; }

    public Map<Integer, Integer> getPaperYears() { return paperYears; }
    public void setPaperYears(Map<Integer, Integer> paperYears) { this.paperYears = paperYears; }

    public Map<String, Map<Integer, Integer>> getClusterYearDistribution() { return clusterYearDistribution; }
    public void setClusterYearDistribution(Map<String, Map<Integer, Integer>> dist) { this.clusterYearDistribution = dist; }
}
