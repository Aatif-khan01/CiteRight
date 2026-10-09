package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Detects temporal gaps — research areas that have become dormant or are declining.
 *
 * Two sub-types:
 *   - DORMANT: Last publication ≥ 3 years ago, was active before that
 *   - DECLINING: Publication rate dropped ≥ 50% from peak in recent years
 *
 * Algorithm:
 *   1. For each cluster, build year-by-year publication histogram
 *   2. Compute peak activity period and recent activity
 *   3. If activity dropped significantly → temporal gap
 *   4. Higher gap scores for topics that had high peak activity (important, not obscure)
 */
public class TemporalGapAnalyzer implements GapAnalyzerModule {

    private static final String NAME = "TemporalGapAnalyzer";
    private static final String VERSION = "1.0";

    /** Years since latest publication to consider "dormant" */
    private static final int DORMANT_THRESHOLD_YEARS = 3;

    /** Ratio of recent to peak activity to consider "declining" */
    private static final double DECLINING_RATIO = 0.5;

    /** Minimum cluster size to analyze */
    private static final int MIN_CLUSTER_SIZE = 3;

    /** Minimum peak paper count to consider significant */
    private static final int MIN_PEAK_PAPERS = 2;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public List<ResearchGap> analyze(KnowledgeContext context) {
        List<ResearchGap> gaps = new ArrayList<>();

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        for (Map.Entry<String, List<Publication>> clusterEntry : context.getClusters().entrySet()) {
            String label = clusterEntry.getKey();
            List<Publication> papers = clusterEntry.getValue();

            if (papers.size() < MIN_CLUSTER_SIZE) continue;

            // Get year distribution
            Map<Integer, Integer> yearDist = context.getClusterYearDistribution()
                .getOrDefault(label, Collections.emptyMap());

            if (yearDist.isEmpty()) continue;

            // Build timeline
            List<Integer> years = new ArrayList<>();
            for (Publication p : papers) if (p.getYear() > 0) years.add(p.getYear());
            GapTimeline timeline = GapTimeline.fromYears(years);

            if (timeline.getPeakCount() < MIN_PEAK_PAPERS) continue;

            // ── Check for DORMANT ───────────────────────────────────────
            int yearsSinceLatest = currentYear - timeline.getLatestYear();

            if (yearsSinceLatest >= DORMANT_THRESHOLD_YEARS) {
                ResearchGap gap = createDormantGap(context, label, papers, timeline, yearsSinceLatest);
                if (gap != null) gaps.add(gap);
                continue;
            }

            // ── Check for DECLINING ─────────────────────────────────────
            int recentCount = 0;
            for (int y = currentYear - 2; y <= currentYear; y++) {
                recentCount += yearDist.getOrDefault(y, 0);
            }
            double recentAvg = recentCount / 3.0;
            double peakAvg = timeline.getPeakCount();

            if (peakAvg > 0 && recentAvg / peakAvg < DECLINING_RATIO && timeline.getPeakCount() >= MIN_PEAK_PAPERS) {
                ResearchGap gap = createDecliningGap(context, label, papers, timeline, recentAvg, peakAvg);
                if (gap != null) gaps.add(gap);
            }
        }

        System.out.println("[TemporalGapAnalyzer] Found " + gaps.size() + " temporal gaps");
        return gaps;
    }

    // ─── Dormant Gap ────────────────────────────────────────────────────

    private ResearchGap createDormantGap(KnowledgeContext context, String label,
                                          List<Publication> papers, GapTimeline timeline,
                                          int yearsSince) {
        String gapId = "TEMPORAL_DORMANT_" + label.hashCode();
        String title = "Dormant topic: " + truncate(label, 50);

        GapEvidence evidence = new GapEvidence(
            String.format("Topic '%s' peaked at %d papers in %d, with no publications for %d years (last: %d). " +
                "This was an active area that researchers seem to have abandoned.",
                label, timeline.getPeakCount(), timeline.getPeakYear(),
                yearsSince, timeline.getLatestYear()),
            0.8,
            collectPaperIds(context, papers)
        );

        GapProvenance provenance = new GapProvenance(NAME, VERSION,
            String.format("%d papers in cluster '%s'", papers.size(), label));
        provenance.addStep("Analyzed publication timeline: " + timeline.getTotalPapers() + " total papers");
        provenance.addStep("Peak activity: " + timeline.getPeakCount() + " papers in " + timeline.getPeakYear());
        provenance.addStep("No publications since " + timeline.getLatestYear() + " (" + yearsSince + " years)");
        provenance.addStep("Classified as DORMANT temporal gap");
        provenance.addEvidence(collectPaperIds(context, papers));

        List<UncertaintyFactor> uncertainties = new ArrayList<>();
        uncertainties.add(new UncertaintyFactor(
            yearsSince + " years since last publication",
            UncertaintyFactor.Direction.STRENGTHENS,
            Math.min(1.0, yearsSince / 5.0)));

        if (timeline.getPeakCount() >= 5) {
            uncertainties.add(new UncertaintyFactor(
                "Was a significant research area (peak: " + timeline.getPeakCount() + " papers)",
                UncertaintyFactor.Direction.STRENGTHENS,
                0.7));
        }

        if (papers.size() < 5) {
            uncertainties.add(new UncertaintyFactor(
                "Small evidence base (" + papers.size() + " papers)",
                UncertaintyFactor.Direction.WEAKENS,
                0.4));
        }

        double confidence = Math.min(1.0, 0.5 + (yearsSince * 0.1) + (papers.size() * 0.02));

        ResearchGap gap = new ResearchGap()
            .gapId(gapId)
            .type(GapType.TEMPORAL_DORMANT)
            .title(title)
            .explanation(String.format(
                "The topic '%s' was actively researched (peaking at %d papers in %d) " +
                "but has had no new publications for %d years. This may represent an " +
                "abandoned research direction worth revisiting with new methods or data.",
                label, timeline.getPeakCount(), timeline.getPeakYear(), yearsSince))
            .detectionMethod("Publication timeline analysis with dormancy threshold of " + DORMANT_THRESHOLD_YEARS + " years")
            .confidenceExplanation(String.format(
                "High confidence due to clear publication gap of %d years after active period.", yearsSince))
            .confidence(confidence)
            .provenance(provenance)
            .temporalContext(timeline);

        gap.addEvidence(evidence);
        gap.setUncertaintyFactors(uncertainties);
        gap.setAffectedClusters(List.of(label));
        gap.setAffectedPaperIds(collectPaperIds(context, papers));

        return gap;
    }

    // ─── Declining Gap ──────────────────────────────────────────────────

    private ResearchGap createDecliningGap(KnowledgeContext context, String label,
                                            List<Publication> papers, GapTimeline timeline,
                                            double recentAvg, double peakAvg) {
        String gapId = "TEMPORAL_DECLINING_" + label.hashCode();
        String title = "Declining topic: " + truncate(label, 50);
        double declineRatio = recentAvg / peakAvg;

        GapEvidence evidence = new GapEvidence(
            String.format("Publication rate dropped from peak of %.0f papers/year (in %d) to %.1f papers/year recently (%.0f%% decline).",
                peakAvg, timeline.getPeakYear(), recentAvg, (1 - declineRatio) * 100),
            0.7,
            collectPaperIds(context, papers)
        );

        GapProvenance provenance = new GapProvenance(NAME, VERSION,
            String.format("%d papers in cluster '%s'", papers.size(), label));
        provenance.addStep("Peak: " + String.format("%.0f papers/year in %d", peakAvg, timeline.getPeakYear()));
        provenance.addStep("Recent: " + String.format("%.1f papers/year (%.0f%% decline)", recentAvg, (1 - declineRatio) * 100));
        provenance.addStep("Classified as DECLINING temporal gap");
        provenance.addEvidence(collectPaperIds(context, papers));

        List<UncertaintyFactor> uncertainties = new ArrayList<>();
        uncertainties.add(new UncertaintyFactor(
            String.format("%.0f%% decline from peak", (1 - declineRatio) * 100),
            UncertaintyFactor.Direction.STRENGTHENS,
            Math.min(1.0, (1 - declineRatio))));

        if (recentAvg > 0) {
            uncertainties.add(new UncertaintyFactor(
                "Some recent activity exists (" + String.format("%.1f papers/year", recentAvg) + ")",
                UncertaintyFactor.Direction.WEAKENS,
                0.3));
        }

        double confidence = Math.min(1.0, 0.3 + (1 - declineRatio) * 0.5 + (papers.size() * 0.01));

        ResearchGap gap = new ResearchGap()
            .gapId(gapId)
            .type(GapType.TEMPORAL_DECLINING)
            .title(title)
            .explanation(String.format(
                "Research activity in '%s' is declining. Publications dropped from a peak of " +
                "%.0f papers/year to %.1f papers/year (%.0f%% decline). This may indicate a " +
                "research direction that could be revived with fresh approaches.",
                label, peakAvg, recentAvg, (1 - declineRatio) * 100))
            .detectionMethod("Publication rate trend analysis comparing peak vs. recent 3-year average")
            .confidenceExplanation(String.format(
                "Moderate confidence — decline is measurable (%.0f%%) but topic is not fully dormant.",
                (1 - declineRatio) * 100))
            .confidence(confidence)
            .provenance(provenance)
            .temporalContext(timeline);

        gap.addEvidence(evidence);
        gap.setUncertaintyFactors(uncertainties);
        gap.setAffectedClusters(List.of(label));
        gap.setAffectedPaperIds(collectPaperIds(context, papers));

        return gap;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private List<Integer> collectPaperIds(KnowledgeContext context, List<Publication> papers) {
        List<Integer> ids = new ArrayList<>();
        for (Publication pub : papers) {
            for (LibraryEntry entry : context.getEntries()) {
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

    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) + "..." : s;
    }
}
