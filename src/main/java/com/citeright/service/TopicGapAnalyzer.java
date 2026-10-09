package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Detects underexplored topic combinations — pairs of clusters that share
 * concepts but have very few papers spanning both topics.
 *
 * Algorithm:
 *   1. For each pair of clusters (A, B):
 *      a. Compute entity overlap using Jaccard on aggregated entities
 *      b. Count cross-cluster citation edges
 *      c. If entity overlap is high but cross-citation density is low → gap
 *   2. Rank gaps by overlap strength × inverse connection density
 *   3. Generate structured evidence and provenance
 */
public class TopicGapAnalyzer implements GapAnalyzerModule {

    private static final String NAME = "TopicGapAnalyzer";
    private static final String VERSION = "1.0";

    /** Minimum entity overlap (Jaccard) to consider clusters related */
    private static final double MIN_ENTITY_OVERLAP = 0.05;

    /** Maximum connection density to qualify as a gap */
    private static final double MAX_CONNECTION_DENSITY = 0.10;

    /** Minimum cluster size to consider (ignore tiny clusters) */
    private static final int MIN_CLUSTER_SIZE = 3;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public List<ResearchGap> analyze(KnowledgeContext context) {
        List<ResearchGap> gaps = new ArrayList<>();

        Map<String, List<Publication>> clusters = context.getClusters();
        if (clusters.size() < 2) return gaps;

        List<String> clusterLabels = new ArrayList<>(clusters.keySet());

        for (int i = 0; i < clusterLabels.size(); i++) {
            for (int j = i + 1; j < clusterLabels.size(); j++) {
                String labelA = clusterLabels.get(i);
                String labelB = clusterLabels.get(j);

                List<Publication> clusterA = clusters.get(labelA);
                List<Publication> clusterB = clusters.get(labelB);

                // Skip tiny clusters
                if (clusterA.size() < MIN_CLUSTER_SIZE || clusterB.size() < MIN_CLUSTER_SIZE) continue;

                Set<String> entitiesA = context.getClusterEntities().getOrDefault(labelA, Collections.emptySet());
                Set<String> entitiesB = context.getClusterEntities().getOrDefault(labelB, Collections.emptySet());

                // Compute entity overlap
                double entityOverlap = HybridSimilarityCalculator.jaccardSimilarity(entitiesA, entitiesB);
                if (entityOverlap < MIN_ENTITY_OVERLAP) continue;

                // Compute cross-cluster connection density
                int crossEdges = countCrossClusterEdges(context, clusterA, clusterB);
                double maxPossibleEdges = clusterA.size() * clusterB.size();
                double connectionDensity = maxPossibleEdges > 0 ? crossEdges / maxPossibleEdges : 0.0;

                // Gap detected if: high conceptual overlap + low connection density
                if (connectionDensity <= MAX_CONNECTION_DENSITY) {
                    // Compute shared entities for explanation
                    Set<String> sharedEntities = new LinkedHashSet<>(entitiesA);
                    sharedEntities.retainAll(entitiesB);

                    if (sharedEntities.isEmpty()) continue;

                    // Calculate gap strength
                    double gapStrength = entityOverlap * (1.0 - connectionDensity);

                    String gapId = "TOPIC_" + labelA.hashCode() + "_" + labelB.hashCode();
                    String title = "Underexplored link: " + truncate(labelA, 30) + " × " + truncate(labelB, 30);

                    // Build evidence
                    List<String> sharedList = new ArrayList<>(sharedEntities);
                    if (sharedList.size() > 5) sharedList = sharedList.subList(0, 5);

                    GapEvidence evidence = new GapEvidence(
                        String.format("Clusters share %d concepts (%s) but only %d cross-citations exist (density: %.1f%%)",
                            sharedEntities.size(),
                            String.join(", ", sharedList),
                            crossEdges,
                            connectionDensity * 100),
                        entityOverlap,
                        collectPaperIds(context, clusterA, clusterB)
                    );

                    // Build provenance
                    GapProvenance provenance = new GapProvenance(NAME, VERSION,
                        String.format("%d papers across %d clusters", context.getPaperCount(), context.getClusterCount()));
                    provenance.addStep("Computed entity overlap between '" + labelA + "' and '" + labelB + "': " +
                        String.format("%.3f (Jaccard)", entityOverlap));
                    provenance.addStep("Measured cross-cluster citation density: " +
                        String.format("%.1f%% (%d edges / %.0f possible)", connectionDensity * 100, crossEdges, maxPossibleEdges));
                    provenance.addStep("Gap detected: high concept overlap with low cross-citation density");
                    provenance.addEvidence(collectPaperIds(context, clusterA, clusterB));

                    // Build uncertainty factors
                    List<UncertaintyFactor> uncertainties = new ArrayList<>();
                    uncertainties.add(new UncertaintyFactor(
                        "Entity overlap: " + String.format("%.1f%%", entityOverlap * 100),
                        UncertaintyFactor.Direction.STRENGTHENS,
                        entityOverlap));

                    if (sharedEntities.size() >= 3) {
                        uncertainties.add(new UncertaintyFactor(
                            sharedEntities.size() + " shared concepts",
                            UncertaintyFactor.Direction.STRENGTHENS,
                            Math.min(1.0, sharedEntities.size() / 10.0)));
                    }

                    if (crossEdges <= 1) {
                        uncertainties.add(new UncertaintyFactor(
                            "Almost no cross-citations (" + crossEdges + ")",
                            UncertaintyFactor.Direction.STRENGTHENS,
                            0.8));
                    }

                    if (clusterA.size() < 5 || clusterB.size() < 5) {
                        uncertainties.add(new UncertaintyFactor(
                            "Small cluster size (may be noise)",
                            UncertaintyFactor.Direction.WEAKENS,
                            0.4));
                    }

                    // Build gap timeline from both clusters' papers
                    List<Integer> years = new ArrayList<>();
                    for (Publication p : clusterA) if (p.getYear() > 0) years.add(p.getYear());
                    for (Publication p : clusterB) if (p.getYear() > 0) years.add(p.getYear());
                    GapTimeline timeline = GapTimeline.fromYears(years);

                    // Assemble ResearchGap
                    ResearchGap gap = new ResearchGap()
                        .gapId(gapId)
                        .type(GapType.TOPIC)
                        .title(title)
                        .explanation(String.format(
                            "The topics '%s' and '%s' share %d concepts (%s) but have very few cross-citations. " +
                            "This suggests a potentially underexplored research intersection.",
                            labelA, labelB, sharedEntities.size(), String.join(", ", sharedList)))
                        .detectionMethod("Entity overlap analysis between topic clusters with cross-citation density check")
                        .confidenceExplanation(String.format(
                            "Confidence based on entity overlap (%.1f%%) and gap density (%.1f%%). " +
                            "Higher overlap + lower density = higher confidence.",
                            entityOverlap * 100, connectionDensity * 100))
                        .confidence(Math.min(1.0, gapStrength * 2))
                        .provenance(provenance)
                        .temporalContext(timeline);

                    gap.addEvidence(evidence);
                    gap.setUncertaintyFactors(uncertainties);
                    gap.setAffectedClusters(List.of(labelA, labelB));
                    gap.setAffectedPaperIds(collectPaperIds(context, clusterA, clusterB));

                    // Recommended methods: methods from either cluster
                    Set<String> allMethods = new HashSet<>();
                    for (Map.Entry<Integer, Set<String>> entry : context.getPaperMethods().entrySet()) {
                        allMethods.addAll(entry.getValue());
                    }
                    gap.setRecommendedMethods(new ArrayList<>(allMethods).subList(0, Math.min(5, allMethods.size())));

                    gaps.add(gap);
                }
            }
        }

        System.out.println("[TopicGapAnalyzer] Found " + gaps.size() + " topic gaps across " + clusterLabels.size() + " clusters");
        return gaps;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private int countCrossClusterEdges(KnowledgeContext context,
                                       List<Publication> clusterA,
                                       List<Publication> clusterB) {
        Set<String> idsA = new HashSet<>();
        for (Publication p : clusterA) if (p.getPaperId() != null) idsA.add(p.getPaperId());

        Set<String> idsB = new HashSet<>();
        for (Publication p : clusterB) if (p.getPaperId() != null) idsB.add(p.getPaperId());

        // Map string paper IDs to int entry IDs
        Map<Integer, String> entryIdToPaperId = new HashMap<>();
        for (LibraryEntry entry : context.getEntries()) {
            if (entry.getPublication() != null && entry.getPublication().getPaperId() != null) {
                entryIdToPaperId.put(entry.getId(), entry.getPublication().getPaperId());
            }
        }

        int count = 0;
        for (int[] edge : context.getEdgePairs()) {
            String srcPaperId = entryIdToPaperId.get(edge[0]);
            String tgtPaperId = entryIdToPaperId.get(edge[1]);
            if (srcPaperId != null && tgtPaperId != null) {
                if ((idsA.contains(srcPaperId) && idsB.contains(tgtPaperId)) ||
                    (idsB.contains(srcPaperId) && idsA.contains(tgtPaperId))) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<Integer> collectPaperIds(KnowledgeContext context,
                                           List<Publication> clusterA,
                                           List<Publication> clusterB) {
        List<Integer> ids = new ArrayList<>();
        Set<String> paperIds = new HashSet<>();
        for (Publication p : clusterA) if (p.getPaperId() != null) paperIds.add(p.getPaperId());
        for (Publication p : clusterB) if (p.getPaperId() != null) paperIds.add(p.getPaperId());

        for (LibraryEntry entry : context.getEntries()) {
            if (entry.getPublication() != null &&
                entry.getPublication().getPaperId() != null &&
                paperIds.contains(entry.getPublication().getPaperId())) {
                ids.add(entry.getId());
            }
        }
        return ids;
    }

    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) + "..." : s;
    }
}
