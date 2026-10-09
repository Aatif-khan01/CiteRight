package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Represents an annotation on a PDF page.
 * Supports highlights, underlines, notes, and strikethroughs.
 * 
 * Demonstrates: ENCAPSULATION — private fields with controlled access.
 */
public class Annotation {

    /**
     * Types of annotations supported.
     */
    public enum AnnotationType {
        HIGHLIGHT("Highlight"),
        UNDERLINE("Underline"),
        NOTE("Note"),
        PEN("Pen"),
        STICKY_NOTE("Sticky Note"),
        STRIKETHROUGH("Strikethrough");

        private final String displayName;

        AnnotationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static AnnotationType fromString(String s) {
            if (s == null) return HIGHLIGHT;
            try {
                return AnnotationType.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return HIGHLIGHT;
            }
        }
    }

    private int id;
    private int pdfId;
    private AnnotationType type;
    private int pageNumber;
    private double x;
    private double y;
    private double width;
    private double height;
    private String content; // Note text or highlighted text
    private String color;
    private String strokeData; // JSON for pen strokes [{x,y}...]
    private LocalDateTime createdAt;

    public Annotation() {
        this.type = AnnotationType.HIGHLIGHT;
        this.color = "#FFFF00"; // Yellow default
        this.createdAt = LocalDateTime.now();
    }

    public Annotation(int pdfId, AnnotationType type, int pageNumber) {
        this();
        this.pdfId = pdfId;
        this.type = type;
        this.pageNumber = pageNumber;
    }

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPdfId() { return pdfId; }
    public void setPdfId(int pdfId) { this.pdfId = pdfId; }

    public AnnotationType getType() { return type; }
    public void setType(AnnotationType type) { this.type = type; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getStrokeData() { return strokeData; }
    public void setStrokeData(String strokeData) { this.strokeData = strokeData; }

    public void setType(String typeStr) { this.type = AnnotationType.fromString(typeStr); }
    public String getTypeString() { return type != null ? type.name() : "HIGHLIGHT"; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("[%s] Page %d: %s", type.getDisplayName(), pageNumber, 
                content != null && content.length() > 50 ? content.substring(0, 47) + "..." : content);
    }
}
