package com.citeright.nlp;

import com.citeright.model.Publication;
import java.util.*;

/**
 * Research Quality Analyzer.
 * Evaluates the quality and appropriateness of search results using
 * pure algorithmic analysis — no external AI required.
 * 
 * Checks for:
 * - Outdated references (papers too old for a fast-moving field)
 * - Citation impact relative to age
 * - Diversity of sources (journals, authors)
 * - Field velocity (how fast the research area is moving)
 * 
 * Demonstrates: Custom analytical AI using statistical heuristics.
 */
public class QualityAnalyzer {

    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private static final int OUTDATED_THRESHOLD_YEARS = 5;
    private static final int HIGHLY_OUTDATED_THRESHOLD_YEARS = 10;

    /**
     * Analyzes a single publication and returns quality flags.
     *
     * @param paper The publication to analyze
     * @return List of quality flags/warnings
     */
    public List<String> analyzeQuality(Publication paper) {
        List<String> flags = new ArrayList<>();

        int age = CURRENT_YEAR - paper.getYear();

        // Age-based analysis
        if (paper.getYear() > 0) {
            if (age >= HIGHLY_OUTDATED_THRESHOLD_YEARS) {
                flags.add("⚠ Published " + age + " years ago — may be significantly outdated");
            } else if (age >= OUTDATED_THRESHOLD_YEARS) {
                flags.add("⚠ Published " + age + " years ago — check for newer research");
            } else if (age <= 1) {
                flags.add("🆕 Recently published (" + paper.getYear() + ")");
            }
        }

        // Citation impact relative to age
        if (paper.getYear() > 0 && paper.getCitationCount() > 0) {
            double citationsPerYear = (double) paper.getCitationCount() / Math.max(1, age);
            if (citationsPerYear >= 50) {
                flags.add("🔥 High impact — " + String.format("%.0f", citationsPerYear) + " citations/year");
            } else if (citationsPerYear >= 20) {
                flags.add("📊 Good impact — " + String.format("%.0f", citationsPerYear) + " citations/year");
            } else if (paper.getCitationCount() == 0 && age >= 2) {
                flags.add("📉 Zero citations after " + age + " years");
            }
        }

        // Missing metadata warnings
        if (paper.getAbstractText() == null || paper.getAbstractText().isEmpty()) {
            flags.add("📄 No abstract available — harder to verify relevance");
        }

        if (paper.getDoi() == null || paper.getDoi().isEmpty()) {
            flags.add("🔗 No DOI — may be a preprint or non-peer-reviewed");
        }

        return flags;
    }

    /**
     * Computes an overall quality label for a publication.
     * 
     * @param paper The publication
     * @return "Strong", "Good", "Consider", or "Outdated"
     */
    public String getQualityLabel(Publication paper) {
        int age = CURRENT_YEAR - paper.getYear();
        int citations = paper.getCitationCount();

        if (age >= HIGHLY_OUTDATED_THRESHOLD_YEARS && citations < 100) {
            return "Outdated";
        }

        if (age >= OUTDATED_THRESHOLD_YEARS && citations < 20) {
            return "Outdated";
        }

        // High citation count papers are always considered high quality
        if (citations >= 100) {
            return "Strong";
        }

        double citationsPerYear = (double) citations / Math.max(1, age);

        if (citationsPerYear >= 20 || (age <= 2 && citations >= 5)) {
            return "Strong";
        }

        if (citationsPerYear >= 5 || age <= 3) {
            return "Good";
        }

        return "Consider";
    }

    /**
     * Analyzes the overall result set for diversity and field velocity.
     * Returns insights about the collection of results as a whole.
     *
     * @param papers List of publications from search results
     * @return List of insight strings
     */
    public List<String> analyzeResultSet(List<Publication> papers) {
        List<String> insights = new ArrayList<>();
        if (papers.isEmpty()) return insights;

        // Field Recency Analysis
        int recentCount = 0;       // Papers from last 3 years
        int oldCount = 0;          // Papers older than 5 years
        int totalWithYear = 0;

        for (Publication p : papers) {
            if (p.getYear() > 0) {
                totalWithYear++;
                int age = CURRENT_YEAR - p.getYear();
                if (age <= 3) recentCount++;
                if (age >= 5) oldCount++;
            }
        }

        if (totalWithYear > 0) {
            double recentPercent = (recentCount * 100.0) / totalWithYear;
            if (recentPercent >= 70) {
                insights.add("⚡ Fast-moving field — " + String.format("%.0f%%", recentPercent) 
                        + " of results are from the last 3 years");
            } else if (recentPercent <= 30 && oldCount > totalWithYear / 2) {
                insights.add("📚 Mature field — most research is from 5+ years ago. Consider searching for recent reviews.");
            }
        }

        // Journal Diversity Analysis
        Map<String, Integer> venueCounts = new HashMap<>();
        for (Publication p : papers) {
            if (p.getVenue() != null && !p.getVenue().isEmpty()) {
                venueCounts.merge(p.getVenue().toLowerCase(), 1, Integer::sum);
            }
        }

        if (!venueCounts.isEmpty()) {
            String topVenue = Collections.max(venueCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
            int topCount = venueCounts.get(topVenue);
            if (topCount >= papers.size() / 2 && papers.size() > 3) {
                insights.add("📰 Low diversity — " + topCount + " of " + papers.size() 
                        + " results are from the same journal");
            }
        }

        // Author Diversity Analysis
        Map<String, Integer> authorCounts = new HashMap<>();
        for (Publication p : papers) {
            if (p.getAuthors() != null) {
                for (var author : p.getAuthors()) {
                    if (author.getName() != null) {
                        authorCounts.merge(author.getName().toLowerCase(), 1, Integer::sum);
                    }
                }
            }
        }

        if (!authorCounts.isEmpty()) {
            String topAuthor = Collections.max(authorCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
            int topAuthorCount = authorCounts.get(topAuthor);
            if (topAuthorCount >= 3 && papers.size() > 5) {
                insights.add("👤 Author concentration — \"" + capitalizeFirst(topAuthor) 
                        + "\" appears in " + topAuthorCount + " results. Consider diversifying sources.");
            }
        }

        // Citation Statistics
        int totalCitations = 0;
        int maxCitations = 0;
        for (Publication p : papers) {
            totalCitations += p.getCitationCount();
            maxCitations = Math.max(maxCitations, p.getCitationCount());
        }

        if (papers.size() > 0) {
            double avgCitations = (double) totalCitations / papers.size();
            if (avgCitations >= 100) {
                insights.add("📊 High-impact area — average " + String.format("%.0f", avgCitations) 
                        + " citations per paper");
            }
        }

        return insights;
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
