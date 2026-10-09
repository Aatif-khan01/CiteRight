package com.citeright.database;

import com.citeright.nlp.EntityExtractor.EntityType;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object for paper entity cache (paper_entities table).
 *
 * Caches extracted entities (methods, domains, tasks, datasets, algorithms)
 * so that entity extraction only runs once per paper.
 */
public class EntityDAO {

    private final SQLiteDatabaseManager dbManager;

    public EntityDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Save extracted entities for a paper.
     */
    public void saveEntities(int paperId, Map<EntityType, Set<String>> entities) {
        if (!dbManager.isAvailable() || entities == null) return;

        String sql = """
            INSERT OR REPLACE INTO paper_entities (paper_id, entity_type, entity_value)
            VALUES (?, ?, ?)
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<EntityType, Set<String>> entry : entities.entrySet()) {
                    for (String value : entry.getValue()) {
                        stmt.setInt(1, paperId);
                        stmt.setString(2, entry.getKey().name());
                        stmt.setString(3, value);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[EntityDAO] Error saving entities for paper " + paperId + ": " + e.getMessage());
        }
    }

    /**
     * Load all cached entities for all papers.
     *
     * @return map of paperId → (entityType → set of entity values)
     */
    public Map<Integer, Map<EntityType, Set<String>>> getAllEntities() {
        Map<Integer, Map<EntityType, Set<String>>> result = new HashMap<>();
        if (!dbManager.isAvailable()) return result;

        String sql = "SELECT paper_id, entity_type, entity_value FROM paper_entities";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int paperId = rs.getInt("paper_id");
                    String typeStr = rs.getString("entity_type");
                    String value = rs.getString("entity_value");

                    EntityType type;
                    try {
                        type = EntityType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        continue; // Skip unknown types
                    }

                    result.computeIfAbsent(paperId, k -> new EnumMap<>(EntityType.class))
                          .computeIfAbsent(type, k -> new LinkedHashSet<>())
                          .add(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EntityDAO] Error loading entities: " + e.getMessage());
        }

        return result;
    }

    /**
     * Load entities for a single paper.
     */
    public Map<EntityType, Set<String>> getEntitiesForPaper(int paperId) {
        Map<EntityType, Set<String>> result = new EnumMap<>(EntityType.class);
        if (!dbManager.isAvailable()) return result;

        String sql = "SELECT entity_type, entity_value FROM paper_entities WHERE paper_id = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, paperId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    EntityType type;
                    try {
                        type = EntityType.valueOf(rs.getString("entity_type"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    result.computeIfAbsent(type, k -> new LinkedHashSet<>())
                          .add(rs.getString("entity_value"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EntityDAO] Error loading entities for paper " + paperId + ": " + e.getMessage());
        }

        return result;
    }

    /**
     * Delete cached entities for a paper (e.g., when paper is removed).
     */
    public void deleteEntities(int paperId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM paper_entities WHERE paper_id = ?")) {
                stmt.setInt(1, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[EntityDAO] Error deleting entities for paper " + paperId + ": " + e.getMessage());
        }
    }
}
