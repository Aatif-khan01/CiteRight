package com.citeright.database;

import java.io.File;
import java.sql.*;

/**
 * Manages SQLite database connections and schema initialization.
 * All data is stored locally in ~/.citeright/library.db — zero server dependency.
 * 
 * CRITICAL FIX: Uses a connection-per-call pattern instead of a single shared
 * connection to avoid try-with-resources accidentally closing the only connection.
 */
public class SQLiteDatabaseManager {

    private static final String APP_DIR_NAME = ".citeright";
    private static final String DB_FILE_NAME = "library.db";
    private static final String PDF_DIR_NAME = "pdfs";

    private static SQLiteDatabaseManager instance;
    private boolean available = false;
    private String appDirPath;
    private String dbFilePath;
    private String pdfDirPath;
    private String dbUrl;

    private SQLiteDatabaseManager() {
        try {
            // Explicitly load SQLite JDBC driver — required in fat JAR because
            // MySQL and SQLite both define META-INF/services/java.sql.Driver,
            // and the shade plugin keeps only one (MySQL's), so auto-discovery fails.
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                System.err.println("[SQLite] CRITICAL: SQLite JDBC driver not found on classpath!");
                throw new RuntimeException("SQLite JDBC driver missing", e);
            }

            appDirPath = System.getProperty("user.home") + File.separator + APP_DIR_NAME;
            dbFilePath = appDirPath + File.separator + DB_FILE_NAME;
            pdfDirPath = appDirPath + File.separator + PDF_DIR_NAME;

            File appDir = new File(appDirPath);
            if (!appDir.exists()) { appDir.mkdirs(); System.out.println("[SQLite] Created: " + appDirPath); }

            File pdfDir = new File(pdfDirPath);
            if (!pdfDir.exists()) { pdfDir.mkdirs(); System.out.println("[SQLite] Created: " + pdfDirPath); }

            dbUrl = "jdbc:sqlite:" + dbFilePath;

            // Test connection + create tables
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA foreign_keys=ON");
                }
                createTablesIfNotExist(conn);
            }

            available = true;
            System.out.println("[SQLite] Database ready: " + dbFilePath);

        } catch (Exception e) {
            available = false;
            System.err.println("[SQLite] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized SQLiteDatabaseManager getInstance() {
        if (instance == null) instance = new SQLiteDatabaseManager();
        return instance;
    }

    public boolean isAvailable() { return available; }

    /**
     * Returns a FRESH connection every time.
     * Callers MUST close this connection (use try-with-resources).
     * This is the correct pattern — each DAO call gets its own connection.
     */
    public Connection getConnection() throws SQLException {
        if (!available) return null;
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    public String getPdfDirPath() { return pdfDirPath; }
    public String getAppDirPath() { return appDirPath; }

    public void close() {
        available = false;
        System.out.println("[SQLite] Manager closed.");
    }

    private void createTablesIfNotExist(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS papers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    paper_id TEXT UNIQUE,
                    title TEXT NOT NULL,
                    abstract_text TEXT,
                    year INTEGER,
                    citation_count INTEGER DEFAULT 0,
                    doi TEXT,
                    url TEXT,
                    venue TEXT,
                    publication_type TEXT DEFAULT 'JOURNAL',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS authors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    author_id TEXT,
                    name TEXT NOT NULL
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_authors (
                    paper_id INTEGER,
                    author_id INTEGER,
                    author_order INTEGER DEFAULT 0,
                    PRIMARY KEY (paper_id, author_id),
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS search_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    query_text TEXT NOT NULL,
                    extracted_keywords TEXT,
                    result_count INTEGER DEFAULT 0,
                    searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS collections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    color TEXT DEFAULT '#4A90D9',
                    parent_id INTEGER,
                    is_smart BOOLEAN DEFAULT 0,
                    smart_query TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (parent_id) REFERENCES collections(id) ON DELETE SET NULL
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_library (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    paper_id INTEGER NOT NULL,
                    collection_id INTEGER,
                    notes TEXT,
                    is_favorite INTEGER DEFAULT 0,
                    read_status TEXT DEFAULT 'UNREAD',
                    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE SET NULL
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    color TEXT DEFAULT '#888888'
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_tags (
                    paper_id INTEGER,
                    tag_id INTEGER,
                    PRIMARY KEY (paper_id, tag_id),
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pdf_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    paper_id INTEGER,
                    file_path TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    file_size INTEGER DEFAULT 0,
                    page_count INTEGER DEFAULT 0,
                    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS annotations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pdf_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    page_number INTEGER NOT NULL,
                    x REAL DEFAULT 0,
                    y REAL DEFAULT 0,
                    width REAL DEFAULT 0,
                    height REAL DEFAULT 0,
                    content TEXT,
                    color TEXT DEFAULT '#FFFF00',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    color TEXT DEFAULT '#6C5CE7',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_groups (
                    paper_id INTEGER,
                    group_id INTEGER,
                    PRIMARY KEY (paper_id, group_id),
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_relationships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source_paper_id INTEGER NOT NULL,
                    target_paper_id INTEGER NOT NULL,
                    relationship_type TEXT NOT NULL,
                    reasoning TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (source_paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    UNIQUE(source_paper_id, target_paper_id, relationship_type)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_pins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    paper_id INTEGER NOT NULL UNIQUE,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    pinned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE
                )
            """);

            // Migrate: add columns if missing
            try { stmt.executeUpdate("ALTER TABLE user_library ADD COLUMN is_deleted INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE user_library ADD COLUMN deleted_at TIMESTAMP"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE user_library ADD COLUMN is_my_publication INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE user_library ADD COLUMN last_read_at TIMESTAMP"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE annotations ADD COLUMN stroke_data TEXT"); } catch (SQLException ignored) {}
            
            // Collections migrations
            try { stmt.executeUpdate("ALTER TABLE collections ADD COLUMN is_smart BOOLEAN DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE collections ADD COLUMN smart_query TEXT"); } catch (SQLException ignored) {}

            // ── Paper Graph: Relationship provenance columns ──
            try { stmt.executeUpdate("ALTER TABLE paper_relationships ADD COLUMN confidence REAL DEFAULT 1.0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE paper_relationships ADD COLUMN source TEXT DEFAULT 'USER'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE paper_relationships ADD COLUMN dismissed INTEGER DEFAULT 0"); } catch (SQLException ignored) {}

            // ── Paper Graph: Workspace notes (freeform sticky notes on canvas) ──
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    width REAL DEFAULT 180,
                    height REAL DEFAULT 100,
                    text TEXT DEFAULT '',
                    color TEXT DEFAULT '#f1c40f',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Paper Graph: Workspace groups (named clusters for argument mapping) ──
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    color TEXT DEFAULT '#6c5ce7',
                    x REAL DEFAULT 0,
                    y REAL DEFAULT 0,
                    width REAL DEFAULT 0,
                    height REAL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_group_papers (
                    group_id INTEGER NOT NULL,
                    paper_id INTEGER NOT NULL,
                    PRIMARY KEY (group_id, paper_id),
                    FOREIGN KEY (group_id) REFERENCES workspace_groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE
                )
            """);

            // ── Paper Graph: Annotated edges (custom-labeled connections on Micro canvas) ──
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_annotated_edges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source_paper_id INTEGER NOT NULL,
                    target_paper_id INTEGER NOT NULL,
                    label TEXT DEFAULT '',
                    color TEXT DEFAULT '#4a9cf7',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (source_paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_paper_id) REFERENCES papers(id) ON DELETE CASCADE
                )
            """);

            // ── Local Semantic Search: Modular, Future-Proof Embeddings Table ──
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_embeddings (
                    paper_id INTEGER PRIMARY KEY,
                    model_name TEXT NOT NULL,
                    model_version TEXT NOT NULL,
                    vector_blob BLOB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE
                )
            """);

            // Default collection
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM collections");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO collections (name, description, color) VALUES ('My Library', 'Default collection', '#4A90D9')");
            }

            // Indexes
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_papers_doi ON papers(doi)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_papers_paper_id ON papers(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_papers_title ON papers(title)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_user_library_paper ON user_library(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_user_library_deleted ON user_library(is_deleted)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_tags_paper ON paper_tags(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pdf_files_paper ON pdf_files(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_groups_paper ON paper_groups(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_rel_source ON paper_relationships(source_paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_rel_target ON paper_relationships(target_paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_rel_dismissed ON paper_relationships(dismissed)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_workspace_pins_paper ON workspace_pins(paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_workspace_group_papers ON workspace_group_papers(group_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_workspace_annotated_src ON workspace_annotated_edges(source_paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_workspace_annotated_tgt ON workspace_annotated_edges(target_paper_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_embeddings_model ON paper_embeddings(model_name, model_version)");

            System.out.println("[SQLite] All tables ready.");
        }
    }
}
