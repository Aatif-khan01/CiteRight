package com.citeright.database;

import java.sql.*;

/**
 * Manages MySQL database connections and schema initialization.
 * Falls back to offline mode if MySQL is not accessible.
 * 
 * Demonstrates: SINGLETON PATTERN — only one database connection manager exists.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/citeright_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "4Ginternet@";
    private static final String CREATE_DB_URL = "jdbc:mysql://localhost:3306/";

    private static DatabaseManager instance;
    private Connection connection;
    private boolean available = false;

    // Private constructor — Singleton pattern
    private DatabaseManager() {
        // Try to connect on startup
        try {
            createDatabaseIfNotExists();
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            createTablesIfNotExist();
            available = true;
            System.out.println("[DB] Connected successfully!");
        } catch (Exception e) {
            available = false;
            System.out.println("[DB] MySQL not available. Running in offline mode.");
            System.out.println("[DB] (Search will work via API. History/caching disabled.)");
            System.out.println("[DB] To enable DB, update password in DatabaseManager.java");
        }
    }

    /**
     * Returns the single instance of DatabaseManager.
     * Demonstrates: SINGLETON PATTERN
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Returns whether the database is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets a database connection. Returns null if DB is not available.
     */
    public Connection getConnection() throws SQLException {
        if (!available) return null;
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (SQLException e) {
                available = false;
                return null;
            }
        }
        return connection;
    }

    /**
     * Creates the citeright_db database if it doesn't exist.
     */
    private void createDatabaseIfNotExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection(CREATE_DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS citeright_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("[DB] Database 'citeright_db' ready.");
        }
    }

    /**
     * Creates all required tables.
     */
    private void createTablesIfNotExist() {
        try (Statement stmt = connection.createStatement()) {

            // Papers table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS papers (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    paper_id VARCHAR(100) UNIQUE,
                    title VARCHAR(500) NOT NULL,
                    abstract_text TEXT,
                    year INT,
                    citation_count INT DEFAULT 0,
                    doi VARCHAR(200),
                    url VARCHAR(500),
                    venue VARCHAR(300),
                    publication_type VARCHAR(50) DEFAULT 'JOURNAL',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Authors table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS authors (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    author_id VARCHAR(100),
                    name VARCHAR(200) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Paper-Author relationship (many-to-many)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS paper_authors (
                    paper_id INT,
                    author_id INT,
                    author_order INT DEFAULT 0,
                    PRIMARY KEY (paper_id, author_id),
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
                    FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // User's saved library
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_library (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    paper_id INT,
                    collection_name VARCHAR(100) DEFAULT 'Default',
                    notes TEXT,
                    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Search history
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS search_history (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    query_text TEXT NOT NULL,
                    extracted_keywords VARCHAR(500),
                    result_count INT DEFAULT 0,
                    searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            System.out.println("[DB] All tables ready.");

        } catch (SQLException e) {
            System.err.println("[DB] Error creating tables: " + e.getMessage());
        }
    }

    /**
     * Closes the database connection.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error closing connection: " + e.getMessage());
        }
    }

    /**
     * Tests if the database connection is working.
     */
    public boolean testConnection() {
        return available;
    }
}
