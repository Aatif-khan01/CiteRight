package com.citeright.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a collection (folder) for organizing papers in the user's library.
 * Collections can be nested via parentId for hierarchical organization.
 * 
 * Demonstrates: ENCAPSULATION — private fields with controlled access.
 */
public class Collection {

    private int id;
    private String name;
    private String description;
    private String color;
    private Integer parentId; // null = root collection
    private int paperCount;   // transient — computed from DB
    private boolean isSmart;
    private String smartQuery;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Collection() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.color = "#4A90D9";
    }

    public Collection(String name) {
        this();
        this.name = name;
    }

    public Collection(String name, String color) {
        this();
        this.name = name;
        this.color = color;
    }

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public int getPaperCount() { return paperCount; }
    public void setPaperCount(int paperCount) { this.paperCount = paperCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSmart() { return isSmart; }
    public void setSmart(boolean smart) { isSmart = smart; }

    public String getSmartQuery() { return smartQuery; }
    public void setSmartQuery(String smartQuery) { this.smartQuery = smartQuery; }

    @Override
    public String toString() {
        return name + " (" + paperCount + " papers)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Collection that = (Collection) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
