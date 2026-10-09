package com.citeright.ui.graph;

import com.citeright.database.SQLiteDatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles SQLite-backed persistence for Graph node positions and cluster metadata.
 * Part of Phase 1D: Graph Layout Caching.
 */
public class GraphLayoutCache {

    public static class CachedNodeData {
        public final double x;
        public final double y;
        public final String clusterLabel;
        public final String clusterColor;

        public CachedNodeData(double x, double y, String clusterLabel, String clusterColor) {
            this.x = x;
            this.y = y;
            this.clusterLabel = clusterLabel;
            this.clusterColor = clusterColor;
        }
    }

    public static void savePositions(GraphModel model) {
        String sql = "INSERT OR REPLACE INTO graph_layout_cache (paper_id, x, y, cluster_label, cluster_color) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            if (conn == null) return;
            conn.setAutoCommit(false);
            for (GraphNode node : model.getNodes()) {
                pstmt.setInt(1, node.entry.getId());
                pstmt.setDouble(2, node.x);
                pstmt.setDouble(3, node.y);
                pstmt.setString(4, node.clusterLabel);
                pstmt.setString(5, node.clusterColor != null ? node.clusterColor.toString() : null);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Map<Integer, CachedNodeData> loadPositions() {
        Map<Integer, CachedNodeData> cache = new HashMap<>();
        String sql = "SELECT paper_id, x, y, cluster_label, cluster_color FROM graph_layout_cache";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            if (conn == null) return cache;
            while (rs.next()) {
                cache.put(
                    rs.getInt("paper_id"),
                    new CachedNodeData(
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getString("cluster_label"),
                        rs.getString("cluster_color")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println("[GraphLayoutCache] Failed to load cache: " + e.getMessage());
        }
        return cache;
    }

    public static void clear() {
        String sql = "DELETE FROM graph_layout_cache";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn != null) {
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
