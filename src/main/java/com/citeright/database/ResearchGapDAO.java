package com.citeright.database;

import com.citeright.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for persisting and querying research gaps.
 *
 * Handles:
 *   - CRUD operations on research_gaps table
 *   - History tracking in research_gap_history for Opportunity Watch
 *   - Status updates (NEW → VIEWED → SAVED → INVESTIGATED → DISMISSED)
 */
public class ResearchGapDAO {

    private final SQLiteDatabaseManager dbManager;

    public ResearchGapDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Save or update a research gap result.
     * If the gap already exists (same gap_id), its scores are updated
     * and a history record is added for Opportunity Watch tracking.
     */
    public void saveGap(ResearchGap gap) {
        if (!dbManager.isAvailable() || gap.getGapId() == null) return;

        String sql = """
            INSERT INTO research_gaps (gap_id, gap_type, title, explanation, detection_method,
                confidence, opportunity_score, evidence_strength, severity, status,
                is_opportunity, research_readiness, missing_paper_count,
                affected_clusters, provenance_json, detected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(gap_id) DO UPDATE SET
                title = excluded.title,
                explanation = excluded.explanation,
                confidence = excluded.confidence,
                opportunity_score = excluded.opportunity_score,
                evidence_strength = excluded.evidence_strength,
                severity = excluded.severity,
                is_opportunity = excluded.is_opportunity,
                research_readiness = excluded.research_readiness,
                missing_paper_count = excluded.missing_paper_count,
                affected_clusters = excluded.affected_clusters,
                provenance_json = excluded.provenance_json,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, gap.getGapId());
                stmt.setString(2, gap.getType() != null ? gap.getType().name() : "TOPIC");
                stmt.setString(3, gap.getTitle());
                stmt.setString(4, gap.getExplanation());
                stmt.setString(5, gap.getDetectionMethod());
                stmt.setDouble(6, gap.getConfidence());
                stmt.setDouble(7, gap.getOpportunityScore());
                stmt.setString(8, gap.getEvidenceStrength() != null ? gap.getEvidenceStrength().name() : "LOW");
                stmt.setString(9, gap.getSeverity() != null ? gap.getSeverity().name() : "LOW");
                stmt.setString(10, gap.getStatus() != null ? gap.getStatus().name() : "NEW");
                stmt.setInt(11, gap.isOpportunity() ? 1 : 0);
                stmt.setDouble(12, gap.getResearchReadiness());
                stmt.setInt(13, gap.getMissingPaperCount());
                stmt.setString(14, gap.getAffectedClusters() != null ? String.join(",", gap.getAffectedClusters()) : "");
                stmt.setString(15, gap.getProvenance() != null ? gap.getProvenance().toSummary() : "");
                stmt.setLong(16, gap.getDetectedAt());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error saving gap " + gap.getGapId() + ": " + e.getMessage());
        }
    }

    /**
     * Batch save multiple gaps in a single transaction.
     */
    public void saveGaps(List<ResearchGap> gaps) {
        if (!dbManager.isAvailable() || gaps == null || gaps.isEmpty()) return;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            conn.setAutoCommit(false);
            try {
                for (ResearchGap gap : gaps) {
                    saveGapInTransaction(conn, gap);
                }
                conn.commit();

                // Record history for Opportunity Watch
                recordHistory(conn, gaps);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error batch saving gaps: " + e.getMessage());
        }
    }

    /**
     * Update the user status of a gap (VIEWED, SAVED, DISMISSED, INVESTIGATED).
     */
    public void updateStatus(String gapId, GapStatus status) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE research_gaps SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE gap_id = ?")) {
                stmt.setString(1, status.name());
                stmt.setString(2, gapId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error updating status: " + e.getMessage());
        }
    }

    /**
     * Update user notes for a gap.
     */
    public void updateNotes(String gapId, String notes) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE research_gaps SET user_notes = ?, updated_at = CURRENT_TIMESTAMP WHERE gap_id = ?")) {
                stmt.setString(1, notes);
                stmt.setString(2, gapId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error updating notes: " + e.getMessage());
        }
    }

    /**
     * Get the score history for a gap (for Opportunity Watch trend visualization).
     */
    public List<double[]> getScoreHistory(String gapId) {
        List<double[]> history = new ArrayList<>();
        if (!dbManager.isAvailable()) return history;

        String sql = "SELECT opportunity_score, confidence, recorded_at FROM research_gap_history " +
                     "WHERE gap_id = ? ORDER BY recorded_at ASC";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return history;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, gapId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    history.add(new double[]{
                        rs.getDouble("opportunity_score"),
                        rs.getDouble("confidence")
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error reading history: " + e.getMessage());
        }

        return history;
    }

    /**
     * Get the user's saved/investigated gaps (persisted status).
     */
    public GapStatus getPersistedStatus(String gapId) {
        if (!dbManager.isAvailable()) return null;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT status FROM research_gaps WHERE gap_id = ?")) {
                stmt.setString(1, gapId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    try { return GapStatus.valueOf(rs.getString("status")); }
                    catch (IllegalArgumentException e) { return GapStatus.NEW; }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ResearchGapDAO] Error reading status: " + e.getMessage());
        }

        return null;
    }

    // ─── Private helpers ────────────────────────────────────────────────

    private void saveGapInTransaction(Connection conn, ResearchGap gap) throws SQLException {
        if (gap.getGapId() == null) return;

        String sql = """
            INSERT INTO research_gaps (gap_id, gap_type, title, explanation, detection_method,
                confidence, opportunity_score, evidence_strength, severity, status,
                is_opportunity, research_readiness, missing_paper_count,
                affected_clusters, provenance_json, detected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(gap_id) DO UPDATE SET
                title = excluded.title,
                explanation = excluded.explanation,
                confidence = excluded.confidence,
                opportunity_score = excluded.opportunity_score,
                evidence_strength = excluded.evidence_strength,
                severity = excluded.severity,
                is_opportunity = excluded.is_opportunity,
                research_readiness = excluded.research_readiness,
                missing_paper_count = excluded.missing_paper_count,
                affected_clusters = excluded.affected_clusters,
                provenance_json = excluded.provenance_json,
                updated_at = CURRENT_TIMESTAMP
        """;

        // Preserve user-set status (don't overwrite SAVED/DISMISSED)
        GapStatus persistedStatus = getPersistedStatusDirect(conn, gap.getGapId());
        if (persistedStatus != null && (persistedStatus == GapStatus.SAVED ||
            persistedStatus == GapStatus.DISMISSED || persistedStatus == GapStatus.INVESTIGATED)) {
            gap.setStatus(persistedStatus);
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, gap.getGapId());
            stmt.setString(2, gap.getType() != null ? gap.getType().name() : "TOPIC");
            stmt.setString(3, gap.getTitle());
            stmt.setString(4, gap.getExplanation());
            stmt.setString(5, gap.getDetectionMethod());
            stmt.setDouble(6, gap.getConfidence());
            stmt.setDouble(7, gap.getOpportunityScore());
            stmt.setString(8, gap.getEvidenceStrength() != null ? gap.getEvidenceStrength().name() : "LOW");
            stmt.setString(9, gap.getSeverity() != null ? gap.getSeverity().name() : "LOW");
            stmt.setString(10, gap.getStatus() != null ? gap.getStatus().name() : "NEW");
            stmt.setInt(11, gap.isOpportunity() ? 1 : 0);
            stmt.setDouble(12, gap.getResearchReadiness());
            stmt.setInt(13, gap.getMissingPaperCount());
            stmt.setString(14, gap.getAffectedClusters() != null ? String.join(",", gap.getAffectedClusters()) : "");
            stmt.setString(15, gap.getProvenance() != null ? gap.getProvenance().toSummary() : "");
            stmt.setLong(16, gap.getDetectedAt());
            stmt.executeUpdate();
        }
    }

    private GapStatus getPersistedStatusDirect(Connection conn, String gapId) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT status FROM research_gaps WHERE gap_id = ?")) {
            stmt.setString(1, gapId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                try { return GapStatus.valueOf(rs.getString("status")); }
                catch (IllegalArgumentException e) { return null; }
            }
        } catch (SQLException e) { /* ignore */ }
        return null;
    }

    private void recordHistory(Connection conn, List<ResearchGap> gaps) throws SQLException {
        String sql = """
            INSERT INTO research_gap_history (gap_id, opportunity_score, confidence, evidence_strength, paper_count)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (ResearchGap gap : gaps) {
                if (gap.getGapId() == null) continue;
                stmt.setString(1, gap.getGapId());
                stmt.setDouble(2, gap.getOpportunityScore());
                stmt.setDouble(3, gap.getConfidence());
                stmt.setString(4, gap.getEvidenceStrength() != null ? gap.getEvidenceStrength().name() : "LOW");
                stmt.setInt(5, gap.getAffectedPaperIds() != null ? gap.getAffectedPaperIds().size() : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
