package com.citeright.ranking;

import com.citeright.model.Publication;

/**
 * Ranks papers by their citation count.
 * Highly cited papers are considered stronger references.
 * Uses logarithmic scaling so extremely cited papers don't dominate.
 * 
 * Demonstrates: POLYMORPHISM — implements RankingStrategy.
 */
public class CitationCountRanker implements RankingStrategy {

    // Papers with this many citations get a score of ~90
    private static final int HIGH_CITATION_THRESHOLD = 1000;

    @Override
    public double score(Publication paper, String originalQuery) {
        int citations = paper.getCitationCount();

        if (citations <= 0) return 5.0; // Minimum score for uncited papers

        // Logarithmic scaling: log(citations) / log(threshold) * 90
        // This gives: 10 citations → ~30, 100 → ~60, 1000 → ~90, 10000 → ~100
        double logScore = (Math.log10(citations) / Math.log10(HIGH_CITATION_THRESHOLD)) * 90.0;

        return Math.min(100.0, Math.max(5.0, logScore));
    }

    @Override
    public String getStrategyName() {
        return "Citation Count";
    }
}
