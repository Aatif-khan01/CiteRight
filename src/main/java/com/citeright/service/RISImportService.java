package com.citeright.service;

import com.citeright.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Imports publications from RIS (.ris) files.
 * 
 * RIS is a tag-based format used by Endnote, Mendeley, Zotero, and most
 * academic databases. Each tag is a 2-character code followed by "  - " and a value.
 */
public class RISImportService {

    /**
     * Parses a .ris file and returns a list of Publication objects.
     */
    public List<Publication> importFromFile(Path risFile) throws Exception {
        List<String> lines = Files.readAllLines(risFile, StandardCharsets.UTF_8);
        return parseLines(lines);
    }

    /**
     * Parses RIS content from a string.
     */
    public List<Publication> importFromString(String risContent) {
        List<String> lines = Arrays.asList(risContent.split("\\r?\\n"));
        return parseLines(lines);
    }

    /**
     * Core parsing logic — processes RIS tag lines into Publication objects.
     */
    private List<Publication> parseLines(List<String> lines) {
        List<Publication> publications = new ArrayList<>();
        Map<String, List<String>> currentEntry = null;
        int entryCount = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // RIS tags are exactly: "XX  - value"
            if (line.length() >= 6 && line.charAt(2) == ' ' && line.charAt(3) == ' '
                    && line.charAt(4) == '-' && line.charAt(5) == ' ') {
                String tag = line.substring(0, 2).toUpperCase();
                String value = line.substring(6).trim();

                if ("TY".equals(tag)) {
                    // Start of a new entry
                    currentEntry = new LinkedHashMap<>();
                    currentEntry.put("TY", new ArrayList<>(List.of(value)));
                } else if ("ER".equals(tag)) {
                    // End of entry — convert and add
                    if (currentEntry != null) {
                        try {
                            entryCount++;
                            Publication pub = convertEntry(currentEntry, entryCount);
                            if (pub != null) publications.add(pub);
                        } catch (Exception e) {
                            System.err.println("[RIS Import] Skipping entry #" + entryCount + ": " + e.getMessage());
                        }
                        currentEntry = null;
                    }
                } else if (currentEntry != null) {
                    // Add tag value (some tags like AU can appear multiple times)
                    currentEntry.computeIfAbsent(tag, k -> new ArrayList<>()).add(value);
                }
            }
        }

        System.out.println("[RIS Import] Parsed " + publications.size() + " entries from RIS.");
        return publications;
    }

    /**
     * Converts a parsed RIS entry (map of tag → values) into a Publication.
     */
    private Publication convertEntry(Map<String, List<String>> entry, int index) {
        String type = getFirst(entry, "TY");

        Publication pub;
        switch (type != null ? type.toUpperCase() : "JOUR") {
            case "BOOK":
            case "SER":
                pub = convertToBook(entry);
                break;
            case "CONF":
            case "CPAPER":
                pub = convertToConferencePaper(entry);
                break;
            default: // JOUR, MGZN, NEWS, GEN, etc.
                pub = convertToJournalArticle(entry);
                break;
        }

        // Common fields
        pub.setPaperId("ris-" + index + "-" + System.currentTimeMillis());
        pub.setTitle(getFirst(entry, "TI", "T1", "CT"));
        pub.setAbstractText(getFirst(entry, "AB", "N2"));
        pub.setDoi(getFirst(entry, "DO", "DOI"));
        pub.setUrl(getFirst(entry, "UR", "L1", "L2"));

        // Year
        String yearStr = getFirst(entry, "PY", "Y1", "DA");
        if (yearStr != null) {
            try {
                // RIS dates can be "2021", "2021/03/15", or "2021///"
                String yearPart = yearStr.split("[/\\-]")[0].trim();
                pub.setYear(Integer.parseInt(yearPart));
            } catch (NumberFormatException ignored) {}
        }

        // Authors — each AU tag is one author
        List<String> authors = entry.getOrDefault("AU", entry.getOrDefault("A1", Collections.emptyList()));
        for (String authorName : authors) {
            if (authorName != null && !authorName.isBlank()) {
                pub.addAuthor(new Author(authorName.trim()));
            }
        }

        // Keywords
        List<String> keywords = entry.getOrDefault("KW", Collections.emptyList());
        // Could be used for tags in the future

        return pub;
    }

    private JournalArticle convertToJournalArticle(Map<String, List<String>> entry) {
        JournalArticle ja = new JournalArticle();
        String journal = getFirst(entry, "JO", "JF", "T2", "JA");
        if (journal != null) {
            ja.setJournalName(journal);
            ja.setVenue(journal);
        }
        ja.setVolume(getFirst(entry, "VL"));
        ja.setIssue(getFirst(entry, "IS", "M1"));

        // Pages: SP (start page) and EP (end page) are separate in RIS
        String sp = getFirst(entry, "SP");
        String ep = getFirst(entry, "EP");
        if (sp != null) {
            ja.setPages(ep != null ? sp + "-" + ep : sp);
        }
        return ja;
    }

    private Book convertToBook(Map<String, List<String>> entry) {
        Book book = new Book();
        book.setPublisher(getFirst(entry, "PB"));
        book.setIsbn(getFirst(entry, "SN"));
        book.setEdition(getFirst(entry, "ET"));
        String venue = getFirst(entry, "PB");
        if (venue != null) book.setVenue(venue);
        return book;
    }

    private ConferencePaper convertToConferencePaper(Map<String, List<String>> entry) {
        ConferencePaper cp = new ConferencePaper();
        String confName = getFirst(entry, "T2", "BT", "CT");
        if (confName != null) {
            cp.setConferenceName(confName);
            cp.setVenue(confName);
        }
        return cp;
    }

    /**
     * Gets the first non-null value from a list of tag keys.
     */
    private String getFirst(Map<String, List<String>> entry, String... keys) {
        for (String key : keys) {
            List<String> values = entry.get(key);
            if (values != null && !values.isEmpty()) {
                String val = values.get(0).trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }
}
