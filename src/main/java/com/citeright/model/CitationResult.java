package com.citeright.model;

/**
 * Wrapper class that combines a Publication with its relevance score.
 * Used to display ranked search results to the user.
 * 
 * Demonstrates: COMPOSITION — CitationResult HAS-A Publication.
 */
public class CitationResult implements Comparable<CitationResult> {

    private Publication publication;
    private double relevanceScore;    // 0.0 to 100.0
    private double keywordMatchScore;
    private double citationScore;
    private double recencyScore;

    // NLP Engine scores (custom AI)
    private double semanticScore;      // Combined BM25 + Cosine
    private double bm25Score;          // Raw BM25 score
    private double cosineSimilarity;   // Raw cosine similarity

    // Quality analysis
    private String qualityLabel;       // "Strong" / "Good" / "Consider" / "Outdated"
    private java.util.List<String> qualityFlags;  // Quality warnings/badges

    // AI evidence (optional — requires Gemini)
    private String evidenceSnippet;    // Exact sentence supporting the claim
    private String supportStrength;    // "Strong Support" / "Partial" / "Contradicts"

    public CitationResult(Publication publication, double relevanceScore) {
        this.publication = publication;
        this.relevanceScore = relevanceScore;
        this.qualityFlags = new java.util.ArrayList<>();
    }

    // --- Getters and Setters ---

    public Publication getPublication() {
        return publication;
    }

    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public double getKeywordMatchScore() {
        return keywordMatchScore;
    }

    public void setKeywordMatchScore(double keywordMatchScore) {
        this.keywordMatchScore = keywordMatchScore;
    }

    public double getCitationScore() {
        return citationScore;
    }

    public void setCitationScore(double citationScore) {
        this.citationScore = citationScore;
    }

    public double getRecencyScore() {
        return recencyScore;
    }

    public void setRecencyScore(double recencyScore) {
        this.recencyScore = recencyScore;
    }

    public double getSemanticScore() { return semanticScore; }
    public void setSemanticScore(double semanticScore) { this.semanticScore = semanticScore; }

    public double getBm25Score() { return bm25Score; }
    public void setBm25Score(double bm25Score) { this.bm25Score = bm25Score; }

    public double getCosineSimilarity() { return cosineSimilarity; }
    public void setCosineSimilarity(double cosineSimilarity) { this.cosineSimilarity = cosineSimilarity; }

    public String getQualityLabel() { return qualityLabel; }
    public void setQualityLabel(String qualityLabel) { this.qualityLabel = qualityLabel; }

    public java.util.List<String> getQualityFlags() { return qualityFlags; }
    public void setQualityFlags(java.util.List<String> qualityFlags) { this.qualityFlags = qualityFlags; }

    public String getEvidenceSnippet() { return evidenceSnippet; }
    public void setEvidenceSnippet(String evidenceSnippet) { this.evidenceSnippet = evidenceSnippet; }

    public String getSupportStrength() { return supportStrength; }
    public void setSupportStrength(String supportStrength) { this.supportStrength = supportStrength; }

    /**
     * Returns the relevance as a percentage string (e.g., "94%").
     */
    public String getRelevancePercentage() {
        return String.format("%.0f%%", relevanceScore);
    }

    /**
     * Returns a strength label based on relevance score.
     */
    public String getStrengthLabel() {
        if (relevanceScore >= 80) return "Strong";
        if (relevanceScore >= 60) return "Good";
        if (relevanceScore >= 40) return "Moderate";
        return "Weak";
    }

    /**
     * Natural ordering: higher relevance score first.
     */
    @Override
    public int compareTo(CitationResult other) {
        return Double.compare(other.relevanceScore, this.relevanceScore);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (Relevance: %s)",
                getStrengthLabel(), publication.getTitle(), getRelevancePercentage());
    }
}
