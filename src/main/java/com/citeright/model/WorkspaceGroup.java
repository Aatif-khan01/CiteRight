package com.citeright.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Named group of papers on the Micro workspace canvas.
 * Visually rendered as a translucent colored rectangle enclosing member papers,
 * supporting argument mapping (e.g., "Evidence For Hypothesis 1").
 */
public class WorkspaceGroup {
    private int id;
    private String name;
    private String color = "#6c5ce7"; // Default purple
    private List<Integer> paperIds = new ArrayList<>();
    // Bounding box (auto-computed from member papers, but stored for persistence)
    private double x, y, width, height;
    private LocalDateTime createdAt;

    public WorkspaceGroup() {
        this.createdAt = LocalDateTime.now();
    }

    public WorkspaceGroup(String name, String color) {
        this();
        this.name = name;
        this.color = color;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public List<Integer> getPaperIds() { return paperIds; }
    public void setPaperIds(List<Integer> paperIds) { this.paperIds = paperIds != null ? paperIds : new ArrayList<>(); }
    public void addPaperId(int paperId) { this.paperIds.add(paperId); }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
