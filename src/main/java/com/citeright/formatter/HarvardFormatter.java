package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import java.util.List;

/**
 * Formats citations in Harvard referencing style.
 * 
 * Format: Author, A.A. (Year) 'Title of article', Journal Name, Volume(Issue), pp. pages.
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class HarvardFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder citation = new StringBuilder();

        // Authors
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            if (authors.size() == 1) {
                citation.append(authors.get(0).getCitationName());
            } else if (authors.size() <= 3) {
                for (int i = 0; i < authors.size() - 1; i++) {
                    citation.append(authors.get(i).getCitationName()).append(", ");
                }
                citation.append("and ").append(authors.get(authors.size() - 1).getCitationName());
            } else {
                citation.append(authors.get(0).getCitationName()).append(" et al.");
            }
        } else {
            citation.append("Unknown Author");
        }

        // Year
        citation.append(" (");
        if (paper.getYear() > 0) {
            citation.append(paper.getYear());
        } else {
            citation.append("n.d.");
        }
        citation.append(") ");

        // Title in single quotes
        if (paper.getTitle() != null) {
            citation.append("'").append(paper.getTitle()).append("', ");
        }

        // Journal/Venue in italics
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            citation.append(paper.getVenue()).append(". ");
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            citation.append("Available at: https://doi.org/").append(paper.getDoi());
        }

        return citation.toString().trim();
    }

    @Override
    public String getStyleName() {
        return "Harvard";
    }
}
