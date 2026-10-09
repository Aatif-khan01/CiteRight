package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Computes the composite opportunity score for a research gap.
 *
 * Formula:
 *   opportunityScore = w_novelty  × noveltySignal
 *                    + w_evidence × evidenceSignal
 *                    + w_trend    × trendSignal
 *                    + w_confidence × confidenceSignal
 *
 * All signals normalized to 0–1.0 range, then scaled to 0–100.
 *
 * Weights:
 *   novelty    = 0.35 (how unique is this gap?)
 *   evidence   = 0.25 (how strong is the supporting data?)
 *   trend      = 0.20 (is this area growing or declining?)
 *   confidence = 0.20 (how certain is the detection?)
 */
public class OpportunityScoreCalculator {

    private static final double W_NOVELTY = 0.35;
    private static final double W_EVIDENCE = 0.25;
    private static final double W_TREND = 0.20;
    private static final double W_CONFIDENCE = 0.20;

    /**
     * Compute composite score (0–100) for a single gap.
     */
    public double computeScore(ResearchGap gap, KnowledgeContext context) {
        double noveltySignal = computeNoveltySignal(gap, context);
        double evidenceSignal = computeEvidenceSignal(gap, context);
        double trendSignal = computeTrendSignal(gap);
        double confidenceSignal = gap.getConfidence();

        double raw = W_NOVELTY * noveltySignal
                   + W_EVIDENCE * evidenceSignal
                   + W_TREND * trendSignal
                   + W_CONFIDENCE * confidenceSignal;

        return Math.min(100.0, raw * 100.0);
    }

    /**
     * Novelty signal — how underexplored is this gap?
     * Based on: number of affected papers (fewer = more novel),
     * cross-cluster connection density, gap type.
     */
    private double computeNoveltySignal(ResearchGap gap, KnowledgeContext context) {
        int totalPapers = context.getPaperCount();
        if (totalPapers == 0) return 0.5;

        // For topic/interdisciplinary gaps: fewer cross-connections = more novel
        int affectedCount = gap.getAffectedPaperIds() != null ? gap.getAffectedPaperIds().size() : 0;

        // Novelty is inversely proportional to the fraction of affected papers
        // that already connect the gap topics
        double coverageRatio = (double) affectedCount / totalPapers;

        // Type-specific novelty boost
        double typeBoost = switch (gap.getType()) {
            case TOPIC -> 0.6;                 // Topic gaps are common
            case METHODOLOGY_TRANSFER -> 0.8;  // Method transfers are rarer
            case TEMPORAL_DORMANT -> 0.7;       // Dormant topics are moderately novel
            case TEMPORAL_DECLINING -> 0.5;     // Declining topics are known
            case INTERDISCIPLINARY -> 0.9;      // Interdisciplinary gaps are very novel
        };

        return Math.min(1.0, typeBoost * (1.0 - coverageRatio * 0.5));
    }

    /**
     * Evidence signal — how well-supported is this gap detection?
     * Based on: number of evidence items, average confidence, paper count.
     */
    private double computeEvidenceSignal(ResearchGap gap, KnowledgeContext context) {
        List<GapEvidence> evidence = gap.getEvidence();
        if (evidence == null || evidence.isEmpty()) return 0.3;

        // Average evidence confidence
        double avgConfidence = evidence.stream()
            .mapToDouble(GapEvidence::getConfidence)
            .average().orElse(0.5);

        // Number of supporting papers
        int totalSupportingPapers = 0;
        for (GapEvidence ev : evidence) {
            if (ev.getSupportingPaperIds() != null) {
                totalSupportingPapers += ev.getSupportingPaperIds().size();
            }
        }
        double paperFactor = Math.min(1.0, totalSupportingPapers / 20.0);

        // Venue quality from context
        double avgVenueQuality = 0.5;
        if (gap.getAffectedPaperIds() != null && !gap.getAffectedPaperIds().isEmpty()) {
            double sum = 0;
            int count = 0;
            for (int id : gap.getAffectedPaperIds()) {
                Double vq = context.getVenueQualities().get(id);
                if (vq != null) { sum += vq; count++; }
            }
            if (count > 0) avgVenueQuality = sum / count;
        }

        return 0.40 * avgConfidence + 0.30 * paperFactor + 0.30 * avgVenueQuality;
    }

    /**
     * Trend signal — is this research area growing or declining?
     * Growing areas score higher (more opportunity).
     * Dormant areas also score high (revival opportunity).
     */
    private double computeTrendSignal(ResearchGap gap) {
        GapTimeline timeline = gap.getTemporalContext();
        if (timeline == null || timeline.getTrendDirection() == null) return 0.5;

        return switch (timeline.getTrendDirection()) {
            case "GROWING" -> 0.9;    // Active area = high opportunity
            case "STABLE" -> 0.6;     // Stable area = moderate opportunity
            case "DECLINING" -> 0.4;  // Declining = lower but still interesting
            case "DORMANT" -> 0.7;    // Dormant = revival opportunity
            default -> 0.5;
        };
    }
}
