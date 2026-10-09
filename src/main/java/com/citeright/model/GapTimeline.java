package com.citeright.model;

import java.util.*;

/**
 * Temporal context for a research gap — answers "Why now?"
 *
 * Contains year-by-year publication history, peak activity, trend direction,
 * and a human-readable description of how the research topic evolved.
 */
public class GapTimeline {

    /** Papers per year in this gap's topic area */
    private Map<Integer, Integer> yearlyPaperCounts;

    /** Year with the most publications */
    private int peakYear;

    /** Paper count at peak */
    private int peakCount;

    /** Most recent publication year */
    private int latestYear;

    /** Trend direction */
    private String trendDirection; // "GROWING", "STABLE", "DECLINING", "DORMANT"

    /** Human-readable trend description */
    private String trendDescription;

    public GapTimeline() {
        this.yearlyPaperCounts = new TreeMap<>();
    }

    /**
     * Build a timeline from a list of publication years.
     */
    public static GapTimeline fromYears(List<Integer> years) {
        GapTimeline timeline = new GapTimeline();
        if (years == null || years.isEmpty()) {
            timeline.trendDirection = "DORMANT";
            timeline.trendDescription = "No publications found";
            return timeline;
        }

        // Count papers per year
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int year : years) {
            counts.merge(year, 1, Integer::sum);
        }
        timeline.yearlyPaperCounts = counts;

        // Find peak
        int maxCount = 0;
        int maxYear = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxYear = entry.getKey();
            }
        }
        timeline.peakYear = maxYear;
        timeline.peakCount = maxCount;

        // Find latest
        timeline.latestYear = Collections.max(counts.keySet());

        // Determine trend
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int yearsSinceLatest = currentYear - timeline.latestYear;

        if (yearsSinceLatest >= 3) {
            timeline.trendDirection = "DORMANT";
            timeline.trendDescription = String.format(
                "Peak activity in %d with %d papers. Last published in %d (%d years ago).",
                maxYear, maxCount, timeline.latestYear, yearsSinceLatest);
        } else {
            // Check recent trend (last 3 years vs peak)
            int recentCount = 0;
            for (int y = currentYear - 2; y <= currentYear; y++) {
                recentCount += counts.getOrDefault(y, 0);
            }
            double recentAvg = recentCount / 3.0;
            double peakAvg = maxCount;

            if (recentAvg >= peakAvg * 0.8) {
                timeline.trendDirection = "GROWING";
                timeline.trendDescription = String.format(
                    "Active research area. Recent average: %.1f papers/year. Peak: %d papers in %d.",
                    recentAvg, maxCount, maxYear);
            } else if (recentAvg >= peakAvg * 0.4) {
                timeline.trendDirection = "STABLE";
                timeline.trendDescription = String.format(
                    "Moderate activity. Recent average: %.1f papers/year. Peak: %d papers in %d.",
                    recentAvg, maxCount, maxYear);
            } else {
                timeline.trendDirection = "DECLINING";
                timeline.trendDescription = String.format(
                    "Declining activity. Recent average: %.1f papers/year, down from peak of %d in %d.",
                    recentAvg, maxCount, maxYear);
            }
        }

        return timeline;
    }

    // Getters and setters
    public Map<Integer, Integer> getYearlyPaperCounts() { return yearlyPaperCounts; }
    public void setYearlyPaperCounts(Map<Integer, Integer> yearlyPaperCounts) { this.yearlyPaperCounts = yearlyPaperCounts; }

    public int getPeakYear() { return peakYear; }
    public void setPeakYear(int peakYear) { this.peakYear = peakYear; }

    public int getPeakCount() { return peakCount; }
    public void setPeakCount(int peakCount) { this.peakCount = peakCount; }

    public int getLatestYear() { return latestYear; }
    public void setLatestYear(int latestYear) { this.latestYear = latestYear; }

    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }

    public String getTrendDescription() { return trendDescription; }
    public void setTrendDescription(String trendDescription) { this.trendDescription = trendDescription; }

    /** Total number of papers across all years */
    public int getTotalPapers() {
        return yearlyPaperCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
