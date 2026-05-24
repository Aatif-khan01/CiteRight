package com.citeright.database;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for handling cached BGE-M3 (or other neural) embeddings in SQLite.
 * Stores 1024-dimensional vectors efficiently as binary BLOBs (4096 bytes).
 */
public class PaperEmbeddingDAO {

    private final SQLiteDatabaseManager dbManager;

    public PaperEmbeddingDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Saves or updates a paper's embedding vector.
     * Converts float[] to raw byte[] BLOB.
     */
    public void saveEmbedding(int paperId, String modelName, String modelVersion, float[] vector) {
        if (!dbManager.isAvailable() || vector == null || vector.length == 0) return;

        String sql = """
            INSERT INTO paper_embeddings (paper_id, model_name, model_version, vector_blob)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(paper_id) DO UPDATE SET
                model_name = excluded.model_name,
                model_version = excluded.model_version,
                vector_blob = excluded.vector_blob,
                created_at = CURRENT_TIMESTAMP
        """;

        byte[] blobBytes = floatArrayToByteArray(vector);

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, paperId);
                stmt.setString(2, modelName);
                stmt.setString(3, modelVersion);
                stmt.setBytes(4, blobBytes);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[PaperEmbeddingDAO] Error saving embedding for paper " + paperId + ": " + e.getMessage());
        }
    }

    /**
     * Retrieves the embedding vector for a single paper.
     */
    public float[] getEmbedding(int paperId, String modelName, String modelVersion) {
        if (!dbManager.isAvailable()) return null;

        String sql = "SELECT vector_blob FROM paper_embeddings WHERE paper_id = ? AND model_name = ? AND model_version = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, paperId);
                stmt.setString(2, modelName);
                stmt.setString(3, modelVersion);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        byte[] blobBytes = rs.getBytes("vector_blob");
                        return byteArrayToFloatArray(blobBytes);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[PaperEmbeddingDAO] Error fetching embedding for paper " + paperId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Loads ALL cached embeddings for a given model and version in a SINGLE database query.
     * Essential for high-performance library similarity rendering and clustering.
     */
    public Map<Integer, float[]> getAllCachedEmbeddings(String modelName, String modelVersion) {
        Map<Integer, float[]> cacheMap = new HashMap<>();
        if (!dbManager.isAvailable()) return cacheMap;

        String sql = "SELECT paper_id, vector_blob FROM paper_embeddings WHERE model_name = ? AND model_version = ?";

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return cacheMap;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, modelName);
                stmt.setString(2, modelVersion);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int paperId = rs.getInt("paper_id");
                        byte[] blobBytes = rs.getBytes("vector_blob");
                        float[] vector = byteArrayToFloatArray(blobBytes);
                        if (vector != null) {
                            cacheMap.put(paperId, vector);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[PaperEmbeddingDAO] Error loading all cached embeddings: " + e.getMessage());
        }
        return cacheMap;
    }

    /**
     * Deletes embedding cache for a paper.
     */
    public void deleteEmbedding(int paperId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM paper_embeddings WHERE paper_id = ?")) {
                stmt.setInt(1, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[PaperEmbeddingDAO] Error deleting embedding: " + e.getMessage());
        }
    }

    // ── Helper Serialization Methods ─────────────────────────────────────────

    private static byte[] floatArrayToByteArray(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * 4);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(floatArray);
        return byteBuffer.array();
    }

    private static float[] byteArrayToFloatArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length % 4 != 0) return null;
        int size = byteArray.length / 4;
        float[] floatArray = new float[size];
        ByteBuffer.wrap(byteArray).asFloatBuffer().get(floatArray);
        return floatArray;
    }
}
