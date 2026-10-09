package com.citeright.model;

/**
 * Represents a journal article publication.
 * 
 * Demonstrates: INHERITANCE — extends Publication abstract class.
 * Adds journal-specific fields like volume, issue, and pages.
 */
public class JournalArticle extends Publication {

    private String journalName;
    private String volume;
    private String issue;
    private String pages;
    private String issn;

    public JournalArticle() {
        super();
    }

    public JournalArticle(String paperId, String title, int year) {
        super(paperId, title, year);
    }

    @Override
    public String getPublicationType() {
        return "Journal Article";
    }

    // --- Journal-specific getters and setters ---

    public String getJournalName() {
        return journalName;
    }

    public void setJournalName(String journalName) {
        this.journalName = journalName;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getIssn() {
        return issn;
    }

    public void setIssn(String issn) {
        this.issn = issn;
    }
}
