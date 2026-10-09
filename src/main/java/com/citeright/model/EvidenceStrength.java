package com.citeright.model;

/**
 * Quality level of the evidence supporting a detected research gap.
 *
 * Computed from: number of supporting papers, average venue quality,
 * average citation count, and recency of evidence.
 *
 * This is NOT the same as confidence — confidence measures how certain
 * the detection is, while evidence strength measures how trustworthy
 * the supporting data is. Two papers from Nature carry more weight
 * than two papers from unknown venues.
 */
public enum EvidenceStrength {

    /** Few papers, low-impact venues, or very old evidence */
    LOW("Low", "#9ca3af"),

    /** Moderate paper count with decent venue quality */
    MEDIUM("Medium", "#60a5fa"),

    /** Strong paper count from reputable venues with good citation counts */
    HIGH("High", "#34d399");

    private final String displayName;
    private final String color;

    EvidenceStrength(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    /**
     * Compute evidence strength from a composite evidence score (0–1.0).
     * Score combines: paper count factor, venue quality, citation impact, recency.
     */
    public static EvidenceStrength fromScore(double evidenceScore) {
        if (evidenceScore >= 0.7) return HIGH;
        if (evidenceScore >= 0.4) return MEDIUM;
        return LOW;
    }
}
