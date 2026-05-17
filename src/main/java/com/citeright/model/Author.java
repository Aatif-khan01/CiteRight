package com.citeright.model;

import java.util.Objects;

/**
 * Represents an author of a publication.
 * Demonstrates: ENCAPSULATION — all fields are private with controlled access.
 */
public class Author {

    private String authorId;
    private String name;

    public Author() {}

    public Author(String name) {
        this.name = name;
    }

    public Author(String authorId, String name) {
        this.authorId = authorId;
        this.name = name;
    }

    // --- Getters and Setters ---

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the last name of the author.
     * Useful for citation formatting (e.g., "Smith" from "John Smith").
     */
    public String getLastName() {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    /**
     * Returns formatted name for citations (e.g., "Smith, J." from "John Smith").
     */
    public String getCitationName() {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        String lastName = parts[parts.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            initials.append(parts[i].charAt(0)).append(".");
            if (i < parts.length - 2) initials.append(" ");
        }
        return lastName + ", " + initials.toString();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Author author = (Author) o;
        return Objects.equals(authorId, author.authorId) && Objects.equals(name, author.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorId, name);
    }
}
