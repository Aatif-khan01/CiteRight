package com.citeright.service;

import com.citeright.model.*;
import com.citeright.formatter.BibTeXFormatter;
import com.citeright.formatter.RISFormatter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles importing and exporting papers in BibTeX and RIS formats.
 * Supports both file-level operations (import/export entire libraries)
 * and single-paper formatting.
 */
public class ImportExportService {

    private final BibTeXFormatter bibTexFormatter = new BibTeXFormatter();
    private final RISFormatter risFormatter = new RISFormatter();

    // === EXPORT ===

    public void exportToBibTeX(List<Publication> papers, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("% CiteRight Library Export - BibTeX Format\n");
            writer.write("% Generated: " + java.time.LocalDateTime.now() + "\n");
            writer.write("% Papers: " + papers.size() + "\n\n");
            for (Publication paper : papers) {
                writer.write(bibTexFormatter.format(paper));
                writer.write("\n\n");
            }
        }
    }

    public void exportToRIS(List<Publication> papers, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (Publication paper : papers) {
                writer.write(risFormatter.format(paper));
                writer.write("\n");
            }
        }
    }

    public void exportToCSV(List<Publication> papers, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("Title,Authors,Year,Journal,DOI,Citations,Abstract\n");
            for (Publication paper : papers) {
                writer.write(escapeCSV(paper.getTitle()) + ",");
                writer.write(escapeCSV(paper.getAuthorsFormatted()) + ",");
                writer.write(paper.getYear() + ",");
                writer.write(escapeCSV(paper.getVenue()) + ",");
                writer.write(escapeCSV(paper.getDoi()) + ",");
                writer.write(paper.getCitationCount() + ",");
                writer.write(escapeCSV(paper.getAbstractText()));
                writer.write("\n");
            }
        }
    }

    // === IMPORT ===

    public List<Publication> importFromBibTeX(File file) throws IOException {
        String content = readFile(file);
        return parseBibTeX(content);
    }

    public List<Publication> importFromRIS(File file) throws IOException {
        String content = readFile(file);
        return parseRIS(content);
    }

    /**
     * Auto-detect format and import.
     */
    public List<Publication> importAuto(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".bib") || name.endsWith(".bibtex")) {
            return importFromBibTeX(file);
        } else if (name.endsWith(".ris")) {
            return importFromRIS(file);
        }
        throw new IOException("Unsupported file format. Use .bib or .ris files.");
    }

    // === PARSERS ===

    List<Publication> parseBibTeX(String content) {
        List<Publication> papers = new ArrayList<>();
        // Match @type{key, ... }
        Pattern entryPattern = Pattern.compile(
            "@(\\w+)\\s*\\{\\s*([^,]*),\\s*([^@]*?)\\}\\s*(?=@|$)",
            Pattern.DOTALL);
        Matcher matcher = entryPattern.matcher(content);

        while (matcher.find()) {
            String type = matcher.group(1).toLowerCase();
            String fields = matcher.group(3);

            Publication paper;
            if ("book".equals(type)) {
                paper = new Book();
            } else if ("inproceedings".equals(type) || "conference".equals(type)) {
                paper = new ConferencePaper();
            } else {
                paper = new JournalArticle();
            }

            // Parse fields
            paper.setTitle(extractBibField(fields, "title"));
            String authorStr = extractBibField(fields, "author");
            if (authorStr != null) {
                for (String name : authorStr.split("\\s+and\\s+")) {
                    paper.addAuthor(new Author(name.trim()));
                }
            }
            String year = extractBibField(fields, "year");
            if (year != null) {
                try { paper.setYear(Integer.parseInt(year.trim())); }
                catch (NumberFormatException e) { /* skip */ }
            }
            paper.setVenue(extractBibField(fields, "journal"));
            if (paper.getVenue() == null) paper.setVenue(extractBibField(fields, "booktitle"));
            paper.setDoi(extractBibField(fields, "doi"));
            paper.setUrl(extractBibField(fields, "url"));
            paper.setAbstractText(extractBibField(fields, "abstract"));

            if (paper instanceof JournalArticle ja) {
                ja.setVolume(extractBibField(fields, "volume"));
                ja.setIssue(extractBibField(fields, "number"));
                ja.setPages(extractBibField(fields, "pages"));
            }
            if (paper instanceof Book book) {
                book.setPublisher(extractBibField(fields, "publisher"));
                book.setIsbn(extractBibField(fields, "isbn"));
                book.setEdition(extractBibField(fields, "edition"));
            }

            // Generate a paper_id
            paper.setPaperId("imported_" + System.nanoTime());

            if (paper.getTitle() != null && !paper.getTitle().isEmpty()) {
                papers.add(paper);
            }
        }
        return papers;
    }

    List<Publication> parseRIS(String content) {
        List<Publication> papers = new ArrayList<>();
        String[] lines = content.split("\n");

        Publication current = null;
        List<Author> currentAuthors = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.length() < 6) continue;

            String tag = line.substring(0, 2).trim();
            String value = line.length() > 6 ? line.substring(6).trim() : "";

            switch (tag) {
                case "TY" -> {
                    currentAuthors = new ArrayList<>();
                    current = switch (value) {
                        case "BOOK" -> new Book();
                        case "CONF", "CPAPER" -> new ConferencePaper();
                        default -> new JournalArticle();
                    };
                    current.setPaperId("imported_" + System.nanoTime());
                }
                case "AU", "A1" -> {
                    if (current != null) currentAuthors.add(new Author(value));
                }
                case "TI", "T1" -> { if (current != null) current.setTitle(value); }
                case "JO", "JF", "T2" -> { if (current != null) current.setVenue(value); }
                case "PY", "Y1" -> {
                    if (current != null) {
                        try { current.setYear(Integer.parseInt(value.substring(0, 4))); }
                        catch (Exception e) { /* skip */ }
                    }
                }
                case "VL" -> { if (current instanceof JournalArticle ja) ja.setVolume(value); }
                case "IS" -> { if (current instanceof JournalArticle ja) ja.setIssue(value); }
                case "SP" -> { if (current instanceof JournalArticle ja) ja.setPages(value); }
                case "EP" -> {
                    if (current instanceof JournalArticle ja && ja.getPages() != null) {
                        ja.setPages(ja.getPages() + "-" + value);
                    }
                }
                case "DO" -> { if (current != null) current.setDoi(value); }
                case "UR" -> { if (current != null) current.setUrl(value); }
                case "AB", "N2" -> { if (current != null) current.setAbstractText(value); }
                case "PB" -> { if (current instanceof Book b) b.setPublisher(value); }
                case "SN" -> { if (current instanceof Book b) b.setIsbn(value); }
                case "ER" -> {
                    if (current != null) {
                        current.setAuthors(currentAuthors);
                        if (current.getTitle() != null && !current.getTitle().isEmpty()) {
                            papers.add(current);
                        }
                        current = null;
                    }
                }
            }
        }
        return papers;
    }

    // === Helpers ===

    private String extractBibField(String fields, String fieldName) {
        // Match: fieldName = {value} or fieldName = "value"
        Pattern p = Pattern.compile(
            fieldName + "\\s*=\\s*[{\"](.*?)[}\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(fields);
        if (m.find()) {
            return m.group(1).trim().replaceAll("\\s+", " ");
        }
        return null;
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
