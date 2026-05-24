package com.citeright.database;

import com.citeright.model.WorkspaceNote;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD operations for freeform workspace notes on the Micro canvas.
 */
public class WorkspaceNoteDAO {
    private final SQLiteDatabaseManager dbManager;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public WorkspaceNoteDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    public void insert(WorkspaceNote note) {
        String sql = "INSERT INTO workspace_notes (x, y, width, height, text, color) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setDouble(1, note.getX());
            pstmt.setDouble(2, note.getY());
            pstmt.setDouble(3, note.getWidth());
            pstmt.setDouble(4, note.getHeight());
            pstmt.setString(5, note.getText());
            pstmt.setString(6, note.getColor());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) note.setId(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(WorkspaceNote note) {
        String sql = "UPDATE workspace_notes SET x = ?, y = ?, width = ?, height = ?, text = ?, color = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, note.getX());
            pstmt.setDouble(2, note.getY());
            pstmt.setDouble(3, note.getWidth());
            pstmt.setDouble(4, note.getHeight());
            pstmt.setString(5, note.getText());
            pstmt.setString(6, note.getColor());
            pstmt.setInt(7, note.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM workspace_notes WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<WorkspaceNote> getAll() {
        List<WorkspaceNote> notes = new ArrayList<>();
        String sql = "SELECT * FROM workspace_notes ORDER BY created_at";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                WorkspaceNote note = new WorkspaceNote();
                note.setId(rs.getInt("id"));
                note.setX(rs.getDouble("x"));
                note.setY(rs.getDouble("y"));
                note.setWidth(rs.getDouble("width"));
                note.setHeight(rs.getDouble("height"));
                note.setText(rs.getString("text"));
                note.setColor(rs.getString("color"));
                String ts = rs.getString("created_at");
                if (ts != null && !ts.isEmpty()) {
                    try { note.setCreatedAt(LocalDateTime.parse(ts, formatter)); } catch (Exception ignored) {}
                }
                notes.add(note);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notes;
    }
}
