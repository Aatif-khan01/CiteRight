package com.citeright.service;

import com.citeright.database.PaperDAO;
import com.citeright.database.SearchHistoryDAO;
import com.citeright.model.*;
import com.citeright.ranking.CompositeRanker;
import com.citeright.search.CrossrefSearch;
import com.citeright.search.EuropePmcSearch;
import com.citeright.search.KeywordExtractor;
import com.citeright.search.SemanticScholarSearch;
import java.util.*;

/**
 * Main service that orchestrates searching, ranking, and caching.
 * This is the central business logic class.
 * 
 * Now searches BOTH OpenAlex AND Crossref APIs simultaneously,
 * merges the results (deduplicating by DOI), and ranks them.
 * This dramatically improves coverage for recently published papers
 * and papers with special characters in titles.
 * 
 * Demonstrates: FACADE PATTERN — provides a simple interface
 * to the complex subsystems (search, ranking, database).
 */
public class SearchService {

    private final SemanticScholarSearch openAlexSearch;
    private final CrossrefSearch crossrefSearch;
    private final EuropePmcSearch europePmcSearch;
    private final CompositeRanker ranker;
    private final KeywordExtractor keywordExtractor;
    private final PaperDAO paperDAO;
    private final SearchHistoryDAO searchHistoryDAO;
    private List<String> lastInsights;

    public SearchService() {
        this.openAlexSearch = new SemanticScholarSearch();
        this.crossrefSearch = new CrossrefSearch();
        this.europePmcSearch = new EuropePmcSearch();
        this.ranker = new CompositeRanker();
        this.keywordExtractor = new KeywordExtractor();
        this.paperDAO = new PaperDAO();
        this.searchHistoryDAO = new SearchHistoryDAO();
    }

    /**
     * Performs a full search: extract keywords → search APIs → merge → rank → cache.
     *
     * @param sentence The researcher's input sentence
     * @param limit    Maximum number of results
     * @return Ranked list of citation results
     */
    public List<CitationResult> search(String sentence, int limit) {
        // Step 1: Extract keywords
        String keywords = keywordExtractor.extract(sentence);
        System.out.println("[SearchService] Keywords: " + keywords);

        // Step 2: Search ALL 3 APIs concurrently for better coverage
        List<Publication> openAlexPapers = new ArrayList<>();
        List<Publication> crossrefPapers = new ArrayList<>();
        List<Publication> europePmcPapers = new ArrayList<>();

        // Use threads to search APIs in parallel
        Thread openAlexThread = new Thread(() -> {
            try {
                List<Publication> results = openAlexSearch.search(sentence, limit);
                synchronized (openAlexPapers) {
                    openAlexPapers.addAll(results);
                }
            } catch (Exception e) {
                System.err.println("[SearchService] OpenAlex search failed: " + e.getMessage());
            }
        });

        Thread crossrefThread = new Thread(() -> {
            try {
                // For Crossref, use original sentence for better fuzzy matching
                List<Publication> results = crossrefSearch.search(sentence, limit);
                synchronized (crossrefPapers) {
                    crossrefPapers.addAll(results);
                }
            } catch (Exception e) {
                System.err.println("[SearchService] Crossref search failed: " + e.getMessage());
            }
        });

        Thread europePmcThread = new Thread(() -> {
            try {
                // For Europe PMC, use extracted keywords similar to OpenAlex
                List<Publication> results = europePmcSearch.search(keywords, limit);
                synchronized (europePmcPapers) {
                    europePmcPapers.addAll(results);
                }
            } catch (Exception e) {
                System.err.println("[SearchService] Europe PMC search failed: " + e.getMessage());
            }
        });

        openAlexThread.start();
        crossrefThread.start();
        europePmcThread.start();

        // Wait for all searches to complete
        try {
            openAlexThread.join(25000); // 25 second timeout
            crossrefThread.join(25000);
            europePmcThread.join(25000);
        } catch (InterruptedException e) {
            System.err.println("[SearchService] Search interrupted: " + e.getMessage());
        }

        // Step 3: Merge and deduplicate results (DOI-based deduplication)
        List<Publication> mergedPapers = mergeResults(openAlexPapers, crossrefPapers, europePmcPapers);
        System.out.println("[SearchService] Merged results: " + mergedPapers.size() + 
                " (OpenAlex: " + openAlexPapers.size() + 
                ", Crossref: " + crossrefPapers.size() + 
                ", EuropePMC: " + europePmcPapers.size() + ")");

        // Step 4: If APIs return no results, try local cache
        if (mergedPapers.isEmpty()) {
            System.out.println("[SearchService] No API results. Trying local cache...");
            mergedPapers = paperDAO.searchLocal(keywords);
        }

        // Step 5: Rank results using NLP engine (TF-IDF + BM25)
        List<CitationResult> rankedResults = ranker.rank(mergedPapers, sentence);

        // Step 5b: Generate NLP quality insights for the result set
        lastInsights = ranker.getResultSetInsights(mergedPapers);

        // Removed automatic AI Generative evidence extraction (Step 5c) to save API quotas.
        // It is now manually triggered via the UI.

        // Step 6: Cache results in database
        try {
            paperDAO.saveAll(mergedPapers);
        } catch (Exception e) {
            System.err.println("[SearchService] Cache save failed (non-critical): " + e.getMessage());
        }

        // Step 7: Save to search history
        try {
            SearchQuery query = new SearchQuery(sentence, keywords);
            query.setResultCount(rankedResults.size());
            searchHistoryDAO.save(query);
        } catch (Exception e) {
            System.err.println("[SearchService] History save failed (non-critical): " + e.getMessage());
        }

        return rankedResults;
    }

    /**
     * Returns NLP-generated insights from the last search.
     * These include field velocity, diversity warnings, and impact analysis.
     */
    public List<String> getLastInsights() {
        return lastInsights != null ? lastInsights : new ArrayList<>();
    }

    /**
     * Merges results from multiple search engines, deduplicating by DOI.
     * Prefers OpenAlex entries when duplicates are found (they typically have better metadata).
     */
    private List<Publication> mergeResults(List<Publication> openAlexPapers, List<Publication> crossrefPapers, List<Publication> europePmcPapers) {
        Map<String, Publication> seenDois = new LinkedHashMap<>();
        Set<String> seenTitles = new HashSet<>();

        // Add OpenAlex papers first (preferred source for metadata)
        addPapersToMergeMap(openAlexPapers, seenDois, seenTitles);

        // Add Crossref papers, skipping duplicates
        addPapersToMergeMap(crossrefPapers, seenDois, seenTitles);

        // Add Europe PMC papers, skipping duplicates
        addPapersToMergeMap(europePmcPapers, seenDois, seenTitles);

        return new ArrayList<>(seenDois.values());
    }

    private void addPapersToMergeMap(List<Publication> papers, Map<String, Publication> seenDois, Set<String> seenTitles) {
        for (Publication paper : papers) {
            String doi = paper.getDoi();
            String titleKey = paper.getTitle() != null ? paper.getTitle().toLowerCase().trim() : "";

            // Skip if we already have this DOI
            if (doi != null && !doi.isEmpty() && seenDois.containsKey(doi.toLowerCase())) {
                continue;
            }

            // Skip if we already have a very similar title (fuzzy dedup)
            if (!titleKey.isEmpty() && seenTitles.contains(titleKey)) {
                continue;
            }

            if (doi != null && !doi.isEmpty()) {
                seenDois.put(doi.toLowerCase(), paper);
            }
            if (!titleKey.isEmpty()) {
                seenTitles.add(titleKey);
            }
        }
    }

    /**
     * Gets recent search history.
     */
    public List<SearchQuery> getSearchHistory(int limit) {
        return searchHistoryDAO.getRecentSearches(limit);
    }

    /**
     * Deletes a single search history entry by its ID.
     */
    public void deleteHistoryEntry(int id) {
        searchHistoryDAO.deleteById(id);
    }

    /**
     * Clears search history.
     */
    public void clearHistory() {
        searchHistoryDAO.clearHistory();
    }

    /**
     * Gets the number of cached papers in the database.
     */
    public int getCachedPaperCount() {
        return paperDAO.getCachedPaperCount();
    }
}
