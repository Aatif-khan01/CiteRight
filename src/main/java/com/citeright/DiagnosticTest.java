package com.citeright;

import com.citeright.database.SQLiteDatabaseManager;
import com.citeright.model.*;
import com.citeright.service.*;

import java.io.File;
import java.util.List;

/**
 * Diagnostic tool — tests the full import/save/load flow from command line.
 * Run: java -cp target/citeright-1.0-SNAPSHOT.jar com.citeright.DiagnosticTest
 */
public class DiagnosticTest {

    public static void main(String[] args) {
        System.out.println("=== CiteRight Diagnostic Test ===\n");

        // Step 1: Database
        System.out.println("[1] Testing database connection...");
        SQLiteDatabaseManager db = SQLiteDatabaseManager.getInstance();
        System.out.println("    Available: " + db.isAvailable());
        try {
            java.sql.Connection conn = db.getConnection();
            System.out.println("    Connection: " + (conn != null ? "OK" : "FAILED (null)"));
            if (conn != null) {
                // Test basic query
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM collections");
                if (rs.next()) System.out.println("    Collections: " + rs.getInt(1));
                rs = stmt.executeQuery("SELECT COUNT(*) FROM papers");
                if (rs.next()) System.out.println("    Papers in DB: " + rs.getInt(1));
                rs = stmt.executeQuery("SELECT COUNT(*) FROM user_library");
                if (rs.next()) System.out.println("    Library entries: " + rs.getInt(1));
                conn.close();
            }
        } catch (Exception e) {
            System.out.println("    ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 2: Parse BibTeX
        System.out.println("\n[2] Testing BibTeX parser...");
        ImportExportService ies = new ImportExportService();
        try {
            File testFile = new File("test_refs.bib");
            if (!testFile.exists()) {
                System.out.println("    ERROR: test_refs.bib not found at " + testFile.getAbsolutePath());
                return;
            }
            List<Publication> parsed = ies.importAuto(testFile);
            System.out.println("    Parsed: " + parsed.size() + " papers");
            for (Publication p : parsed) {
                System.out.println("    - Title: " + p.getTitle());
                System.out.println("      Authors: " + p.getAuthorsFormatted());
                System.out.println("      Year: " + p.getYear());
                System.out.println("      DOI: " + p.getDoi());
                System.out.println("      PaperId: " + p.getPaperId());
            }
        } catch (Exception e) {
            System.out.println("    PARSE ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 3: Save to library
        System.out.println("\n[3] Testing save to library...");
        LibraryService ls = new LibraryService();
        try {
            File testFile = new File("test_refs.bib");
            List<Publication> parsed = ies.importAuto(testFile);
            int saved = 0;
            for (Publication pub : parsed) {
                System.out.println("    Saving: " + pub.getTitle() + " (id: " + pub.getPaperId() + ")");
                boolean alreadyIn = ls.isInLibrary(pub.getPaperId());
                System.out.println("    Already in library: " + alreadyIn);
                if (!alreadyIn) {
                    ls.saveToDefaultCollection(pub);
                    saved++;
                    System.out.println("    Saved OK");
                }
            }
            System.out.println("    Total saved: " + saved);
        } catch (Exception e) {
            System.out.println("    SAVE ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 4: Load from library
        System.out.println("\n[4] Testing load from library...");
        try {
            List<LibraryEntry> entries = ls.getAllActive();
            System.out.println("    Active entries: " + entries.size());
            for (LibraryEntry e : entries) {
                Publication p = e.getPublication();
                System.out.println("    - Title: " + (p != null ? p.getTitle() : "null"));
                System.out.println("      Authors: " + (p != null ? p.getAuthorsFormatted() : "null"));
                System.out.println("      Favorite: " + e.isFavorite());
            }
        } catch (Exception e) {
            System.out.println("    LOAD ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 5: Verify DB state
        System.out.println("\n[5] Final DB state...");
        try {
            java.sql.Connection conn = db.getConnection();
            if (conn != null) {
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM papers");
                if (rs.next()) System.out.println("    Papers: " + rs.getInt(1));
                rs = stmt.executeQuery("SELECT COUNT(*) FROM user_library");
                if (rs.next()) System.out.println("    Library entries: " + rs.getInt(1));
                rs = stmt.executeQuery("SELECT COUNT(*) FROM authors");
                if (rs.next()) System.out.println("    Authors: " + rs.getInt(1));
                rs = stmt.executeQuery("SELECT COUNT(*) FROM paper_authors");
                if (rs.next()) System.out.println("    Paper-Author links: " + rs.getInt(1));
                conn.close();
            }
        } catch (Exception e) {
            System.out.println("    ERROR: " + e.getMessage());
        }

        System.out.println("\n=== Diagnostic Complete ===");
    }
}
