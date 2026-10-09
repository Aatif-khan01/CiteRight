package com.citeright.model;

/**
 * Request object to pass PDF details + publication when opening the viewer.
 */
public class PdfOpenRequest {
    private final String pdfPath;
    private final int pdfId;
    private final String title;
    private final Publication publication; // for citation quick-copy

    public PdfOpenRequest(String pdfPath, int pdfId, String title) {
        this(pdfPath, pdfId, title, null);
    }

    public PdfOpenRequest(String pdfPath, int pdfId, String title, Publication publication) {
        this.pdfPath = pdfPath;
        this.pdfId = pdfId;
        this.title = title;
        this.publication = publication;
    }

    public String getPdfPath() { return pdfPath; }
    public int getPdfId() { return pdfId; }
    public String getTitle() { return title; }
    public Publication getPublication() { return publication; }
}
