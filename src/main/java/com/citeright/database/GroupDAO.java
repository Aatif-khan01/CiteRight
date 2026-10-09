package com.citeright.database;

import com.citeright.model.Group;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    public Group create(Group group) {
        String sql = "INSERT INTO groups (name, description, color) VALUES (?, ?, ?)";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return group;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, group.getName());
                ps.setString(2, group.getDescription());
                ps.setString(3, group.getColor());
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) group.setId(rs.getInt(1));
            }
        } catch (SQLException e) { System.err.println("[GroupDAO] create error: " + e.getMessage()); }
        return group;
    }

    public List<Group> getAll() {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM groups ORDER BY name";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return groups;
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group();
                    g.setId(rs.getInt("id"));
                    g.setName(rs.getString("name"));
                    g.setDescription(rs.getString("description"));
                    g.setColor(rs.getString("color"));
                    groups.add(g);
                }
            }
        } catch (SQLException e) { System.err.println("[GroupDAO] getAll error: " + e.getMessage()); }
        return groups;
    }

    public void delete(int id) {
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM groups WHERE id = ?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[GroupDAO] delete error: " + e.getMessage()); }
    }

    public void addPaperToGroup(int paperId, int groupId) {
        String sql = "INSERT OR IGNORE INTO paper_groups (paper_id, group_id) VALUES (?, ?)";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, paperId); ps.setInt(2, groupId); ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[GroupDAO] addPaper error: " + e.getMessage()); }
    }

    public void removePaperFromGroup(int paperId, int groupId) {
        String sql = "DELETE FROM paper_groups WHERE paper_id = ? AND group_id = ?";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, paperId); ps.setInt(2, groupId); ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[GroupDAO] removePaper error: " + e.getMessage()); }
    }
}
