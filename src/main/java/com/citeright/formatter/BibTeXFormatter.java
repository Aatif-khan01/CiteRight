package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import com.citeright.model.JournalArticle;
import com.citeright.model.Book;
import com.citeright.model.ConferencePaper;
import java.util.List;

/**
 * Formats publications as BibTeX entries.
 * 
 * Generates standard BibTeX format used by LaTeX and most reference managers.
 * Example output:
 *   @article{smith2021,
 *     author = {Smith, John and Doe, Jane},
 *     title = {Deep Learning in Medical Imaging},
 *     journal = {Medical Image Analysis},
 *     year = {2021},
 *     doi = {10.1016/j.media.2017.07.005}
 *   }
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class BibTeXFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder bib = new StringBuilder();

        // Determine entry type and cite key
        String entryType = getEntryType(paper);
        String citeKey = generateCiteKey(paper);

        bib.append("@").append(entryType).append("{").append(citeKey).append(",\n");

        // Authors
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            bib.append("  author = {");
            for (int i = 0; i < authors.size(); i++) {
                if (i > 0) bib.append(" and ");
                bib.append(authors.get(i).getName());
            }
            bib.append("},\n");
        }

        // Title
        if (paper.getTitle() != null) {
            bib.append("  title = {").append(paper.getTitle()).append("},\n");
        }

        // Venue / Journal / Booktitle
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            if (paper instanceof ConferencePaper) {
                bib.append("  booktitle = {").append(paper.getVenue()).append("},\n");
            } else {
                bib.append("  journal = {").append(paper.getVenue()).append("},\n");
            }
        }

        // Year
        if (paper.getYear() > 0) {
            bib.append("  year = {").append(paper.getYear()).append("},\n");
        }

        // Journal-specific fields
        if (paper instanceof JournalArticle ja) {
            if (ja.getVolume() != null && !ja.getVolume().isEmpty()) {
                bib.append("  volume = {").append(ja.getVolume()).append("},\n");
            }
            if (ja.getIssue() != null && !ja.getIssue().isEmpty()) {
                bib.append("  number = {").append(ja.getIssue()).append("},\n");
            }
            if (ja.getPages() != null && !ja.getPages().isEmpty()) {
                bib.append("  pages = {").append(ja.getPages()).append("},\n");
            }
        }

        // Book-specific fields
        if (paper instanceof Book book) {
            if (book.getPublisher() != null && !book.getPublisher().isEmpty()) {
                bib.append("  publisher = {").append(book.getPublisher()).append("},\n");
            }
            if (book.getIsbn() != null && !book.getIsbn().isEmpty()) {
                bib.append("  isbn = {").append(book.getIsbn()).append("},\n");
            }
            if (book.getEdition() != null && !book.getEdition().isEmpty()) {
                bib.append("  edition = {").append(book.getEdition()).append("},\n");
            }
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            bib.append("  doi = {").append(paper.getDoi()).append("},\n");
        }

        // URL
        if (paper.getUrl() != null && !paper.getUrl().isEmpty()) {
            bib.append("  url = {").append(paper.getUrl()).append("},\n");
        }

        // Abstract
        if (paper.getAbstractText() != null && !paper.getAbstractText().isEmpty()) {
            bib.append("  abstract = {").append(paper.getAbstractText()).append("},\n");
        }

        // Remove trailing comma and close
        String result = bib.toString();
        if (result.endsWith(",\n")) {
            result = result.substring(0, result.length() - 2) + "\n";
        }
        result += "}";

        return result;
    }

    @Override
    public String getStyleName() {
        return "BibTeX";
    }

    /**
     * Generates a unique cite key from first author's last name + year.
     * Example: "smith2021"
     */
    public String generateCiteKey(Publication paper) {
        String key = "unknown";

        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            String lastName = authors.get(0).getLastName().toLowerCase();
            // Remove non-alphanumeric characters
            lastName = lastName.replaceAll("[^a-z]", "");
            if (!lastName.isEmpty()) {
                key = lastName;
            }
        }

        if (paper.getYear() > 0) {
            key += paper.getYear();
        }

        return key;
    }

    private String getEntryType(Publication paper) {
        if (paper instanceof Book) return "book";
        if (paper instanceof ConferencePaper) return "inproceedings";
        return "article"; // default for JournalArticle and others
    }
}
