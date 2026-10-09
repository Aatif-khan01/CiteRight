package com.citeright.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured evidence for a research gap.
 *
 * Each piece of evidence has a human-readable statement, a confidence level,
 * the papers that support it, and a venue quality indicator.
 *
 * This replaces simple string-based evidence with a traceable, structured format
 * that supports the 4-question explainability framework (Why/How/Evidence/Confidence).
 */
public class GapEvidence {

    /** Human-readable evidence statement */
    private String statement;

    /** Confidence in this specific piece of evidence (0–1.0) */
    private double confidence;

    /** Paper IDs that support this evidence statement */
    private List<Integer> supportingPaperIds;

    /** Average venue quality of supporting papers (0–1.0) */
    private double venueQuality;

    public GapEvidence() {
        this.supportingPaperIds = new ArrayList<>();
        this.confidence = 1.0;
        this.venueQuality = 0.5;
    }

    public GapEvidence(String statement, double confidence, List<Integer> supportingPaperIds) {
        this.statement = statement;
        this.confidence = confidence;
        this.supportingPaperIds = supportingPaperIds != null ? new ArrayList<>(supportingPaperIds) : new ArrayList<>();
        this.venueQuality = 0.5;
    }

    public GapEvidence(String statement, double confidence, List<Integer> supportingPaperIds, double venueQuality) {
        this(statement, confidence, supportingPaperIds);
        this.venueQuality = venueQuality;
    }

    // Getters and setters
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<Integer> getSupportingPaperIds() { return supportingPaperIds; }
    public void setSupportingPaperIds(List<Integer> supportingPaperIds) { this.supportingPaperIds = supportingPaperIds; }

    public double getVenueQuality() { return venueQuality; }
    public void setVenueQuality(double venueQuality) { this.venueQuality = venueQuality; }

    @Override
    public String toString() {
        return statement + " (confidence: " + String.format("%.2f", confidence)
             + ", papers: " + supportingPaperIds.size() + ")";
    }
}
