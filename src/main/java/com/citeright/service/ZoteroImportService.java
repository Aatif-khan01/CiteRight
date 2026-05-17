package com.citeright.service;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import javafx.concurrent.Task;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to import papers directly from a local zotero.sqlite database.
 */
public class ZoteroImportService {

    private final LibraryService libraryService;

    public ZoteroImportService(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    /**
     * Imports items from the given zotero.sqlite file.
     * Returns a Task for background execution.
     */
    public Task<Integer> importZoteroDatabase(String zoteroDbPath) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String jdbcUrl = "jdbc:sqlite:" + zoteroDbPath;
                int importedCount = 0;

                try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                    // 1. Get all fields mapping
                    Map<Integer, String> fields = new HashMap<>();
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT fieldID, fieldName FROM fields");
                         ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            fields.put(rs.getInt("fieldID"), rs.getString("fieldName"));
                        }
                    }

                    // 2. Query items and their data
                    String itemSql = """
                        SELECT i.itemID, 
                               (SELECT value FROM itemDataValues idv 
                                JOIN itemData id ON id.valueID = idv.valueID 
                                JOIN fields f ON id.fieldID = f.fieldID 
                                WHERE id.itemID = i.itemID AND f.fieldName = 'title') as title,
                               (SELECT value FROM itemDataValues idv 
                                JOIN itemData id ON id.valueID = idv.valueID 
                                JOIN fields f ON id.fieldID = f.fieldID 
                                WHERE id.itemID = i.itemID AND f.fieldName = 'date') as date,
                               (SELECT value FROM itemDataValues idv 
                                JOIN itemData id ON id.valueID = idv.valueID 
                                JOIN fields f ON id.fieldID = f.fieldID 
                                WHERE id.itemID = i.itemID AND f.fieldName = 'DOI') as doi,
                               (SELECT value FROM itemDataValues idv 
                                JOIN itemData id ON id.valueID = idv.valueID 
                                JOIN fields f ON id.fieldID = f.fieldID 
                                WHERE id.itemID = i.itemID AND f.fieldName = 'abstractNote') as abstractText
                        FROM items i
                        WHERE i.itemTypeID IN (2, 3, 4, 5, 8) -- articles, books, etc
                    """;

                    String authorSql = """
                        SELECT c.firstName, c.lastName 
                        FROM itemCreators ic
                        JOIN creators c ON ic.creatorID = c.creatorID
                        WHERE ic.itemID = ?
                        ORDER BY ic.orderIndex
                    """;

                    try (PreparedStatement itemStmt = conn.prepareStatement(itemSql);
                         PreparedStatement authorStmt = conn.prepareStatement(authorSql);
                         ResultSet itemRs = itemStmt.executeQuery()) {

                        while (itemRs.next()) {
                            int itemId = itemRs.getInt("itemID");
                            String title = itemRs.getString("title");
                            if (title == null || title.isEmpty()) continue; // Skip empty items

                            String dateStr = itemRs.getString("date");
                            int year = extractYear(dateStr);
                            String doi = itemRs.getString("doi");
                            String abs = itemRs.getString("abstractText");

                            com.citeright.model.JournalArticle pub = new com.citeright.model.JournalArticle();
                            pub.setTitle(title);
                            pub.setYear(year);
                            pub.setDoi(doi);
                            pub.setAbstractText(abs);

                            // Get authors
                            authorStmt.setInt(1, itemId);
                            try (ResultSet authorRs = authorStmt.executeQuery()) {
                                List<Author> authors = new ArrayList<>();
                                while (authorRs.next()) {
                                    String f = authorRs.getString("firstName");
                                    String l = authorRs.getString("lastName");
                                    authors.add(new Author(f + " " + l));
                                }
                                pub.setAuthors(authors);
                            }

                            // Save to CiteRight Default Collection
                            libraryService.saveToDefaultCollection(pub);
                            importedCount++;
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[ZoteroImportService] Error during import: " + e.getMessage());
                    e.printStackTrace();
                    throw e; // Rethrow so the Task fails
                }

                return importedCount;
            }
        };
    }

    private int extractYear(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            // Very naive extraction, looks for first 4 digits
            String nums = dateStr.replaceAll("[^0-9]", " ");
            for (String s : nums.split(" ")) {
                if (s.length() == 4) return Integer.parseInt(s);
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
