package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import com.citeright.model.JournalArticle;
import com.citeright.model.Book;
import com.citeright.model.ConferencePaper;
import java.util.List;

/**
 * Formats publications in RIS (Research Information Systems) format.
 * 
 * RIS is a standardized tag format used by Endnote, Mendeley, Zotero.
 * Example:
 *   TY  - JOUR
 *   AU  - Smith, John
 *   AU  - Doe, Jane
 *   TI  - Deep Learning in Medical Imaging
 *   JO  - Medical Image Analysis
 *   PY  - 2021
 *   DO  - 10.1016/j.media.2017.07.005
 *   ER  -
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class RISFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder ris = new StringBuilder();

        // Type
        ris.append("TY  - ").append(getRISType(paper)).append("\n");

        // Authors (one AU line per author)
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            for (Author author : authors) {
                ris.append("AU  - ").append(author.getName()).append("\n");
            }
        }

        // Title
        if (paper.getTitle() != null) {
            ris.append("TI  - ").append(paper.getTitle()).append("\n");
        }

        // Journal / Booktitle
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            if (paper instanceof ConferencePaper) {
                ris.append("T2  - ").append(paper.getVenue()).append("\n");
            } else {
                ris.append("JO  - ").append(paper.getVenue()).append("\n");
            }
        }

        // Year
        if (paper.getYear() > 0) {
            ris.append("PY  - ").append(paper.getYear()).append("\n");
        }

        // Journal-specific fields
        if (paper instanceof JournalArticle ja) {
            if (ja.getVolume() != null && !ja.getVolume().isEmpty()) {
                ris.append("VL  - ").append(ja.getVolume()).append("\n");
            }
            if (ja.getIssue() != null && !ja.getIssue().isEmpty()) {
                ris.append("IS  - ").append(ja.getIssue()).append("\n");
            }
            if (ja.getPages() != null && !ja.getPages().isEmpty()) {
                String[] pages = ja.getPages().split("-");
                ris.append("SP  - ").append(pages[0].trim()).append("\n");
                if (pages.length > 1) {
                    ris.append("EP  - ").append(pages[1].trim()).append("\n");
                }
            }
        }

        // Book-specific fields
        if (paper instanceof Book book) {
            if (book.getPublisher() != null && !book.getPublisher().isEmpty()) {
                ris.append("PB  - ").append(book.getPublisher()).append("\n");
            }
            if (book.getIsbn() != null && !book.getIsbn().isEmpty()) {
                ris.append("SN  - ").append(book.getIsbn()).append("\n");
            }
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            ris.append("DO  - ").append(paper.getDoi()).append("\n");
        }

        // URL
        if (paper.getUrl() != null && !paper.getUrl().isEmpty()) {
            ris.append("UR  - ").append(paper.getUrl()).append("\n");
        }

        // Abstract
        if (paper.getAbstractText() != null && !paper.getAbstractText().isEmpty()) {
            ris.append("AB  - ").append(paper.getAbstractText()).append("\n");
        }

        // End of record
        ris.append("ER  - \n");

        return ris.toString();
    }

    @Override
    public String getStyleName() {
        return "RIS";
    }

    private String getRISType(Publication paper) {
        if (paper instanceof Book) return "BOOK";
        if (paper instanceof ConferencePaper) return "CONF";
        return "JOUR"; // default for journal articles
    }
}
