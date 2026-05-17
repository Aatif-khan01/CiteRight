package com.citeright.model;

import java.time.LocalDateTime;

public class Group {
    private int id;
    private String name;
    private String description;
    private String color;
    private LocalDateTime createdAt;

    public Group() { this.color = "#6C5CE7"; this.createdAt = LocalDateTime.now(); }
    public Group(String name) { this(); this.name = name; }
    public Group(String name, String color) { this(); this.name = name; this.color = color; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}
