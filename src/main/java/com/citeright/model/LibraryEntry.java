package com.citeright.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Publication with library-specific metadata.
 * This is what the user sees in their personal library.
 * 
 * Demonstrates: COMPOSITION — LibraryEntry HAS-A Publication.
 */
public class LibraryEntry {

    /**
     * Enum for tracking reading progress.
     */
    public enum ReadStatus {
        UNREAD("Unread"),
        READING("Reading"),
        READ("Read");

        private final String displayName;

        ReadStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ReadStatus fromString(String s) {
            if (s == null) return UNREAD;
            try {
                return ReadStatus.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNREAD;
            }
        }
    }

    private int id;
    private Publication publication;
    private int collectionId;
    private String collectionName; // transient — for display
    private String notes;
    private boolean favorite;
    private ReadStatus readStatus;
    private List<Tag> tags;
    private LocalDateTime addedAt;

    public LibraryEntry() {
        this.readStatus = ReadStatus.UNREAD;
        this.tags = new ArrayList<>();
        this.addedAt = LocalDateTime.now();
    }

    public LibraryEntry(Publication publication) {
        this();
        this.publication = publication;
    }

    public LibraryEntry(Publication publication, int collectionId) {
        this();
        this.publication = publication;
        this.collectionId = collectionId;
    }

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Publication getPublication() { return publication; }
    public void setPublication(Publication publication) { this.publication = publication; }

    public int getCollectionId() { return collectionId; }
    public void setCollectionId(int collectionId) { this.collectionId = collectionId; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public ReadStatus getReadStatus() { return readStatus; }
    public void setReadStatus(ReadStatus readStatus) { this.readStatus = readStatus; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    public void addTag(Tag tag) {
        if (this.tags == null) this.tags = new ArrayList<>();
        this.tags.add(tag);
    }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    @Override
    public String toString() {
        String title = publication != null ? publication.getTitle() : "Unknown";
        return String.format("[%s] %s (%s)", readStatus.getDisplayName(), title, 
                favorite ? "⭐" : "");
    }
}
