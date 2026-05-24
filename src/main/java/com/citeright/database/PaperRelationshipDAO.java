package com.citeright.database;

import com.citeright.model.PaperRelationship;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for paper relationships.
 * Supports user-created, AI-suggested, and auto-detected relationships
 * with confidence scoring and dismiss/confirm workflow.
 */
public class PaperRelationshipDAO {
    private final SQLiteDatabaseManager dbManager;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PaperRelationshipDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    public void insert(PaperRelationship rel) {
        String sql = "INSERT INTO paper_relationships (source_paper_id, target_paper_id, relationship_type, reasoning, confidence, source, dismissed) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, rel.getSourcePaperId());
            pstmt.setInt(2, rel.getTargetPaperId());
            pstmt.setString(3, rel.getRelationshipType());
            pstmt.setString(4, rel.getReasoning());
            pstmt.setDouble(5, rel.getConfidence());
            pstmt.setString(6, rel.getSource() != null ? rel.getSource().name() : "USER");
            pstmt.setInt(7, rel.isDismissed() ? 1 : 0);
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    rel.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Bulk insert for AI-suggested or auto-detected relationships.
     * Uses a single transaction for performance with large batches.
     */
    public void insertBatch(List<PaperRelationship> rels) {
        String sql = "INSERT OR IGNORE INTO paper_relationships (source_paper_id, target_paper_id, relationship_type, reasoning, confidence, source, dismissed) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (PaperRelationship rel : rels) {
                    pstmt.setInt(1, rel.getSourcePaperId());
                    pstmt.setInt(2, rel.getTargetPaperId());
                    pstmt.setString(3, rel.getRelationshipType());
                    pstmt.setString(4, rel.getReasoning());
                    pstmt.setDouble(5, rel.getConfidence());
                    pstmt.setString(6, rel.getSource() != null ? rel.getSource().name() : "AUTO_DETECTED");
                    pstmt.setInt(7, rel.isDismissed() ? 1 : 0);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Confirm an AI-suggested relationship — upgrades source to AI_CONFIRMED.
     */
    public void confirm(int id) {
        String sql = "UPDATE paper_relationships SET source = 'AI_CONFIRMED', confidence = 1.0 WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Dismiss an AI-suggested relationship — hides it without deleting.
     */
    public void dismiss(int id) {
        String sql = "UPDATE paper_relationships SET dismissed = 1 WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<PaperRelationship> getBySourcePaperId(int sourcePaperId) {
        return getByColumn("source_paper_id", sourcePaperId);
    }

    public List<PaperRelationship> getByTargetPaperId(int targetPaperId) {
        return getByColumn("target_paper_id", targetPaperId);
    }

    public List<PaperRelationship> getByPaperId(int paperId) {
        List<PaperRelationship> results = new ArrayList<>();
        String sql = "SELECT * FROM paper_relationships WHERE (source_paper_id = ? OR target_paper_id = ?) AND dismissed = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, paperId);
            pstmt.setInt(2, paperId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToPaperRelationship(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Returns only active (non-dismissed) relationships for the given source paper.
     */
    public List<PaperRelationship> getActiveBySourcePaperId(int sourcePaperId) {
        List<PaperRelationship> results = new ArrayList<>();
        String sql = "SELECT * FROM paper_relationships WHERE source_paper_id = ? AND dismissed = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sourcePaperId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToPaperRelationship(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Returns AI-suggested relationships awaiting user confirmation for a paper.
     */
    public List<PaperRelationship> getPendingSuggestions(int paperId) {
        List<PaperRelationship> results = new ArrayList<>();
        String sql = "SELECT * FROM paper_relationships WHERE (source_paper_id = ? OR target_paper_id = ?) AND source = 'AI_SUGGESTED' AND dismissed = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, paperId);
            pstmt.setInt(2, paperId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToPaperRelationship(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Returns all non-dismissed relationships across the entire library.
     */
    public List<PaperRelationship> getAll() {
        List<PaperRelationship> results = new ArrayList<>();
        String sql = "SELECT * FROM paper_relationships WHERE dismissed = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapRowToPaperRelationship(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void delete(int id) {
        String sql = "DELETE FROM paper_relationships WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<PaperRelationship> getByColumn(String columnName, int value) {
        List<PaperRelationship> results = new ArrayList<>();
        String sql = "SELECT * FROM paper_relationships WHERE " + columnName + " = ? AND dismissed = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, value);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToPaperRelationship(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    private PaperRelationship mapRowToPaperRelationship(ResultSet rs) throws SQLException {
        PaperRelationship rel = new PaperRelationship();
        rel.setId(rs.getInt("id"));
        rel.setSourcePaperId(rs.getInt("source_paper_id"));
        rel.setTargetPaperId(rs.getInt("target_paper_id"));
        rel.setRelationshipType(rs.getString("relationship_type"));
        rel.setReasoning(rs.getString("reasoning"));
        
        // New fields — handle gracefully if column missing in old DB
        try {
            rel.setConfidence(rs.getDouble("confidence"));
        } catch (SQLException ignored) {
            rel.setConfidence(1.0);
        }
        try {
            String sourceStr = rs.getString("source");
            rel.setSource(sourceStr != null ? PaperRelationship.Source.valueOf(sourceStr) : PaperRelationship.Source.USER);
        } catch (Exception ignored) {
            rel.setSource(PaperRelationship.Source.USER);
        }
        try {
            rel.setDismissed(rs.getInt("dismissed") == 1);
        } catch (SQLException ignored) {
            rel.setDismissed(false);
        }
        
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            try {
                rel.setCreatedAt(LocalDateTime.parse(createdAtStr, formatter));
            } catch (Exception e) {
                // Ignore parse error
            }
        }
        return rel;
    }
}
