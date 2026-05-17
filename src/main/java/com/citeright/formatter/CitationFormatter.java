package com.citeright.formatter;

import com.citeright.model.Publication;

/**
 * Interface for citation formatters.
 * 
 * Demonstrates: POLYMORPHISM
 * Each formatter implements format() differently for APA, MLA, IEEE, etc.
 * The same method call produces different output based on the object type.
 */
public interface CitationFormatter {

    /**
     * Formats a publication into a citation string.
     *
     * @param paper The publication to format
     * @return The formatted citation string
     */
    String format(Publication paper);

    /**
     * Returns the name of this citation style.
     */
    String getStyleName();
}
