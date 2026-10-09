package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import java.util.List;

/**
 * Formats citations in APA 7th Edition style.
 * 
 * Format: Author, A. A., & Author, B. B. (Year). Title of article. 
 *         Journal Name, Volume(Issue), Pages. https://doi.org/xxxxx
 * 
 * Example: Litjens, G., Kooi, T., & Bejnordi, B. E. (2017). A survey on 
 *          deep learning in medical image analysis. Medical Image Analysis, 
 *          42, 60-88. https://doi.org/10.1016/j.media.2017.07.005
 * 
 * Demonstrates: POLYMORPHISM — implements CitationFormatter.
 */
public class APAFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder citation = new StringBuilder();

        // Authors
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            if (authors.size() == 1) {
                citation.append(authors.get(0).getCitationName());
            } else if (authors.size() == 2) {
                citation.append(authors.get(0).getCitationName())
                        .append(", & ")
                        .append(authors.get(1).getCitationName());
            } else if (authors.size() <= 20) {
                for (int i = 0; i < authors.size() - 1; i++) {
                    citation.append(authors.get(i).getCitationName()).append(", ");
                }
                citation.append("& ").append(authors.get(authors.size() - 1).getCitationName());
            } else {
                // APA 7: For 21+ authors, list first 19, then ... then last
                for (int i = 0; i < 19; i++) {
                    citation.append(authors.get(i).getCitationName()).append(", ");
                }
                citation.append("... ").append(authors.get(authors.size() - 1).getCitationName());
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
        citation.append("). ");

        // Title (sentence case, italicized in actual documents)
        if (paper.getTitle() != null) {
            citation.append(paper.getTitle()).append(". ");
        }

        // Venue / Journal
        if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            citation.append(paper.getVenue()).append(". ");
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            citation.append("https://doi.org/").append(paper.getDoi());
        }

        return citation.toString().trim();
    }

    @Override
    public String getStyleName() {
        return "APA 7th Edition";
    }
}
