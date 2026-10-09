package com.citeright.formatter;

import com.citeright.model.Publication;

/**
 * Generates a LaTeX \cite{key} command plus the full BibTeX entry.
 * One-click copy for LaTeX users.
 */
public class LaTeXCiteFormatter implements CitationFormatter {

    private final BibTeXFormatter bibFormatter = new BibTeXFormatter();

    @Override
    public String format(Publication paper) {
        String citeKey = bibFormatter.generateCiteKey(paper);
        StringBuilder sb = new StringBuilder();
        sb.append("% LaTeX Citation Command:\n");
        sb.append("\\cite{").append(citeKey).append("}\n\n");
        sb.append("% BibTeX Entry (add to your .bib file):\n");
        sb.append(bibFormatter.format(paper));
        return sb.toString();
    }

    /** Returns just the \cite{key} command for inline use */
    public String formatInline(Publication paper) {
        return "\\cite{" + bibFormatter.generateCiteKey(paper) + "}";
    }

    @Override
    public String getStyleName() {
        return "LaTeX";
    }
}
