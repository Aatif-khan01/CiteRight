package com.citeright.database;

import com.citeright.model.*;
import com.citeright.model.LibraryEntry.ReadStatus;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Library operations.
 * Manages the user's personal paper library in SQLite.
 * 
 * Demonstrates: DAO PATTERN
 */
public class LibraryDAO {

    private final SQLiteDatabaseManager dbManager;

    public LibraryDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Saves a paper to the user's library.
     * First ensures the paper exists in the papers table, then adds a library entry.
     */
    public void saveToLibrary(Publication paper, int collectionId) {
        if (!dbManager.isAvailable()) return;

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;

            // Step 1: Ensure paper exists in papers table
            int dbPaperId = ensurePaperExists(conn, paper);
            if (dbPaperId <= 0) return;

            // Step 2: Check if already in library
            boolean isActive = false;
            boolean isDeleted = false;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT is_deleted FROM user_library WHERE paper_id = ?")) {
                stmt.setInt(1, dbPaperId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int deletedFlag = rs.getInt(1);
                    if (rs.wasNull() || deletedFlag == 0) isActive = true;
                    else isDeleted = true;
                }
            }

            if (isActive) {
                System.out.println("[LibraryDAO] Paper already in library: " + paper.getTitle());
                return;
            }

            if (isDeleted) {
                System.out.println("[LibraryDAO] Restoring deleted paper: " + paper.getTitle());
                String sql = "UPDATE user_library SET is_deleted = 0, added_at = datetime('now'), collection_id = ? WHERE paper_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, collectionId);
                    stmt.setInt(2, dbPaperId);
                    stmt.executeUpdate();
                }
            } else {
                // Step 3: Add to user_library
                String sql = "INSERT INTO user_library (paper_id, collection_id) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, dbPaperId);
                    stmt.setInt(2, collectionId);
                    stmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error saving to library: " + e.getMessage());
        }
    }

    /**
     * Removes a paper from the library by its DB paper ID.
     */
    public void removeFromLibrary(int paperId) {
        if (!dbManager.isAvailable()) return;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM user_library WHERE paper_id = ?")) {
                stmt.setInt(1, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error removing: " + e.getMessage());
        }
    }

    /**
     * Checks if a paper (by paper_id string) is in the library.
     */
    public boolean isInLibraryByPaperId(String paperId) {
        if (!dbManager.isAvailable()) return false;
        // If paperId is null, we can't do a fast lookup — caller must rely on ensurePaperExists dedup
        if (paperId == null) return false;
        String sql = "SELECT COUNT(*) FROM user_library ul INNER JOIN papers p ON ul.paper_id = p.id " +
                     "WHERE p.paper_id = ? AND (ul.is_deleted IS NULL OR ul.is_deleted = 0)";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, paperId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
        return false;
    }

    private static final String SELECT_LIBRARY_SQL = 
        "SELECT ul.id as ul_id, ul.notes, ul.is_favorite, ul.read_status, ul.collection_id, ul.added_at, " +
        "p.id as p_id, p.paper_id as p_paper_id, p.title, p.abstract_text, p.year, p.citation_count, " +
        "p.doi, p.url, p.venue, p.publication_type, c.name as collection_name " +
        "FROM user_library ul " +
        "INNER JOIN papers p ON ul.paper_id = p.id " +
        "LEFT JOIN collections c ON ul.collection_id = c.id ";

    /**
     * Gets all library entries, optionally filtered.
     */
    public List<LibraryEntry> getAll(Integer collectionId, String readStatus, Boolean favoritesOnly) {
        List<LibraryEntry> entries = new ArrayList<>();
        if (!dbManager.isAvailable()) return entries;

        StringBuilder sql = new StringBuilder(SELECT_LIBRARY_SQL + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (collectionId != null) {
            sql.append(" AND ul.collection_id = ?");
            params.add(collectionId);
        }
        if (readStatus != null && !readStatus.isEmpty()) {
            sql.append(" AND ul.read_status = ?");
            params.add(readStatus);
        }
        if (favoritesOnly != null && favoritesOnly) {
            sql.append(" AND ul.is_favorite = 1");
        }
        sql.append(" ORDER BY ul.added_at DESC");

        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return entries;
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof Integer) stmt.setInt(i + 1, (Integer) param);
                    else stmt.setString(i + 1, param.toString());
                }

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(mapLibraryEntry(rs));
                }
            }
            loadAuthorsForEntries(conn, entries);
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error fetching library: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Toggles favorite status for a paper.
     */
    public void toggleFavorite(int paperId) {
        if (!dbManager.isAvailable()) return;
        String sql = "UPDATE user_library SET is_favorite = CASE WHEN is_favorite = 1 THEN 0 ELSE 1 END WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
    }

    /**
     * Updates read status for a paper.
     */
    public void updateReadStatus(int paperId, ReadStatus status) {
        if (!dbManager.isAvailable()) return;
        String sql = "UPDATE user_library SET read_status = ? WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                stmt.setInt(2, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
    }

    /**
     * Updates notes for a paper.
     */
    public void updateNotes(int paperId, String notes) {
        if (!dbManager.isAvailable()) return;
        String sql = "UPDATE user_library SET notes = ? WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, notes);
                stmt.setInt(2, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
    }

    /**
     * Moves a paper to a different collection.
     */
    public void moveToCollection(int paperId, int newCollectionId) {
        if (!dbManager.isAvailable()) return;
        String sql = "UPDATE user_library SET collection_id = ? WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newCollectionId);
                stmt.setInt(2, paperId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
    }

    /**
     * Updates the core metadata fields of a paper (title, year, venue, doi, url, abstract, authors).
     * Used by the edit dialog in DetailPanel.
     *
     * @param dbPaperId The integer primary key in the papers table.
     * @param pub       The publication object containing updated values.
     */
    public void updateMetadata(int dbPaperId, com.citeright.model.Publication pub) {
        if (!dbManager.isAvailable()) return;
        String sql = "UPDATE papers SET title=?, year=?, venue=?, doi=?, url=?, abstract_text=? WHERE id=?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, pub.getTitle());
                stmt.setInt(2, pub.getYear());
                stmt.setString(3, pub.getVenue());
                stmt.setString(4, pub.getDoi());
                stmt.setString(5, pub.getUrl());
                stmt.setString(6, pub.getAbstractText());
                stmt.setInt(7, dbPaperId);
                stmt.executeUpdate();
            }

            // Replace authors: delete existing links then re-insert
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM paper_authors WHERE paper_id = ?")) {
                del.setInt(1, dbPaperId);
                del.executeUpdate();
            }

            if (pub.getAuthors() != null) {
                int order = 0;
                for (com.citeright.model.Author author : pub.getAuthors()) {
                    int authorId = ensureAuthorExists(conn, author);
                    if (authorId > 0) {
                        try (PreparedStatement pa = conn.prepareStatement(
                                "INSERT OR IGNORE INTO paper_authors (paper_id, author_id, author_order) VALUES (?, ?, ?)")) {
                            pa.setInt(1, dbPaperId);
                            pa.setInt(2, authorId);
                            pa.setInt(3, order++);
                            pa.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] updateMetadata error: " + e.getMessage());
        }
    }

    /**
     * Gets the total count of papers in the library.
     */
    public int getLibraryCount() {
        if (!dbManager.isAvailable()) return 0;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_library")) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Gets DB paper ID for a paper_id string. Returns -1 if not found.
     */
    public int getDbPaperId(String paperId) {
        if (!dbManager.isAvailable() || paperId == null) return -1;
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return -1;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM papers WHERE paper_id = ?")) {
                stmt.setString(1, paperId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error: " + e.getMessage());
        }
        return -1;
    }

    // --- Private helpers ---

    /**
     * Generates a stable synthetic paper ID for papers that don't have one.
     * Uses title + year so the same paper always gets the same synthetic ID.
     */
    private String generateSyntheticId(Publication paper) {
        String title = paper.getTitle() != null ? paper.getTitle().toLowerCase().trim() : "";
        int year = paper.getYear();
        // A simple but stable hash-based ID
        return "local-" + Math.abs((title + year).hashCode());
    }

    private int ensurePaperExists(Connection conn, Publication paper) throws SQLException {
        // Assign a stable synthetic ID if none exists
        if (paper.getPaperId() == null || paper.getPaperId().isBlank()) {
            paper.setPaperId(generateSyntheticId(paper));
        }

        // 4) Not found — insert as new paper
        int dbPaperId = -1;
        // Check by title + year since they shouldn't be duplicate
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM papers WHERE paper_id = ?")) {
            stmt.setString(1, paper.getPaperId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) dbPaperId = rs.getInt(1);
        }

        if (dbPaperId == -1 && paper.getDoi() != null && !paper.getDoi().isBlank()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM papers WHERE doi = ?")) {
                stmt.setString(1, paper.getDoi());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbPaperId = rs.getInt(1);
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE papers SET paper_id = ? WHERE id = ? AND (paper_id IS NULL OR paper_id = '')")) {
                        upd.setString(1, paper.getPaperId());
                        upd.setInt(2, dbPaperId);
                        upd.executeUpdate();
                    }
                }
            }
        }

        if (dbPaperId == -1 && paper.getTitle() != null && !paper.getTitle().isBlank()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM papers WHERE LOWER(title) = LOWER(?) AND year = ?")) {
                stmt.setString(1, paper.getTitle().trim());
                stmt.setInt(2, paper.getYear());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) dbPaperId = rs.getInt(1);
            }
        }

        if (dbPaperId == -1) {
            String insertSql = """
                INSERT INTO papers (paper_id, title, abstract_text, year, citation_count, doi, url, venue, publication_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
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

                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) dbPaperId = keys.getInt(1);
            }
        }

        // 5) Always ensure authors are saved and linked to this dbPaperId
        // This was previously skipping if the paper was already cached by a search!
        if (dbPaperId > 0 && paper.getAuthors() != null) {
            int order = 0;
            for (Author author : paper.getAuthors()) {
                int authorId = ensureAuthorExists(conn, author);
                if (authorId > 0) {
                    try (PreparedStatement paStmt = conn.prepareStatement(
                            "INSERT OR IGNORE INTO paper_authors (paper_id, author_id, author_order) VALUES (?, ?, ?)")) {
                        paStmt.setInt(1, dbPaperId);
                        paStmt.setInt(2, authorId);
                        paStmt.setInt(3, order++);
                        paStmt.executeUpdate();
                    }
                }
            }
        }
        return dbPaperId;
    }

    private int ensureAuthorExists(Connection conn, Author author) throws SQLException {
        // Try to find existing by name
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM authors WHERE name = ?")) {
            stmt.setString(1, author.getName());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        // Insert new
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO authors (author_id, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, author.getAuthorId());
            stmt.setString(2, author.getName());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    private void loadAuthorsForEntries(Connection conn, List<LibraryEntry> entries) {
        if (entries.isEmpty()) return;
        String sql = "SELECT a.author_id, a.name FROM authors a INNER JOIN paper_authors pa ON a.id = pa.author_id " +
                     "WHERE pa.paper_id = ? ORDER BY pa.author_order";
        try (PreparedStatement authStmt = conn.prepareStatement(sql)) {
            for (LibraryEntry entry : entries) {
                authStmt.setInt(1, entry.getId());
                try (ResultSet authRs = authStmt.executeQuery()) {
                    while (authRs.next()) {
                        entry.getPublication().addAuthor(new Author(authRs.getString("author_id"), authRs.getString("name")));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error loading authors: " + e.getMessage());
        }
    }

    private LibraryEntry mapLibraryEntry(ResultSet rs) throws SQLException {
        JournalArticle paper = new JournalArticle();
        paper.setPaperId(rs.getString("p_paper_id"));
        paper.setTitle(rs.getString("title"));
        paper.setAbstractText(rs.getString("abstract_text"));
        paper.setYear(rs.getInt("year"));
        paper.setCitationCount(rs.getInt("citation_count"));
        paper.setDoi(rs.getString("doi"));
        paper.setUrl(rs.getString("url"));
        paper.setVenue(rs.getString("venue"));
        paper.setInLibrary(true);

        int dbPaperId = rs.getInt("p_id");

        LibraryEntry entry = new LibraryEntry(paper);
        entry.setId(dbPaperId);
        entry.setCollectionId(rs.getInt("collection_id"));
        entry.setNotes(rs.getString("notes"));
        entry.setFavorite(rs.getInt("is_favorite") == 1);
        entry.setReadStatus(ReadStatus.fromString(rs.getString("read_status")));
        try {
            entry.setCollectionName(rs.getString("collection_name"));
        } catch (SQLException e) { /* column may not exist in some queries */ }
        return entry;
    }

    // === Smart Folder Queries ===

    public List<LibraryEntry> getSmartCollectionPapers(String smartQuery, String readStatus, Boolean favoritesOnly) {
        if (!dbManager.isAvailable()) return new ArrayList<>();

        String filterSql = SmartQueryParser.parseToSqlWhere(smartQuery);
        StringBuilder sql = new StringBuilder(SELECT_LIBRARY_SQL + " WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) AND (");
        sql.append(filterSql).append(")");

        List<Object> params = new ArrayList<>();
        if (readStatus != null && !readStatus.isEmpty()) {
            sql.append(" AND ul.read_status = ?");
            params.add(readStatus);
        }
        if (favoritesOnly != null && favoritesOnly) {
            sql.append(" AND ul.is_favorite = 1");
        }
        sql.append(" ORDER BY ul.added_at DESC");

        List<LibraryEntry> entries = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return entries;
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof Integer) stmt.setInt(i + 1, (Integer) param);
                    else stmt.setString(i + 1, param.toString());
                }
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(mapLibraryEntry(rs));
                }
            }
            loadAuthorsForEntries(conn, entries);
        } catch (SQLException e) {
            System.err.println("[LibraryDAO] Error fetching smart collection: " + e.getMessage());
        }
        return entries;
    }

    public List<LibraryEntry> getRecentlyAdded(int days) {
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) AND ul.added_at >= datetime('now', '-" + days + " days') ORDER BY ul.added_at DESC";
        return queryEntries(sql);
    }

    public List<LibraryEntry> getRecentlyRead(int days) {
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) AND ul.last_read_at >= datetime('now', '-" + days + " days') ORDER BY ul.last_read_at DESC";
        return queryEntries(sql);
    }

    public List<LibraryEntry> getMyPublications() {
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) AND ul.is_my_publication = 1 ORDER BY p.year DESC";
        return queryEntries(sql);
    }

    public List<LibraryEntry> getUnsorted() {
        // Unsorted = papers NOT in any user-created collection (only default/null)
        // Default collection is id=1 ("My Library"). User-created collections have id > 1.
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) AND (ul.collection_id IS NULL OR ul.collection_id <= 1) ORDER BY ul.added_at DESC";
        return queryEntries(sql);
    }

    public List<LibraryEntry> getTrashed() {
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE ul.is_deleted = 1 ORDER BY ul.deleted_at DESC";
        return queryEntries(sql);
    }

    public List<LibraryEntry> getAllActive() {
        String sql = SELECT_LIBRARY_SQL + 
                "WHERE (ul.is_deleted IS NULL OR ul.is_deleted = 0) ORDER BY ul.added_at DESC";
        return queryEntries(sql);
    }

    private List<LibraryEntry> queryEntries(String sql) {
        List<LibraryEntry> entries = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return entries;
            try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) entries.add(mapLibraryEntry(rs));
            }
            loadAuthorsForEntries(conn, entries);
        } catch (SQLException e) { System.err.println("[LibraryDAO] query error: " + e.getMessage()); }
        return entries;
    }

    // === Soft Delete / Trash ===

    public void softDelete(int paperId) {
        String sql = "UPDATE user_library SET is_deleted = 1, deleted_at = datetime('now') WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, paperId); stmt.executeUpdate(); }
        } catch (SQLException e) { System.err.println("[LibraryDAO] softDelete error: " + e.getMessage()); }
    }

    public void restore(int paperId) {
        String sql = "UPDATE user_library SET is_deleted = 0, deleted_at = NULL WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, paperId); stmt.executeUpdate(); }
        } catch (SQLException e) { System.err.println("[LibraryDAO] restore error: " + e.getMessage()); }
    }

    public void permanentDelete(int paperId) {
        String sql = "DELETE FROM user_library WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, paperId); stmt.executeUpdate(); }
        } catch (SQLException e) { System.err.println("[LibraryDAO] permanentDelete error: " + e.getMessage()); }
    }

    public void markAsMyPublication(int paperId, boolean isMine) {
        String sql = "UPDATE user_library SET is_my_publication = ? WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, isMine ? 1 : 0); stmt.setInt(2, paperId); stmt.executeUpdate(); }
        } catch (SQLException e) { System.err.println("[LibraryDAO] markAsMyPublication error: " + e.getMessage()); }
    }

    public void updateLastReadAt(int paperId) {
        String sql = "UPDATE user_library SET last_read_at = datetime('now') WHERE paper_id = ?";
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, paperId); stmt.executeUpdate(); }
        } catch (SQLException e) { System.err.println("[LibraryDAO] updateLastReadAt error: " + e.getMessage()); }
    }
}

