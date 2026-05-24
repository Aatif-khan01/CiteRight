package com.citeright.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Abstract base class representing an academic publication.
 * 
 * Demonstrates: ABSTRACTION + ENCAPSULATION
 * - Abstract class with common fields shared by all publication types
 * - Private fields with public getters/setters (encapsulation)
 * - Abstract method getPublicationType() for polymorphic behavior
 */
public abstract class Publication {

    // Encapsulated fields — private access
    private String paperId;
    private String title;
    private List<Author> authors;
    private int year;
    private String abstractText;
    private int citationCount;
    private String doi;
    private String url;
    private String venue; // Journal name or conference name
    private List<Tag> tags; // User-assigned tags
    private boolean inLibrary; // Transient — whether this paper is in user's library
    private float[] embedding; // Dense semantic vector (e.g. BGE-M3 1024-dimensional)

    // Constructor
    public Publication() {
        this.authors = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    public Publication(String paperId, String title, int year) {
        this.paperId = paperId;
        this.title = title;
        this.year = year;
        this.authors = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    // Abstract method — each subclass must implement
    public abstract String getPublicationType();

    // --- Tags and Library State ---

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public void addTag(Tag tag) {
        if (this.tags == null) this.tags = new ArrayList<>();
        this.tags.add(tag);
    }

    public boolean isInLibrary() {
        return inLibrary;
    }

    public void setInLibrary(boolean inLibrary) {
        this.inLibrary = inLibrary;
    }

    // --- Getters and Setters (Encapsulation) ---

    public String getPaperId() {
        return paperId;
    }

    public void setPaperId(String paperId) {
        this.paperId = paperId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    public void setAuthors(List<Author> authors) {
        this.authors = authors != null ? authors : new ArrayList<>();
    }

    public void addAuthor(Author author) {
        if (this.authors == null) {
            this.authors = new ArrayList<>();
        }
        this.authors.add(author);
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public int getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(int citationCount) {
        this.citationCount = citationCount;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    /**
     * Returns a formatted string of all author names.
     * Example: "Smith, J., Johnson, A., Williams, B."
     */
    public String getAuthorsFormatted() {
        if (authors == null || authors.isEmpty()) {
            return "Unknown Authors";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            sb.append(authors.get(i).getName());
            if (i < authors.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Returns shortened author list (e.g., "Litjens et al.") for display.
     */
    public String getAuthorsShort() {
        if (authors == null || authors.isEmpty()) {
            return "Unknown";
        }
        if (authors.size() == 1) {
            return authors.get(0).getName();
        }
        if (authors.size() == 2) {
            return authors.get(0).getName() + " & " + authors.get(1).getName();
        }
        return authors.get(0).getName() + " et al.";
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%d) - %s - Citations: %d",
                getPublicationType(), title, year, getAuthorsShort(), citationCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Publication that = (Publication) o;
        return Objects.equals(paperId, that.paperId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paperId);
    }
}
