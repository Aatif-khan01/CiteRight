package com.citeright.database;

import com.citeright.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Tag operations.
 * Demonstrates: DAO PATTERN
 */
public class TagDAO {

    private final SQLiteDatabaseManager dbManager;

    public TagDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    public Tag create(Tag tag) {
        if (!dbManager.isAvailable()) return tag;
        Tag existing = getByName(tag.getName());
        if (existing != null) return existing;

        String sql = "INSERT INTO tags (name, color) VALUES (?, ?)";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return tag;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, tag.getName());
                stmt.setString(2, tag.getColor());
                stmt.executeUpdate();
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) tag.setId(keys.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error creating tag: " + e.getMessage());
        }
        return tag;
    }

    public List<Tag> getAll() {
        List<Tag> tags = new ArrayList<>();
        if (!dbManager.isAvailable()) return tags;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return tags;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM tags ORDER BY name")) {
                while (rs.next()) tags.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
        return tags;
    }

    public Tag getByName(String name) {
        if (!dbManager.isAvailable()) return null;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tags WHERE name = ?")) {
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return mapResultSet(rs);
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
        return null;
    }

    public List<Tag> getTagsForPaper(int paperId) {
        List<Tag> tags = new ArrayList<>();
        if (!dbManager.isAvailable()) return tags;
        String sql = "SELECT t.* FROM tags t INNER JOIN paper_tags pt ON t.id = pt.tag_id WHERE pt.paper_id = ? ORDER BY t.name";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return tags;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, paperId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) tags.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
        return tags;
    }

    public void addTagToPaper(int paperId, int tagId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("INSERT OR IGNORE INTO paper_tags (paper_id, tag_id) VALUES (?, ?)")) {
                stmt.setInt(1, paperId);
                stmt.setInt(2, tagId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
    }

    public void removeTagFromPaper(int paperId, int tagId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM paper_tags WHERE paper_id = ? AND tag_id = ?")) {
                stmt.setInt(1, paperId);
                stmt.setInt(2, tagId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
    }

    public void delete(int tagId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM tags WHERE id = ?")) {
                stmt.setInt(1, tagId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[TagDAO] Error: " + e.getMessage());
        }
    }

    private Tag mapResultSet(ResultSet rs) throws SQLException {
        Tag tag = new Tag();
        tag.setId(rs.getInt("id"));
        tag.setName(rs.getString("name"));
        tag.setColor(rs.getString("color"));
        return tag;
    }
}
