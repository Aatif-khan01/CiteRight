package com.citeright.model;

/**
 * Represents a conference paper publication.
 * 
 * Demonstrates: INHERITANCE — extends Publication abstract class.
 * Adds conference-specific fields like conference name and location.
 */
public class ConferencePaper extends Publication {

    private String conferenceName;
    private String location;
    private String publisher;

    public ConferencePaper() {
        super();
    }

    public ConferencePaper(String paperId, String title, int year) {
        super(paperId, title, year);
    }

    @Override
    public String getPublicationType() {
        return "Conference Paper";
    }

    // --- Conference-specific getters and setters ---

    public String getConferenceName() {
        return conferenceName;
    }

    public void setConferenceName(String conferenceName) {
        this.conferenceName = conferenceName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
}
