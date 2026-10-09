package com.citeright.ranking;

import com.citeright.model.Publication;
import java.time.Year;

/**
 * Ranks papers by how recent they are.
 * Newer papers score higher, with a configurable decay rate.
 * 
 * Demonstrates: POLYMORPHISM — implements RankingStrategy.
 */
public class RecencyRanker implements RankingStrategy {

    private static final int DECAY_YEARS = 15; // Score drops to ~50% after this many years

    @Override
    public double score(Publication paper, String originalQuery) {
        int currentYear = Year.now().getValue();
        int paperYear = paper.getYear();

        if (paperYear <= 0) return 30.0; // Default for unknown year

        int age = currentYear - paperYear;

        if (age < 0) age = 0;   // Future publications (pre-prints)
        if (age == 0) return 100.0; // This year
        if (age <= 2) return 95.0;  // Very recent (1-2 years)
        if (age <= 5) return 85.0;  // Recent (3-5 years)

        // Exponential decay for older papers
        // Score = 80 * e^(-age/decayYears)
        double score = 80.0 * Math.exp(-(double) age / DECAY_YEARS);

        return Math.max(10.0, score); // Minimum score of 10 (old papers still have value)
    }

    @Override
    public String getStrategyName() {
        return "Recency";
    }
}
