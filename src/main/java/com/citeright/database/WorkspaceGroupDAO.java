package com.citeright.database;

import com.citeright.model.WorkspaceGroup;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD operations for workspace groups (named paper clusters for argument mapping).
 * Manages both the group metadata and the group-paper membership join table.
 */
public class WorkspaceGroupDAO {
    private final SQLiteDatabaseManager dbManager;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public WorkspaceGroupDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    public void insert(WorkspaceGroup group) {
        String sql = "INSERT INTO workspace_groups (name, color, x, y, width, height) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getColor());
            pstmt.setDouble(3, group.getX());
            pstmt.setDouble(4, group.getY());
            pstmt.setDouble(5, group.getWidth());
            pstmt.setDouble(6, group.getHeight());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    group.setId(rs.getInt(1));
                }
            }
            // Insert paper memberships
            if (!group.getPaperIds().isEmpty()) {
                String memberSql = "INSERT OR IGNORE INTO workspace_group_papers (group_id, paper_id) VALUES (?, ?)";
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (int paperId : group.getPaperIds()) {
                        memberStmt.setInt(1, group.getId());
                        memberStmt.setInt(2, paperId);
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(WorkspaceGroup group) {
        String sql = "UPDATE workspace_groups SET name = ?, color = ?, x = ?, y = ?, width = ?, height = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getColor());
            pstmt.setDouble(3, group.getX());
            pstmt.setDouble(4, group.getY());
            pstmt.setDouble(5, group.getWidth());
            pstmt.setDouble(6, group.getHeight());
            pstmt.setInt(7, group.getId());
            pstmt.executeUpdate();

            // Re-sync paper memberships
            try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM workspace_group_papers WHERE group_id = ?")) {
                delStmt.setInt(1, group.getId());
                delStmt.executeUpdate();
            }
            if (!group.getPaperIds().isEmpty()) {
                String memberSql = "INSERT OR IGNORE INTO workspace_group_papers (group_id, paper_id) VALUES (?, ?)";
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (int paperId : group.getPaperIds()) {
                        memberStmt.setInt(1, group.getId());
                        memberStmt.setInt(2, paperId);
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(int id) {
        // CASCADE handles workspace_group_papers cleanup
        String sql = "DELETE FROM workspace_groups WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<WorkspaceGroup> getAll() {
        List<WorkspaceGroup> groups = new ArrayList<>();
        String sql = "SELECT * FROM workspace_groups ORDER BY created_at";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                WorkspaceGroup group = new WorkspaceGroup();
                group.setId(rs.getInt("id"));
                group.setName(rs.getString("name"));
                group.setColor(rs.getString("color"));
                group.setX(rs.getDouble("x"));
                group.setY(rs.getDouble("y"));
                group.setWidth(rs.getDouble("width"));
                group.setHeight(rs.getDouble("height"));
                String ts = rs.getString("created_at");
                if (ts != null && !ts.isEmpty()) {
                    try { group.setCreatedAt(LocalDateTime.parse(ts, formatter)); } catch (Exception ignored) {}
                }
                // Load paper memberships
                group.setPaperIds(getPaperIdsForGroup(conn, group.getId()));
                groups.add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    private List<Integer> getPaperIdsForGroup(Connection conn, int groupId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT paper_id FROM workspace_group_papers WHERE group_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("paper_id"));
                }
            }
        }
        return ids;
    }
}
