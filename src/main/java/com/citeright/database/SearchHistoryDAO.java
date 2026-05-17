package com.citeright.database;

import com.citeright.model.SearchQuery;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for search history operations.
 * Now uses SQLite for local storage.
 * 
 * Demonstrates: DAO PATTERN
 */
public class SearchHistoryDAO {

    private final SQLiteDatabaseManager dbManager;

    public SearchHistoryDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Saves a search query to history.
     */
    public void save(SearchQuery query) {
        if (!dbManager.isAvailable()) return;

        String sql = "INSERT INTO search_history (query_text, extracted_keywords, result_count) VALUES (?, ?, ?)";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, query.getOriginalSentence());
                stmt.setString(2, query.getExtractedKeywords());
                stmt.setInt(3, query.getResultCount());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SearchHistoryDAO] Error saving: " + e.getMessage());
        }
    }

    /**
     * Returns the most recent search queries.
     */
    public List<SearchQuery> getRecentSearches(int limit) {
        List<SearchQuery> history = new ArrayList<>();
        if (!dbManager.isAvailable()) return history;

        String sql = "SELECT * FROM search_history ORDER BY searched_at DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return history;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    SearchQuery query = new SearchQuery();
                    query.setId(rs.getInt("id"));
                    query.setOriginalSentence(rs.getString("query_text"));
                    query.setExtractedKeywords(rs.getString("extracted_keywords"));
                    query.setResultCount(rs.getInt("result_count"));
                    // SQLite returns timestamps as strings — handle gracefully
                    String timestamp = rs.getString("searched_at");
                    if (timestamp != null) {
                        try {
                            query.setSearchedAt(java.time.LocalDateTime.parse(
                                timestamp.replace(" ", "T")));
                        } catch (Exception e) {
                            query.setSearchedAt(java.time.LocalDateTime.now());
                        }
                    }
                    history.add(query);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SearchHistoryDAO] Error fetching history: " + e.getMessage());
        }

        return history;
    }

    /**
     * Deletes a single search history entry by its ID.
     */
    public void deleteById(int id) {
        if (!dbManager.isAvailable()) return;

        String sql = "DELETE FROM search_history WHERE id = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SearchHistoryDAO] Error deleting entry: " + e.getMessage());
        }
    }

    /**
     * Clears all search history.
     */
    public void clearHistory() {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM search_history");
            }
        } catch (SQLException e) {
            System.err.println("[SearchHistoryDAO] Error clearing: " + e.getMessage());
        }
    }
}
