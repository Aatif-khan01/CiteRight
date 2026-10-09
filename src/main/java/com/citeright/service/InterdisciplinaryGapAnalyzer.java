package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Detects interdisciplinary gaps — mature clusters with weak cross-connections
 * despite having sufficient conceptual affinity to benefit from collaboration.
 *
 * Different from TopicGapAnalyzer:
 *   - TopicGapAnalyzer looks at entity overlap between any cluster pair
 *   - InterdisciplinaryGapAnalyzer specifically looks for MATURE clusters
 *     (high paper count + temporal span) that should have connected but haven't
 *
 * Algorithm:
 *   1. Identify "mature" clusters (≥ maturity threshold papers, active ≥ 3 years)
 *   2. For each pair of mature clusters:
 *      a. Compute hybrid similarity (embeddings + entities)
 *      b. Count actual cross-citations (bridge papers)
 *      c. If similarity is moderate-to-high but bridge papers are ≤ 2 → gap
 *   3. Higher scores for clusters that are both large, old, and conceptually related
 */
public class InterdisciplinaryGapAnalyzer implements GapAnalyzerModule {

    private static final String NAME = "InterdisciplinaryGapAnalyzer";
    private static final String VERSION = "1.0";

    /** Minimum papers for a cluster to be "mature" */
    private static final int MATURITY_THRESHOLD = 5;

    /** Minimum years a cluster must span */
    private static final int MIN_YEAR_SPAN = 2;

    /** Maximum bridge papers allowed for a gap to exist */
    private static final int MAX_BRIDGE_PAPERS = 2;

    /** Minimum cluster similarity to consider them related */
    private static final double MIN_CLUSTER_SIMILARITY = 0.08;

    private final HybridSimilarityCalculator similarityCalc;

    public InterdisciplinaryGapAnalyzer() {
        this.similarityCalc = new HybridSimilarityCalculator();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public List<ResearchGap> analyze(KnowledgeContext context) {
        List<ResearchGap> gaps = new ArrayList<>();

        Map<String, List<Publication>> clusters = context.getClusters();
        if (clusters.size() < 2) return gaps;

        // ── Step 1: Filter mature clusters ──────────────────────────────
        List<String> matureClusters = new ArrayList<>();
        for (Map.Entry<String, List<Publication>> entry : clusters.entrySet()) {
            String label = entry.getKey();
            List<Publication> papers = entry.getValue();

            if (papers.size() < MATURITY_THRESHOLD) continue;

            // Check year span
            int minYear = Integer.MAX_VALUE, maxYear = Integer.MIN_VALUE;
            for (Publication p : papers) {
                if (p.getYear() > 0) {
                    minYear = Math.min(minYear, p.getYear());
                    maxYear = Math.max(maxYear, p.getYear());
                }
            }

            if (maxYear - minYear >= MIN_YEAR_SPAN) {
                matureClusters.add(label);
            }
        }

        if (matureClusters.size() < 2) return gaps;

        // ── Step 2: Find interdisciplinary gaps ─────────────────────────
        for (int i = 0; i < matureClusters.size(); i++) {
            for (int j = i + 1; j < matureClusters.size(); j++) {
                String labelA = matureClusters.get(i);
                String labelB = matureClusters.get(j);

                List<Publication> clusterA = clusters.get(labelA);
                List<Publication> clusterB = clusters.get(labelB);

                // Compute cluster similarity
                Set<String> entitiesA = context.getClusterEntities().getOrDefault(labelA, Collections.emptySet());
                Set<String> entitiesB = context.getClusterEntities().getOrDefault(labelB, Collections.emptySet());

                float[] centroidA = context.getClusterCentroids().get(labelA);
                float[] centroidB = context.getClusterCentroids().get(labelB);

                int crossEdges = countCrossClusterEdges(context, clusterA, clusterB);
                double clusterSim = similarityCalc.computeClusterSimilarity(
                    centroidA, centroidB, entitiesA, entitiesB,
                    crossEdges, clusterA.size(), clusterB.size());

                if (clusterSim < MIN_CLUSTER_SIMILARITY) continue;

                // Count bridge papers (papers that cite across both clusters)
                int bridgePapers = countBridgePapers(context, clusterA, clusterB);

                if (bridgePapers > MAX_BRIDGE_PAPERS) continue;

                // ── Interdisciplinary gap detected ──────────────────────
                Set<String> shared = new LinkedHashSet<>(entitiesA);
                shared.retainAll(entitiesB);
                List<String> sharedList = new ArrayList<>(shared);
                if (sharedList.size() > 5) sharedList = sharedList.subList(0, 5);

                String gapId = "INTERDISCIPLINARY_" + labelA.hashCode() + "_" + labelB.hashCode();
                String title = "Cross-pollination gap: " + truncate(labelA, 25) + " ↔ " + truncate(labelB, 25);

                GapEvidence evidence = new GapEvidence(
                    String.format("Two mature research areas ('%s': %d papers, '%s': %d papers) " +
                        "have %.1f%% conceptual similarity but only %d bridge papers connecting them.",
                        truncate(labelA, 30), clusterA.size(),
                        truncate(labelB, 30), clusterB.size(),
                        clusterSim * 100, bridgePapers),
                    clusterSim,
                    collectAllPaperIds(context, clusterA, clusterB)
                );

                GapProvenance provenance = new GapProvenance(NAME, VERSION,
                    String.format("%d mature clusters out of %d total", matureClusters.size(), clusters.size()));
                provenance.addStep("Identified mature clusters: '" + labelA + "' (" + clusterA.size() + " papers) and '" +
                    labelB + "' (" + clusterB.size() + " papers)");
                provenance.addStep("Computed cluster similarity: " + String.format("%.3f", clusterSim));
                provenance.addStep("Bridge papers found: " + bridgePapers + " (threshold: " + MAX_BRIDGE_PAPERS + ")");
                provenance.addStep("Interdisciplinary gap detected — mature fields with insufficient cross-pollination");
                provenance.addEvidence(collectAllPaperIds(context, clusterA, clusterB));

                List<UncertaintyFactor> uncertainties = new ArrayList<>();
                uncertainties.add(new UncertaintyFactor(
                    "Both clusters are mature (" + clusterA.size() + " and " + clusterB.size() + " papers)",
                    UncertaintyFactor.Direction.STRENGTHENS, 0.8));

                if (shared.size() >= 3) {
                    uncertainties.add(new UncertaintyFactor(
                        shared.size() + " shared concepts (" + String.join(", ", sharedList.subList(0, Math.min(3, sharedList.size()))) + ")",
                        UncertaintyFactor.Direction.STRENGTHENS,
                        Math.min(1.0, shared.size() / 10.0)));
                }

                if (bridgePapers == 0) {
                    uncertainties.add(new UncertaintyFactor(
                        "Zero bridge papers — complete isolation",
                        UncertaintyFactor.Direction.STRENGTHENS, 0.9));
                }

                if (clusterSim < 0.15) {
                    uncertainties.add(new UncertaintyFactor(
                        "Relatively low similarity — may not benefit from cross-pollination",
                        UncertaintyFactor.Direction.WEAKENS, 0.4));
                }

                // Timeline from both clusters
                List<Integer> years = new ArrayList<>();
                for (Publication p : clusterA) if (p.getYear() > 0) years.add(p.getYear());
                for (Publication p : clusterB) if (p.getYear() > 0) years.add(p.getYear());

                double confidence = Math.min(1.0, clusterSim * 3 + (1.0 / (bridgePapers + 1)) * 0.3);

                ResearchGap gap = new ResearchGap()
                    .gapId(gapId)
                    .type(GapType.INTERDISCIPLINARY)
                    .title(title)
                    .explanation(String.format(
                        "Two established research areas — '%s' (%d papers) and '%s' (%d papers) — " +
                        "share conceptual ground (%s) but have almost no cross-referencing. " +
                        "Interdisciplinary research between these fields may yield novel insights.",
                        labelA, clusterA.size(), labelB, clusterB.size(),
                        String.join(", ", sharedList)))
                    .detectionMethod("Mature cluster pair analysis using hybrid similarity + bridge paper counting")
                    .confidenceExplanation(String.format(
                        "Confidence based on cluster maturity, %.1f%% conceptual similarity, and only %d bridge papers.",
                        clusterSim * 100, bridgePapers))
                    .confidence(confidence)
                    .provenance(provenance)
                    .temporalContext(GapTimeline.fromYears(years));

                gap.addEvidence(evidence);
                gap.setUncertaintyFactors(uncertainties);
                gap.setAffectedClusters(List.of(labelA, labelB));
                gap.setAffectedPaperIds(collectAllPaperIds(context, clusterA, clusterB));

                gaps.add(gap);
            }
        }

        System.out.println("[InterdisciplinaryGapAnalyzer] Found " + gaps.size() +
            " interdisciplinary gaps among " + matureClusters.size() + " mature clusters");
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

        Map<Integer, String> entryIdToPaperId = new HashMap<>();
        for (LibraryEntry entry : context.getEntries()) {
            if (entry.getPublication() != null && entry.getPublication().getPaperId() != null) {
                entryIdToPaperId.put(entry.getId(), entry.getPublication().getPaperId());
            }
        }

        int count = 0;
        for (int[] edge : context.getEdgePairs()) {
            String src = entryIdToPaperId.get(edge[0]);
            String tgt = entryIdToPaperId.get(edge[1]);
            if (src != null && tgt != null) {
                if ((idsA.contains(src) && idsB.contains(tgt)) ||
                    (idsB.contains(src) && idsA.contains(tgt))) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countBridgePapers(KnowledgeContext context,
                                   List<Publication> clusterA,
                                   List<Publication> clusterB) {
        // A "bridge paper" is one that cites papers in both clusters
        Set<String> idsA = new HashSet<>();
        for (Publication p : clusterA) if (p.getPaperId() != null) idsA.add(p.getPaperId());
        Set<String> idsB = new HashSet<>();
        for (Publication p : clusterB) if (p.getPaperId() != null) idsB.add(p.getPaperId());

        Map<Integer, String> entryIdToPaperId = new HashMap<>();
        for (LibraryEntry entry : context.getEntries()) {
            if (entry.getPublication() != null && entry.getPublication().getPaperId() != null) {
                entryIdToPaperId.put(entry.getId(), entry.getPublication().getPaperId());
            }
        }

        // For each paper, check if it connects to both clusters
        Set<Integer> bridgeEntries = new HashSet<>();
        for (Map.Entry<Integer, Set<Integer>> adj : context.getAdjacency().entrySet()) {
            int entryId = adj.getKey();
            String paperId = entryIdToPaperId.get(entryId);
            if (paperId == null) continue;

            boolean connectsA = idsA.contains(paperId);
            boolean connectsB = idsB.contains(paperId);

            if (!connectsA && !connectsB) {
                // Check if neighbors span both clusters
                boolean hasNeighborInA = false, hasNeighborInB = false;
                for (int neighborId : adj.getValue()) {
                    String nPaperId = entryIdToPaperId.get(neighborId);
                    if (nPaperId != null) {
                        if (idsA.contains(nPaperId)) hasNeighborInA = true;
                        if (idsB.contains(nPaperId)) hasNeighborInB = true;
                    }
                }
                if (hasNeighborInA && hasNeighborInB) bridgeEntries.add(entryId);
            }
        }

        return bridgeEntries.size();
    }

    private List<Integer> collectAllPaperIds(KnowledgeContext context,
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
