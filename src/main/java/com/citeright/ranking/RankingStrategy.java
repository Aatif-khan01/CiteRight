package com.citeright.ranking;

import com.citeright.model.Publication;

/**
 * Interface for ranking strategies.
 * 
 * Demonstrates: STRATEGY PATTERN + POLYMORPHISM
 * Different ranking algorithms can be plugged in and combined.
 */
public interface RankingStrategy {

    /**
     * Scores a publication based on this strategy's criteria.
     *
     * @param paper         The publication to score
     * @param originalQuery The user's original search query
     * @return A score from 0.0 to 100.0
     */
    double score(Publication paper, String originalQuery);

    /**
     * Returns the name of this ranking strategy.
     */
    String getStrategyName();
}
