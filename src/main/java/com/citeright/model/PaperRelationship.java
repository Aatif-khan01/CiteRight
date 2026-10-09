package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Represents a relationship between two papers in the library.
 * Relationships can originate from user curation, AI inference, or automatic detection.
 * The confidence field tracks certainty (0-1), and source tracks provenance.
 */
public class PaperRelationship {

    /**
     * Tracks the origin of a paper relationship.
     */
    public enum Source {
        USER,           // Manually created by the researcher
        AI_SUGGESTED,   // Inferred by Gemini, awaiting confirmation
        AI_CONFIRMED,   // AI-suggested and confirmed by the researcher
        AUTO_DETECTED   // Algorithmically detected (citation chain, methodology overlap)
    }
    private int id;
    private int sourcePaperId;
    private int targetPaperId;
    private String relationshipType;
    private String reasoning;
    private double confidence = 1.0;   // 0.0–1.0, defaults to 1.0 for user-created
    private Source source = Source.USER;
    private boolean dismissed = false; // true if user dismissed an AI suggestion
    private LocalDateTime createdAt;

    public PaperRelationship() {}

    public PaperRelationship(int sourcePaperId, int targetPaperId, String relationshipType, String reasoning) {
        this.sourcePaperId = sourcePaperId;
        this.targetPaperId = targetPaperId;
        this.relationshipType = relationshipType;
        this.reasoning = reasoning;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSourcePaperId() { return sourcePaperId; }
    public void setSourcePaperId(int sourcePaperId) { this.sourcePaperId = sourcePaperId; }

    public int getTargetPaperId() { return targetPaperId; }
    public void setTargetPaperId(int targetPaperId) { this.targetPaperId = targetPaperId; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
