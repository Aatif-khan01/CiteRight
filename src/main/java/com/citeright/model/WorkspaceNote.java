package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Freeform text note on the Micro workspace canvas.
 * Not attached to any specific paper — exists as independent thought on the canvas.
 */
public class WorkspaceNote {
    private int id;
    private double x, y;
    private double width = 180;
    private double height = 100;
    private String text = "";
    private String color = "#f1c40f"; // Default yellow
    private LocalDateTime createdAt;

    public WorkspaceNote() {
        this.createdAt = LocalDateTime.now();
    }

    public WorkspaceNote(double x, double y, String text) {
        this();
        this.x = x;
        this.y = y;
        this.text = text;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
