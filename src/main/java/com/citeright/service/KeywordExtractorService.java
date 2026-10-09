package com.citeright.service;

import com.citeright.nlp.TextPreprocessor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts keywords from paper title + abstract using TF-IDF scoring.
 * Used for automatic tag suggestions when importing papers.
 */
public class KeywordExtractorService {

    // Common academic stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "study", "paper", "results", "method", "approach", "proposed", "using",
        "based", "analysis", "show", "research", "data", "model", "system",
        "used", "new", "present", "work", "also", "two", "first", "one",
        "however", "may", "well", "can", "provide", "different", "important",
        "several", "including", "found", "high", "use", "number", "large",
        "small", "three", "much", "many", "various", "general", "specific"
    );

    /**
     * Extract top N keywords from text (title + abstract).
     * Returns keywords sorted by relevance score.
     */
    public List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        List<String> tokens = TextPreprocessor.tokenize(text.toLowerCase());

        // Count term frequencies
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String token : tokens) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            freq.merge(token, 1, Integer::sum);
        }

        // Score: frequency * length bonus (longer words are usually more specific)
        return freq.entrySet().stream()
            .sorted((a, b) -> {
                double scoreA = a.getValue() * (1 + Math.log(a.getKey().length()));
                double scoreB = b.getValue() * (1 + Math.log(b.getKey().length()));
                return Double.compare(scoreB, scoreA);
            })
            .limit(topN)
            .map(Map.Entry::getKey)
            .map(k -> k.substring(0, 1).toUpperCase() + k.substring(1)) // capitalize
            .collect(Collectors.toList());
    }

    /** Extract top 5 keywords — convenience method */
    public List<String> extractKeywords(String text) {
        return extractKeywords(text, 5);
    }
}
