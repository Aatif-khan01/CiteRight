package com.citeright.ranking;

import com.citeright.model.Publication;
import com.citeright.search.KeywordExtractor;
import java.util.List;

/**
 * Ranks papers by how many query keywords appear in the title and abstract.
 * 
 * Demonstrates: POLYMORPHISM — implements RankingStrategy.
 */
public class RelevanceRanker implements RankingStrategy {

    private final KeywordExtractor keywordExtractor;

    public RelevanceRanker() {
        this.keywordExtractor = new KeywordExtractor();
    }

    @Override
    public double score(Publication paper, String originalQuery) {
        List<String> queryKeywords = keywordExtractor.extractAsList(originalQuery);
        if (queryKeywords.isEmpty()) return 0.0;

        String titleLower = (paper.getTitle() != null) ? paper.getTitle().toLowerCase() : "";
        String abstractLower = (paper.getAbstractText() != null) ? paper.getAbstractText().toLowerCase() : "";
        String combined = titleLower + " " + abstractLower;

        int matchCount = 0;
        int titleMatchCount = 0;

        for (String keyword : queryKeywords) {
            if (combined.contains(keyword.toLowerCase())) {
                matchCount++;
            }
            // Title matches are weighted higher
            if (titleLower.contains(keyword.toLowerCase())) {
                titleMatchCount++;
            }
        }

        // Prevent lower scores for long queries by capping the denominator
        double denominator = Math.min(8.0, queryKeywords.size());

        // Base score: percentage of keywords found
        double baseScore = (matchCount * 100.0) / denominator;

        // Bonus for title matches (title relevance is more important)
        double titleBonus = (titleMatchCount * 25.0) / denominator;

        return Math.min(100.0, baseScore + titleBonus);
    }

    @Override
    public String getStrategyName() {
        return "Keyword Relevance";
    }
}
