package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Represents a PDF file linked to a paper in the library.
 * PDF files are stored in ~/.citeright/pdfs/ directory.
 * 
 * Demonstrates: ENCAPSULATION — private fields with controlled access.
 */
public class PdfFile {

    private int id;
    private int paperId;
    private String filePath;
    private String fileName;
    private long fileSize;
    private int pageCount;
    private LocalDateTime addedAt;

    public PdfFile() {
        this.addedAt = LocalDateTime.now();
    }

    public PdfFile(int paperId, String filePath, String fileName) {
        this();
        this.paperId = paperId;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPaperId() { return paperId; }
    public void setPaperId(int paperId) { this.paperId = paperId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    /**
     * Returns human-readable file size (e.g., "2.3 MB").
     */
    public String getFileSizeFormatted() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return fileName + " (" + getFileSizeFormatted() + ", " + pageCount + " pages)";
    }
}
