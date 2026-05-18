package com.citeright.service;

import com.citeright.model.*;
import org.jbibtex.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Imports publications from BibTeX (.bib) files using the jbibtex library.
 * 
 * Handles edge cases: LaTeX escape sequences, multi-line fields, nested braces,
 * and maps BibTeX entry types to our Publication model hierarchy.
 */
public class BibTeXImportService {

    /**
     * Parses a .bib file and returns a list of Publication objects.
     *
     * @param bibFile Path to the .bib file
     * @return List of parsed publications
     * @throws Exception if the file cannot be read or parsed
     */
    public List<Publication> importFromFile(Path bibFile) throws Exception {
        try (Reader reader = Files.newBufferedReader(bibFile, StandardCharsets.UTF_8)) {
            return importFromReader(reader);
        }
    }

    /**
     * Parses BibTeX content from a string.
     */
    public List<Publication> importFromString(String bibtexContent) throws Exception {
        try (Reader reader = new StringReader(bibtexContent)) {
            return importFromReader(reader);
        }
    }

    /**
     * Core parsing logic using jbibtex.
     */
    private List<Publication> importFromReader(Reader reader) throws Exception {
        BibTeXParser parser = new BibTeXParser();
        BibTeXDatabase database = parser.parse(reader);

        List<Publication> publications = new ArrayList<>();
        Map<Key, BibTeXEntry> entries = database.getEntries();

        for (Map.Entry<Key, BibTeXEntry> entry : entries.entrySet()) {
            try {
                Publication pub = convertEntry(entry.getKey(), entry.getValue());
                if (pub != null) {
                    publications.add(pub);
                }
            } catch (Exception e) {
                System.err.println("[BibTeX Import] Skipping entry '" + entry.getKey().getValue()
                        + "': " + e.getMessage());
            }
        }

        System.out.println("[BibTeX Import] Parsed " + publications.size() + " entries from BibTeX.");
        return publications;
    }

    /**
     * Converts a single BibTeX entry into our Publication model.
     */
    private Publication convertEntry(Key citeKey, BibTeXEntry entry) {
        String type = entry.getType().getValue().toLowerCase();

        Publication pub;
        switch (type) {
            case "book":
            case "booklet":
                pub = convertToBook(entry);
                break;
            case "inproceedings":
            case "conference":
                pub = convertToConferencePaper(entry);
                break;
            default: // article, misc, techreport, phdthesis, mastersthesis, etc.
                pub = convertToJournalArticle(entry);
                break;
        }

        // Common fields
        pub.setPaperId("bib-" + citeKey.getValue());
        pub.setTitle(cleanLaTeX(getField(entry, BibTeXEntry.KEY_TITLE)));
        pub.setAbstractText(cleanLaTeX(getField(entry, new Key("abstract"))));
        pub.setDoi(getField(entry, new Key("doi")));
        pub.setUrl(getField(entry, new Key("url")));

        // Year
        String yearStr = getField(entry, BibTeXEntry.KEY_YEAR);
        if (yearStr != null && !yearStr.isEmpty()) {
            try {
                pub.setYear(Integer.parseInt(yearStr.replaceAll("[^0-9]", "").substring(0, 4)));
            } catch (Exception ignored) {}
        }

        // Authors
        String authorsStr = cleanLaTeX(getField(entry, BibTeXEntry.KEY_AUTHOR));
        if (authorsStr != null && !authorsStr.isEmpty()) {
            parseAuthors(authorsStr).forEach(pub::addAuthor);
        }

        return pub;
    }

    private JournalArticle convertToJournalArticle(BibTeXEntry entry) {
        JournalArticle ja = new JournalArticle();
        String journal = cleanLaTeX(getField(entry, BibTeXEntry.KEY_JOURNAL));
        if (journal != null) {
            ja.setJournalName(journal);
            ja.setVenue(journal);
        }
        ja.setVolume(getField(entry, BibTeXEntry.KEY_VOLUME));
        ja.setIssue(getField(entry, BibTeXEntry.KEY_NUMBER));
        ja.setPages(getField(entry, BibTeXEntry.KEY_PAGES));
        return ja;
    }

    private Book convertToBook(BibTeXEntry entry) {
        Book book = new Book();
        book.setPublisher(cleanLaTeX(getField(entry, BibTeXEntry.KEY_PUBLISHER)));
        book.setIsbn(getField(entry, new Key("isbn")));
        book.setEdition(getField(entry, BibTeXEntry.KEY_EDITION));
        String venue = cleanLaTeX(getField(entry, BibTeXEntry.KEY_PUBLISHER));
        if (venue != null) book.setVenue(venue);
        return book;
    }

    private ConferencePaper convertToConferencePaper(BibTeXEntry entry) {
        ConferencePaper cp = new ConferencePaper();
        String booktitle = cleanLaTeX(getField(entry, BibTeXEntry.KEY_BOOKTITLE));
        if (booktitle != null) {
            cp.setConferenceName(booktitle);
            cp.setVenue(booktitle);
        }
        return cp;
    }

    /**
     * Safely extracts a field value from a BibTeX entry.
     */
    private String getField(BibTeXEntry entry, Key key) {
        Value value = entry.getField(key);
        if (value == null) return null;
        String text = value.toUserString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Parses a BibTeX author string (e.g., "Smith, John and Doe, Jane")
     * into a list of Author objects.
     */
    private List<Author> parseAuthors(String authorsStr) {
        List<Author> authors = new ArrayList<>();
        // BibTeX uses " and " to separate authors
        String[] parts = authorsStr.split("\\s+and\\s+");
        for (String part : parts) {
            String name = part.trim();
            if (!name.isEmpty()) {
                authors.add(new Author(name));
            }
        }
        return authors;
    }

    /**
     * Cleans common LaTeX escape sequences from a string.
     * Converts: {\"u} → ü, {\'{e}} → é, removes braces, etc.
     */
    private String cleanLaTeX(String text) {
        if (text == null) return null;
        // Remove surrounding braces
        text = text.replaceAll("^\\{|\\}$", "");
        // Common LaTeX accents
        text = text.replace("\\\"u", "ü").replace("\\\"o", "ö").replace("\\\"a", "ä");
        text = text.replace("\\\"U", "Ü").replace("\\\"O", "Ö").replace("\\\"A", "Ä");
        text = text.replace("\\'e", "é").replace("\\'a", "á").replace("\\'i", "í");
        text = text.replace("\\`e", "è").replace("\\`a", "à");
        text = text.replace("\\~n", "ñ").replace("\\c{c}", "ç");
        // Remove remaining braces
        text = text.replace("{", "").replace("}", "");
        // Clean up LaTeX commands
        text = text.replace("\\&", "&").replace("\\%", "%").replace("\\_", "_");
        text = text.replace("\\textit", "").replace("\\textbf", "");
        text = text.replace("\\emph", "");
        return text.trim();
    }
}
