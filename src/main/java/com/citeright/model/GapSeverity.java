package com.citeright.model;

/**
 * Severity level of a detected research gap.
 * Derived from the opportunity score thresholds.
 */
public enum GapSeverity {

    /** Score 0–40: Minor gap, possibly noise */
    LOW("Low", "#6b7280"),

    /** Score 40–75: Notable gap worth investigating */
    MEDIUM("Medium", "#f59e0b"),

    /** Score 75–100: Major gap with strong evidence */
    HIGH("High", "#ef4444");

    private final String displayName;
    private final String color;

    GapSeverity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    /** Compute severity from an opportunity score (0–100) */
    public static GapSeverity fromScore(double score) {
        if (score >= 75) return HIGH;
        if (score >= 40) return MEDIUM;
        return LOW;
    }
}
