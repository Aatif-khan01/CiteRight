package com.citeright.service;

import com.citeright.model.*;

/**
 * Generates human-readable explanations for detected research gaps.
 *
 * Answers the 4 explainability questions:
 *   1. WHY is this a gap?
 *   2. HOW was it detected?
 *   3. What EVIDENCE supports it?
 *   4. How CONFIDENT are we?
 *
 * Also generates:
 *   - One-line summary for gap cards
 *   - Detailed narrative for gap detail view
 *   - Uncertainty explanation with ✔ strengths and ⚠ weaknesses
 *   - Provenance summary for traceability
 */
public class ResearchGapExplainer {

    /**
     * Generate a one-line summary suitable for gap cards.
     */
    public String generateCardSummary(ResearchGap gap) {
        if (gap.getType() == null) return gap.getTitle();

        return switch (gap.getType()) {
            case TOPIC -> String.format("Topic Gap — %d concepts shared across disconnected clusters (score: %.0f)",
                countSharedConcepts(gap), gap.getOpportunityScore());

            case METHODOLOGY_TRANSFER -> String.format("Method Transfer — '%s' could work in new domains (score: %.0f)",
                getFirstRecommendedMethod(gap), gap.getOpportunityScore());

            case TEMPORAL_DORMANT -> String.format("Dormant Topic — no publications since %d (score: %.0f)",
                gap.getTemporalContext() != null ? gap.getTemporalContext().getLatestYear() : 0,
                gap.getOpportunityScore());

            case TEMPORAL_DECLINING -> String.format("Declining Topic — publication rate falling (score: %.0f)",
                gap.getOpportunityScore());

            case INTERDISCIPLINARY -> String.format("Cross-pollination Gap — mature fields with %d bridge papers (score: %.0f)",
                countBridgePapers(gap), gap.getOpportunityScore());
        };
    }

    /**
     * Generate the 4-question explanation block.
     * Returns a structured multi-line explanation.
     */
    public String generateFullExplanation(ResearchGap gap) {
        StringBuilder sb = new StringBuilder();

        // WHY
        sb.append("## Why is this a gap?\n");
        sb.append(gap.getExplanation()).append("\n\n");

        // HOW
        sb.append("## How was it detected?\n");
        sb.append(gap.getDetectionMethod()).append("\n\n");

        // EVIDENCE
        sb.append("## What evidence supports it?\n");
        if (gap.getEvidence() != null && !gap.getEvidence().isEmpty()) {
            for (int i = 0; i < gap.getEvidence().size(); i++) {
                GapEvidence ev = gap.getEvidence().get(i);
                sb.append(String.format("%d. %s (confidence: %.0f%%)\n",
                    i + 1, ev.getStatement(), ev.getConfidence() * 100));
            }
        } else {
            sb.append("No structured evidence available.\n");
        }
        sb.append("\n");

        // CONFIDENCE
        sb.append("## How confident are we?\n");
        sb.append(String.format("Overall confidence: %.0f%%\n", gap.getConfidence() * 100));
        sb.append(gap.getConfidenceExplanation()).append("\n\n");

        // Uncertainty factors
        if (gap.getUncertaintyFactors() != null && !gap.getUncertaintyFactors().isEmpty()) {
            sb.append("### Uncertainty Factors\n");
            for (UncertaintyFactor uf : gap.getUncertaintyFactors()) {
                sb.append(uf.toDisplayString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generate a provenance summary string.
     */
    public String generateProvenanceSummary(ResearchGap gap) {
        if (gap.getProvenance() == null) {
            return "Provenance not available.";
        }
        return gap.getProvenance().toSummary();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private int countSharedConcepts(ResearchGap gap) {
        if (gap.getEvidence() == null || gap.getEvidence().isEmpty()) return 0;
        // Parse from evidence statement (pattern: "share N concepts")
        String stmt = gap.getEvidence().get(0).getStatement();
        try {
            int idx = stmt.indexOf("share ");
            if (idx >= 0) {
                String rest = stmt.substring(idx + 6);
                int spaceIdx = rest.indexOf(' ');
                return Integer.parseInt(rest.substring(0, spaceIdx));
            }
        } catch (Exception e) { /* ignore */ }
        return 0;
    }

    private String getFirstRecommendedMethod(ResearchGap gap) {
        if (gap.getRecommendedMethods() != null && !gap.getRecommendedMethods().isEmpty()) {
            return gap.getRecommendedMethods().get(0);
        }
        return "unknown";
    }

    private int countBridgePapers(ResearchGap gap) {
        // Extract from evidence statement
        if (gap.getEvidence() == null || gap.getEvidence().isEmpty()) return 0;
        String stmt = gap.getEvidence().get(0).getStatement();
        try {
            int idx = stmt.indexOf("bridge papers");
            if (idx > 0) {
                // Look backward for the number
                String prefix = stmt.substring(0, idx).trim();
                String[] parts = prefix.split("\\s+");
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (Exception e) { /* ignore */ }
        return 0;
    }
}
