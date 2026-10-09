package com.citeright.search;

import com.citeright.model.Publication;
import java.util.List;

/**
 * Interface for search engines.
 * 
 * Demonstrates: POLYMORPHISM + STRATEGY PATTERN
 * Different implementations search different sources (API, local DB)
 * but all conform to the same interface.
 */
public interface SearchEngine {

    /**
     * Search for publications matching the given query.
     *
     * @param query  The search query (keywords or sentence)
     * @param limit  Maximum number of results to return
     * @return List of matching publications
     */
    List<Publication> search(String query, int limit);

    /**
     * Returns the name of this search source.
     */
    String getSourceName();

    /**
     * Returns whether this search engine is currently available.
     */
    boolean isAvailable();
}
