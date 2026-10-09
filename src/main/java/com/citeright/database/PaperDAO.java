package com.citeright.database;

import com.citeright.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Paper operations.
 * Now uses SQLite for local storage (replaces MySQL).
 * 
 * Demonstrates: DAO PATTERN — separates data access logic from business logic.
 */
public class PaperDAO {

    private final SQLiteDatabaseManager dbManager;

    public PaperDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Generates a stable synthetic paper ID for papers that have no external ID.
     * Uses title + year so the same paper always produces the same ID.
     */
    private String generateSyntheticId(Publication paper) {
        String title = paper.getTitle() != null ? paper.getTitle().toLowerCase().trim() : "";
        int year = paper.getYear();
        return "local-" + Math.abs((title + year).hashCode());
    }

    /**
     * Saves a publication to the database (caches API results).
     * Uses SQLite's ON CONFLICT for upsert behavior.
     */
    public void save(Publication paper) {
        if (!dbManager.isAvailable()) return;
        if (paper.getTitle() == null || paper.getTitle().isBlank()) return; // skip garbage entries

        // Assign a stable synthetic ID if none exists so ON CONFLICT works correctly
        if (paper.getPaperId() == null || paper.getPaperId().isBlank()) {
            paper.setPaperId(generateSyntheticId(paper));
        }

        String sql = """
            INSERT INTO papers (paper_id, title, abstract_text, year, citation_count, doi, url, venue, publication_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(paper_id) DO UPDATE SET
                title = excluded.title,
                citation_count = excluded.citation_count,
                abstract_text = excluded.abstract_text
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, paper.getPaperId());
                stmt.setString(2, paper.getTitle());
                stmt.setString(3, paper.getAbstractText());
                stmt.setInt(4, paper.getYear());
                stmt.setInt(5, paper.getCitationCount());
                stmt.setString(6, paper.getDoi());
                stmt.setString(7, paper.getUrl());
                stmt.setString(8, paper.getVenue());
                stmt.setString(9, paper.getPublicationType());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Error saving paper: " + e.getMessage());
        }
    }


    /**
     * Saves a list of publications (batch cache from API).
     */
    public void saveAll(List<Publication> papers) {
        if (!dbManager.isAvailable()) return;
        for (Publication paper : papers) {
            save(paper);
        }
    }

    /**
     * Searches cached papers by keywords using SQL LIKE.
     */
    public List<Publication> searchLocal(String keywords) {
        List<Publication> results = new ArrayList<>();
        if (!dbManager.isAvailable()) return results;

        String[] terms = keywords.toLowerCase().split("\\s+");
        StringBuilder whereClause = new StringBuilder();
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append("(LOWER(title) LIKE ? OR LOWER(abstract_text) LIKE ?)");
        }

        String sql = "SELECT * FROM papers WHERE " + whereClause + " ORDER BY citation_count DESC LIMIT 20";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return results;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (String term : terms) {
                    String like = "%" + term + "%";
                    stmt.setString(paramIndex++, like);
                    stmt.setString(paramIndex++, like);
                }
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Local search error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Finds a paper by DOI.
     */
    public Publication findByDoi(String doi) {
        if (!dbManager.isAvailable() || doi == null) return null;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM papers WHERE doi = ?")) {
                stmt.setString(1, doi);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return mapResultSet(rs);
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds a paper by database integer ID.
     */
    public Publication findById(int id) {
        if (!dbManager.isAvailable()) return null;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM papers WHERE id = ?")) {
                stmt.setInt(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return mapResultSet(rs);
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Error finding paper by ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds a paper by paper_id.
     */
    public Publication findByPaperId(String paperId) {
        if (!dbManager.isAvailable() || paperId == null) return null;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM papers WHERE paper_id = ?")) {
                stmt.setString(1, paperId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return mapResultSet(rs);
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets total number of cached papers.
     */
    public int getCachedPaperCount() {
        if (!dbManager.isAvailable()) return 0;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM papers")) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[PaperDAO] Error counting papers: " + e.getMessage());
        }
        return 0;
    }

    private Publication mapResultSet(ResultSet rs) throws SQLException {
        JournalArticle paper = new JournalArticle();
        paper.setPaperId(rs.getString("paper_id"));
        paper.setTitle(rs.getString("title"));
        paper.setAbstractText(rs.getString("abstract_text"));
        paper.setYear(rs.getInt("year"));
        paper.setCitationCount(rs.getInt("citation_count"));
        paper.setDoi(rs.getString("doi"));
        paper.setUrl(rs.getString("url"));
        paper.setVenue(rs.getString("venue"));
        return paper;
    }
}
