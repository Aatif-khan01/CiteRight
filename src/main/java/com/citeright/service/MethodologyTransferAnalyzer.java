package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Detects methodology transfer opportunities — methods proven in one domain
 * that have never been applied to related domains in the user's library.
 *
 * Algorithm:
 *   1. Build method → domain matrix: which methods appear in which clusters
 *   2. For each method M used in cluster A:
 *      a. Find clusters B where M has NOT been used
 *      b. Check if clusters A and B are conceptually related (entity overlap > threshold)
 *      c. If related but method is absent → methodology transfer gap
 *   3. Rank by: method maturity × domain overlap × cluster size
 */
public class MethodologyTransferAnalyzer implements GapAnalyzerModule {

    private static final String NAME = "MethodologyTransferAnalyzer";
    private static final String VERSION = "1.0";

    /** Minimum entity overlap between source and target clusters */
    private static final double MIN_DOMAIN_OVERLAP = 0.03;

    /** Minimum papers using a method to consider it "proven" */
    private static final int MIN_METHOD_PAPERS = 2;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public List<ResearchGap> analyze(KnowledgeContext context) {
        List<ResearchGap> gaps = new ArrayList<>();

        Map<String, List<Publication>> clusters = context.getClusters();
        if (clusters.size() < 2) return gaps;

        // ── Step 1: Build method → cluster matrix ───────────────────────
        // For each cluster, collect all methods used within it
        Map<String, Set<String>> clusterMethods = new HashMap<>();
        Map<String, Map<String, Integer>> clusterMethodCounts = new HashMap<>(); // cluster → method → count

        for (Map.Entry<String, List<Publication>> clusterEntry : clusters.entrySet()) {
            String label = clusterEntry.getKey();
            Set<String> methods = new HashSet<>();
            Map<String, Integer> methodCounts = new HashMap<>();

            for (Publication pub : clusterEntry.getValue()) {
                int paperId = findEntryId(context, pub);
                if (paperId < 0) continue;

                Set<String> paperMethods = context.getPaperMethods().getOrDefault(paperId, Collections.emptySet());
                methods.addAll(paperMethods);
                for (String m : paperMethods) {
                    methodCounts.merge(m, 1, Integer::sum);
                }
            }

            clusterMethods.put(label, methods);
            clusterMethodCounts.put(label, methodCounts);
        }

        // ── Step 2: Find transfer opportunities ────────────────────────
        List<String> clusterLabels = new ArrayList<>(clusters.keySet());

        for (String sourceCluster : clusterLabels) {
            Set<String> sourceMethods = clusterMethods.getOrDefault(sourceCluster, Collections.emptySet());
            Map<String, Integer> sourceMethodCounts = clusterMethodCounts.getOrDefault(sourceCluster, Collections.emptyMap());
            Set<String> sourceEntities = context.getClusterEntities().getOrDefault(sourceCluster, Collections.emptySet());

            for (String method : sourceMethods) {
                // Method must be "proven" (used in multiple papers)
                int methodCount = sourceMethodCounts.getOrDefault(method, 0);
                if (methodCount < MIN_METHOD_PAPERS) continue;

                for (String targetCluster : clusterLabels) {
                    if (sourceCluster.equals(targetCluster)) continue;

                    Set<String> targetMethods = clusterMethods.getOrDefault(targetCluster, Collections.emptySet());

                    // Skip if target already uses this method
                    if (targetMethods.contains(method)) continue;

                    // Check domain overlap
                    Set<String> targetEntities = context.getClusterEntities().getOrDefault(targetCluster, Collections.emptySet());
                    double domainOverlap = HybridSimilarityCalculator.jaccardSimilarity(sourceEntities, targetEntities);

                    if (domainOverlap < MIN_DOMAIN_OVERLAP) continue;

                    // ── Gap detected: method proven in source, absent in related target ──
                    String gapId = "TRANSFER_" + method.hashCode() + "_" + targetCluster.hashCode();
                    String title = "Transfer '" + method + "' to " + truncate(targetCluster, 40);

                    // Shared concepts between domains
                    Set<String> sharedConcepts = new LinkedHashSet<>(sourceEntities);
                    sharedConcepts.retainAll(targetEntities);
                    List<String> sharedList = new ArrayList<>(sharedConcepts);
                    if (sharedList.size() > 5) sharedList = sharedList.subList(0, 5);

                    // Build evidence
                    GapEvidence evidence = new GapEvidence(
                        String.format("'%s' is used in %d papers in '%s' but absent from '%s'. " +
                            "The domains share %d concepts (%s), suggesting transferability.",
                            method, methodCount, truncate(sourceCluster, 30),
                            truncate(targetCluster, 30),
                            sharedConcepts.size(), String.join(", ", sharedList)),
                        domainOverlap,
                        collectMethodPaperIds(context, sourceCluster, method)
                    );

                    // Build provenance
                    GapProvenance provenance = new GapProvenance(NAME, VERSION,
                        String.format("%d methods across %d clusters", sourceMethods.size(), clusters.size()));
                    provenance.addStep(String.format("Found method '%s' used in %d papers in cluster '%s'",
                        method, methodCount, sourceCluster));
                    provenance.addStep(String.format("Method absent from cluster '%s' (domain overlap: %.3f)",
                        targetCluster, domainOverlap));
                    provenance.addStep("Methodology transfer opportunity detected");
                    provenance.addEvidence(collectMethodPaperIds(context, sourceCluster, method));

                    // Uncertainty factors
                    List<UncertaintyFactor> uncertainties = new ArrayList<>();
                    uncertainties.add(new UncertaintyFactor(
                        String.format("Method proven in %d papers", methodCount),
                        UncertaintyFactor.Direction.STRENGTHENS,
                        Math.min(1.0, methodCount / 5.0)));

                    if (domainOverlap > 0.1) {
                        uncertainties.add(new UncertaintyFactor(
                            String.format("Strong domain overlap (%.1f%%)", domainOverlap * 100),
                            UncertaintyFactor.Direction.STRENGTHENS,
                            domainOverlap));
                    }

                    if (methodCount < 3) {
                        uncertainties.add(new UncertaintyFactor(
                            "Method used in few papers — may be niche",
                            UncertaintyFactor.Direction.WEAKENS,
                            0.3));
                    }

                    // Timeline from source cluster
                    List<Integer> years = new ArrayList<>();
                    for (Publication p : clusters.get(sourceCluster)) if (p.getYear() > 0) years.add(p.getYear());

                    double confidence = Math.min(1.0, domainOverlap * 3 + (methodCount / 10.0));

                    ResearchGap gap = new ResearchGap()
                        .gapId(gapId)
                        .type(GapType.METHODOLOGY_TRANSFER)
                        .title(title)
                        .explanation(String.format(
                            "The method '%s' has been successfully used in %d papers within '%s', " +
                            "but has not yet been applied to the related domain '%s'. " +
                            "Given their shared concepts (%s), this method may yield novel results there.",
                            method, methodCount, sourceCluster, targetCluster,
                            String.join(", ", sharedList)))
                        .detectionMethod("Method-domain matrix analysis with entity overlap verification")
                        .confidenceExplanation(String.format(
                            "Based on method maturity (%d papers), domain relatedness (%.1f%% overlap), " +
                            "and %d shared concepts.",
                            methodCount, domainOverlap * 100, sharedConcepts.size()))
                        .confidence(confidence)
                        .provenance(provenance)
                        .temporalContext(GapTimeline.fromYears(years));

                    gap.addEvidence(evidence);
                    gap.setUncertaintyFactors(uncertainties);
                    gap.setAffectedClusters(List.of(sourceCluster, targetCluster));
                    gap.setRecommendedMethods(List.of(method));

                    gaps.add(gap);
                }
            }
        }

        System.out.println("[MethodologyTransferAnalyzer] Found " + gaps.size() + " transfer opportunities");
        return gaps;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private int findEntryId(KnowledgeContext context, Publication pub) {
        if (pub.getPaperId() == null) return -1;
        for (LibraryEntry entry : context.getEntries()) {
            if (entry.getPublication() != null &&
                pub.getPaperId().equals(entry.getPublication().getPaperId())) {
                return entry.getId();
            }
        }
        return -1;
    }

    private List<Integer> collectMethodPaperIds(KnowledgeContext context, String cluster, String method) {
        List<Integer> ids = new ArrayList<>();
        List<Publication> pubs = context.getClusters().getOrDefault(cluster, Collections.emptyList());
        for (Publication pub : pubs) {
            int entryId = findEntryId(context, pub);
            if (entryId < 0) continue;
            Set<String> methods = context.getPaperMethods().getOrDefault(entryId, Collections.emptySet());
            if (methods.contains(method)) {
                ids.add(entryId);
            }
        }
        return ids;
    }

    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) + "..." : s;
    }
}
