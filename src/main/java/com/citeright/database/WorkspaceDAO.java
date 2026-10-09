package com.citeright.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class WorkspaceDAO {
    private final SQLiteDatabaseManager dbManager;

    public WorkspaceDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    public static class PinLocation {
        public double x;
        public double y;
        public PinLocation(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public void pinPaper(int paperId, double x, double y) {
        String sql = "INSERT OR REPLACE INTO workspace_pins (paper_id, x, y) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, paperId);
            pstmt.setDouble(2, x);
            pstmt.setDouble(3, y);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updatePinLocation(int paperId, double x, double y) {
        String sql = "UPDATE workspace_pins SET x = ?, y = ? WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, y);
            pstmt.setInt(3, paperId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void unpinPaper(int paperId) {
        String sql = "DELETE FROM workspace_pins WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, paperId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<Integer, PinLocation> getAllPins() {
        Map<Integer, PinLocation> pins = new HashMap<>();
        String sql = "SELECT paper_id, x, y FROM workspace_pins";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                pins.put(rs.getInt("paper_id"), new PinLocation(rs.getDouble("x"), rs.getDouble("y")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pins;
    }

    public boolean isPinned(int paperId) {
        String sql = "SELECT 1 FROM workspace_pins WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, paperId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
