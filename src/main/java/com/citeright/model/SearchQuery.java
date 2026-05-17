package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Represents a search query made by the user.
 * Stores the original sentence, extracted keywords, and search metadata.
 */
public class SearchQuery {

    private int id;
    private String originalSentence;
    private String extractedKeywords;
    private int resultCount;
    private LocalDateTime searchedAt;

    public SearchQuery() {
        this.searchedAt = LocalDateTime.now();
    }

    public SearchQuery(String originalSentence, String extractedKeywords) {
        this.originalSentence = originalSentence;
        this.extractedKeywords = extractedKeywords;
        this.searchedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOriginalSentence() {
        return originalSentence;
    }

    public void setOriginalSentence(String originalSentence) {
        this.originalSentence = originalSentence;
    }

    public String getExtractedKeywords() {
        return extractedKeywords;
    }

    public void setExtractedKeywords(String extractedKeywords) {
        this.extractedKeywords = extractedKeywords;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(LocalDateTime searchedAt) {
        this.searchedAt = searchedAt;
    }

    @Override
    public String toString() {
        return String.format("Query: \"%s\" → Keywords: [%s] (%d results)",
                originalSentence, extractedKeywords, resultCount);
    }
}
