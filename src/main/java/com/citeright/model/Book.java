package com.citeright.model;

/**
 * Represents a book publication.
 * 
 * Demonstrates: INHERITANCE — extends Publication abstract class.
 * Adds book-specific fields like publisher, edition, and ISBN.
 */
public class Book extends Publication {

    private String publisher;
    private String edition;
    private String isbn;
    private String chapter;

    public Book() {
        super();
    }

    public Book(String paperId, String title, int year) {
        super(paperId, title, year);
    }

    @Override
    public String getPublicationType() {
        return "Book";
    }

    // --- Book-specific getters and setters ---

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }
}
