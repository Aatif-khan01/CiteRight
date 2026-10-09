package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import java.util.List;

/**
 * Formats citations in IEEE style.
 * 
 * Format: [1] A. Author and B. Author, "Title," Journal, vol. X, no. X, pp. X-X, Year.
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class IEEEFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder citation = new StringBuilder();

        // Authors (initials first: A. B. LastName)
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            for (int i = 0; i < authors.size(); i++) {
                String name = authors.get(i).getName();
                citation.append(formatIEEEAuthor(name));
                if (i < authors.size() - 2) {
                    citation.append(", ");
                } else if (i == authors.size() - 2) {
                    citation.append(", and ");
                }
            }
        } else {
            citation.append("Unknown Author");
        }
        citation.append(", ");

        // Title in quotes
        if (paper.getTitle() != null) {
            citation.append("\"").append(paper.getTitle()).append(",\" ");
        }

        // Journal/Venue in italics
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            citation.append(paper.getVenue()).append(", ");
        }

        // Year
        if (paper.getYear() > 0) {
            citation.append(paper.getYear()).append(".");
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            citation.append(" doi: ").append(paper.getDoi()).append(".");
        }

        return citation.toString().trim();
    }

    /**
     * Formats a name for IEEE style: "John Smith" → "J. Smith"
     */
    private String formatIEEEAuthor(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];

        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            formatted.append(parts[i].charAt(0)).append(". ");
        }
        formatted.append(parts[parts.length - 1]);
        return formatted.toString();
    }

    @Override
    public String getStyleName() {
        return "IEEE";
    }
}
