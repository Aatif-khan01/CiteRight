package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import java.util.List;

/**
 * Formats citations in MLA 9th Edition style.
 * 
 * Format: Author(s). "Title of Article." Journal Name, vol. X, no. X, Year, pp. X-X.
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class MLAFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder citation = new StringBuilder();

        // Authors (Last, First format for first author, then First Last for others)
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            if (authors.size() == 1) {
                citation.append(authors.get(0).getCitationName());
            } else if (authors.size() == 2) {
                citation.append(authors.get(0).getCitationName())
                        .append(", and ")
                        .append(authors.get(1).getName());
            } else {
                // MLA: First author et al. for 3+ authors
                citation.append(authors.get(0).getCitationName())
                        .append(", et al");
            }
        } else {
            citation.append("Unknown Author");
        }
        citation.append(". ");

        // Title in quotes
        if (paper.getTitle() != null) {
            citation.append("\"").append(paper.getTitle()).append(".\" ");
        }

        // Journal/Venue in italics (represented without markup here)
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            citation.append(paper.getVenue()).append(", ");
        }

        // Year
        if (paper.getYear() > 0) {
            citation.append(paper.getYear()).append(". ");
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            citation.append("https://doi.org/").append(paper.getDoi());
        }

        return citation.toString().trim();
    }

    @Override
    public String getStyleName() {
        return "MLA 9th Edition";
    }
}
