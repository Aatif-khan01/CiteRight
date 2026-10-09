package com.citeright.model;

import java.util.Objects;

/**
 * Represents a tag for categorizing papers.
 * Tags are user-created labels with custom colors.
 * 
 * Demonstrates: ENCAPSULATION — private fields with controlled access.
 */
public class Tag {

    private int id;
    private String name;
    private String color;

    public Tag() {
        this.color = "#888888";
    }

    public Tag(String name) {
        this();
        this.name = name;
    }

    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return id == tag.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
