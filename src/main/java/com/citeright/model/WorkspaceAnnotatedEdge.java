package com.citeright.model;

import java.time.LocalDateTime;

/**
 * Custom-labeled directed edge between two papers on the Micro workspace.
 * Unlike PaperRelationship (which uses fixed types like SUPPORTS/CONTRADICTS),
 * this allows researchers to write freeform labels like "leads to", "disproves claim 3 of", etc.
 */
public class WorkspaceAnnotatedEdge {
    private int id;
    private int sourcePaperId;
    private int targetPaperId;
    private String label = "";
    private String color = "#4a9cf7"; // Default blue
    private LocalDateTime createdAt;

    public WorkspaceAnnotatedEdge() {
        this.createdAt = LocalDateTime.now();
    }

    public WorkspaceAnnotatedEdge(int sourcePaperId, int targetPaperId, String label) {
        this();
        this.sourcePaperId = sourcePaperId;
        this.targetPaperId = targetPaperId;
        this.label = label;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSourcePaperId() { return sourcePaperId; }
    public void setSourcePaperId(int sourcePaperId) { this.sourcePaperId = sourcePaperId; }

    public int getTargetPaperId() { return targetPaperId; }
    public void setTargetPaperId(int targetPaperId) { this.targetPaperId = targetPaperId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
