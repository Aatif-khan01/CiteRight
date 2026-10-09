package com.citeright.formatter;

import com.citeright.model.Author;
import com.citeright.model.Publication;
import java.util.List;

/**
 * Generates a Word-compatible XML reference field.
 * Users can paste this into Word's bibliography source XML.
 */
public class WordXMLFormatter implements CitationFormatter {

    @Override
    public String format(Publication paper) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b:Source xmlns:b=\"http://schemas.openxmlformats.org/officeDocument/2006/bibliography\">\n");
        sb.append("  <b:Tag>").append(generateTag(paper)).append("</b:Tag>\n");
        sb.append("  <b:SourceType>JournalArticle</b:SourceType>\n");

        // Authors
        List<Author> authors = paper.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            sb.append("  <b:Author>\n    <b:Author>\n      <b:NameList>\n");
            for (Author a : authors) {
                sb.append("        <b:Person>\n");
                sb.append("          <b:Last>").append(esc(a.getLastName())).append("</b:Last>\n");
                String first = a.getName() != null ? a.getName().replace(a.getLastName(), "").trim() : "";
                sb.append("          <b:First>").append(esc(first)).append("</b:First>\n");
                sb.append("        </b:Person>\n");
            }
            sb.append("      </b:NameList>\n    </b:Author>\n  </b:Author>\n");
        }

        if (paper.getTitle() != null)
            sb.append("  <b:Title>").append(esc(paper.getTitle())).append("</b:Title>\n");
        if (paper.getVenue() != null)
            sb.append("  <b:JournalName>").append(esc(paper.getVenue())).append("</b:JournalName>\n");
        if (paper.getYear() > 0)
            sb.append("  <b:Year>").append(paper.getYear()).append("</b:Year>\n");
        if (paper.getDoi() != null && !paper.getDoi().isEmpty())
            sb.append("  <b:DOI>").append(esc(paper.getDoi())).append("</b:DOI>\n");
        if (paper.getUrl() != null && !paper.getUrl().isEmpty())
            sb.append("  <b:URL>").append(esc(paper.getUrl())).append("</b:URL>\n");

        sb.append("</b:Source>");
        return sb.toString();
    }

    @Override
    public String getStyleName() { return "Word XML"; }

    private String generateTag(Publication paper) {
        List<Author> authors = paper.getAuthors();
        String tag = "Unknown";
        if (authors != null && !authors.isEmpty()) {
            tag = authors.get(0).getLastName().replaceAll("[^a-zA-Z]", "");
        }
        if (paper.getYear() > 0) tag += paper.getYear();
        return tag;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
