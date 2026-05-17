package com.citeright.database;

import com.citeright.model.Collection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Collection operations.
 * Handles CRUD for paper collections/folders in SQLite.
 * 
 * Demonstrates: DAO PATTERN — separates data access from business logic.
 */
public class CollectionDAO {

    private final SQLiteDatabaseManager dbManager;

    public CollectionDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Creates a new collection.
     * Returns the created collection with its generated ID.
     */
    public Collection create(Collection collection) {
        if (!dbManager.isAvailable()) return collection;

        String sql = "INSERT INTO collections (name, description, color, parent_id, is_smart, smart_query) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return collection;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, collection.getName());
            stmt.setString(2, collection.getDescription());
            stmt.setString(3, collection.getColor());
            if (collection.getParentId() != null) {
                stmt.setInt(4, collection.getParentId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setBoolean(5, collection.isSmart());
            stmt.setString(6, collection.getSmartQuery());

            stmt.executeUpdate();

                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    collection.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error creating collection: " + e.getMessage());
        }

        return collection;
    }

    public Collection createSmartCollection(String name, String query) {
        Collection collection = new Collection(name);
        collection.setSmart(true);
        collection.setSmartQuery(query);
        collection.setColor("#9b59b6"); // purple for smart collections
        return create(collection);
    }

    /**
     * Gets all collections.
     */
    public List<Collection> getAll() {
        List<Collection> collections = new ArrayList<>();
        if (!dbManager.isAvailable()) return collections;

        String sql = """
            SELECT c.*, 
                   (SELECT COUNT(*) FROM user_library ul WHERE ul.collection_id = c.id) AS paper_count
            FROM collections c 
            ORDER BY c.name
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return collections;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    collections.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error fetching collections: " + e.getMessage());
        }

        return collections;
    }

    /**
     * Gets a collection by ID.
     */
    public Collection getById(int id) {
        if (!dbManager.isAvailable()) return null;

        String sql = """
            SELECT c.*, 
                   (SELECT COUNT(*) FROM user_library ul WHERE ul.collection_id = c.id) AS paper_count
            FROM collections c WHERE c.id = ?
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error fetching collection: " + e.getMessage());
        }

        return null;
    }

    /**
     * Renames a collection.
     */
    public void rename(int id, String newName) {
        if (!dbManager.isAvailable()) return;

        String sql = "UPDATE collections SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newName);
                stmt.setInt(2, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error renaming collection: " + e.getMessage());
        }
    }

    /**
     * Updates a collection's color.
     */
    public void updateColor(int id, String color) {
        if (!dbManager.isAvailable()) return;

        String sql = "UPDATE collections SET color = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, color);
                stmt.setInt(2, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error updating collection color: " + e.getMessage());
        }
    }

    /**
     * Deletes a collection. Papers in the collection are NOT deleted,
     * their collection_id is set to NULL.
     */
    public void delete(int id) {
        if (!dbManager.isAvailable()) return;

        // First, unlink papers from this collection
        String unlinkSql = "UPDATE user_library SET collection_id = NULL WHERE collection_id = ?";
        String deleteSql = "DELETE FROM collections WHERE id = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;

            try (PreparedStatement stmt = conn.prepareStatement(unlinkSql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error deleting collection: " + e.getMessage());
        }
    }

    /**
     * Gets child collections of a parent.
     */
    public List<Collection> getChildren(int parentId) {
        List<Collection> children = new ArrayList<>();
        if (!dbManager.isAvailable()) return children;

        String sql = """
            SELECT c.*, 
                   (SELECT COUNT(*) FROM user_library ul WHERE ul.collection_id = c.id) AS paper_count
            FROM collections c WHERE c.parent_id = ?
            ORDER BY c.name
        """;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return children;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, parentId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    children.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CollectionDAO] Error fetching children: " + e.getMessage());
        }

        return children;
    }

    private Collection mapResultSet(ResultSet rs) throws SQLException {
        Collection c = new Collection();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setColor(rs.getString("color"));
        int parentId = rs.getInt("parent_id");
        c.setParentId(rs.wasNull() ? null : parentId);
        c.setPaperCount(rs.getInt("paper_count"));
        
        // Handle migration states where column might not exist in old resultsets
        try {
            c.setSmart(rs.getBoolean("is_smart"));
            c.setSmartQuery(rs.getString("smart_query"));
        } catch (SQLException ignored) {}
        
        return c;
    }
}
