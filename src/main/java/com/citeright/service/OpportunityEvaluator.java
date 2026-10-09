package com.citeright.service;

import com.citeright.model.*;
import com.citeright.nlp.VenueQualityProvider;

import java.util.*;

/**
 * Evaluates raw gaps and promotes them to Research Opportunities.
 *
 * This is the critical distinction:
 *   Gap = something missing in the literature
 *   Opportunity = a gap that is actually worth pursuing
 *
 * Evaluation criteria:
 *   1. Opportunity Score (composite of novelty, evidence, trend, confidence)
 *   2. Evidence Strength (venue quality + citation impact + paper count)
 *   3. Research Readiness (% of key papers the user already owns)
 *   4. Severity classification (LOW / MEDIUM / HIGH)
 *
 * Only gaps scoring above the promotion threshold become opportunities.
 */
public class OpportunityEvaluator {

    /** Minimum opportunity score to promote a gap to an opportunity */
    private static final double PROMOTION_THRESHOLD = 20.0;

    private final OpportunityScoreCalculator scoreCalculator;

    public OpportunityEvaluator() {
        this.scoreCalculator = new OpportunityScoreCalculator();
    }

    /**
     * Evaluate a list of raw gaps and promote qualifying ones to opportunities.
     *
     * @param gaps    raw gaps from analyzers
     * @param context the knowledge context for readiness computation
     * @return all gaps with scores computed, qualifying ones marked as opportunities
     */
    public List<ResearchGap> evaluate(List<ResearchGap> gaps, KnowledgeContext context) {
        if (gaps == null || gaps.isEmpty()) return Collections.emptyList();

        List<ResearchGap> evaluated = new ArrayList<>();

        for (ResearchGap gap : gaps) {
            // 1. Compute opportunity score
            double score = scoreCalculator.computeScore(gap, context);
            gap.setOpportunityScore(score);

            // 2. Compute evidence strength
            EvidenceStrength strength = computeEvidenceStrength(gap, context);
            gap.setEvidenceStrength(strength);

            // 3. Compute research readiness
            double readiness = computeReadiness(gap, context);
            gap.setResearchReadiness(readiness);

            // 4. Set severity from score
            gap.setSeverity(GapSeverity.fromScore(score));

            // 5. Promote to opportunity if above threshold
            gap.setOpportunity(score >= PROMOTION_THRESHOLD);

            evaluated.add(gap);
        }

        // Sort by opportunity score descending
        evaluated.sort((a, b) -> Double.compare(b.getOpportunityScore(), a.getOpportunityScore()));

        long opportunities = evaluated.stream().filter(ResearchGap::isOpportunity).count();
        System.out.printf("[OpportunityEvaluator] Evaluated %d gaps → %d opportunities (threshold: %.0f)%n",
            gaps.size(), opportunities, PROMOTION_THRESHOLD);

        return evaluated;
    }

    /**
     * Compute evidence strength from supporting papers' venue quality and citations.
     */
    private EvidenceStrength computeEvidenceStrength(ResearchGap gap, KnowledgeContext context) {
        List<Integer> paperIds = gap.getAffectedPaperIds();
        if (paperIds == null || paperIds.isEmpty()) return EvidenceStrength.LOW;

        double totalVenueQuality = 0;
        int totalCitations = 0;
        int count = 0;

        for (int id : paperIds) {
            Double vq = context.getVenueQualities().get(id);
            if (vq != null) totalVenueQuality += vq;

            Integer citations = context.getCitationCounts().get(id);
            if (citations != null) totalCitations += citations;
            count++;
        }

        if (count == 0) return EvidenceStrength.LOW;

        // Composite evidence score
        double avgVenueQuality = totalVenueQuality / count;
        double citationFactor = Math.min(1.0, Math.log10(totalCitations + 1) / 3.0);
        double paperCountFactor = Math.min(1.0, count / 20.0);

        double evidenceScore = 0.40 * avgVenueQuality + 0.30 * citationFactor + 0.30 * paperCountFactor;

        return EvidenceStrength.fromScore(evidenceScore);
    }

    /**
     * Compute research readiness — what % of key papers the user already has.
     * For V1, this is simply the ratio of affected papers that are in the library.
     */
    private double computeReadiness(ResearchGap gap, KnowledgeContext context) {
        List<Integer> affected = gap.getAffectedPaperIds();
        if (affected == null || affected.isEmpty()) return 0.0;

        // All affected papers are already in the library (since they came from it)
        // Readiness is 1.0 for V1; V2 will add "missing papers" from external sources
        gap.setMissingPaperCount(0);
        return 1.0;
    }
}
