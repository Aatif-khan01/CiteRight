package com.citeright.nlp;

import java.util.*;

/**
 * BM25 (Best Match 25) Scoring Algorithm.
 * 
 * This is the EXACT algorithm used by:
 * - Apache Lucene / Apache Solr
 * - Elasticsearch
 * - Microsoft Bing
 * - Many production search engines worldwide
 * 
 * BM25 is an evolution of TF-IDF that improves two things:
 * 1. Term frequency SATURATION — a word appearing 10x isn't 10x more important than 1x
 * 2. Document LENGTH normalization — long documents don't unfairly dominate
 * 
 * Formula:
 *   BM25(q, d) = Σ IDF(t) × [TF(t,d) × (k1 + 1)] / [TF(t,d) + k1 × (1 - b + b × |d|/avgDL)]
 * 
 * Parameters:
 *   k1 = 1.5 (controls term frequency saturation)
 *   b  = 0.75 (controls length normalization: 0 = no normalization, 1 = full)
 * 
 * Demonstrates: Custom search engine algorithm implemented from scratch.
 */
public class BM25Scorer {

    // Standard BM25 parameters (used by Elasticsearch defaults)
    private double k1 = 1.5;
    private double b = 0.75;

    // Corpus statistics
    private Map<String, Integer> documentFrequency;  // How many docs contain each term
    private int totalDocuments;
    private double averageDocumentLength;

    public BM25Scorer() {
        this.documentFrequency = new HashMap<>();
        this.totalDocuments = 0;
        this.averageDocumentLength = 0.0;
    }

    /**
     * Builds the BM25 model from a corpus of documents.
     * Computes document frequencies and average document length.
     *
     * @param documents List of document texts (title + abstract)
     */
    public void buildModel(List<String> documents) {
        this.totalDocuments = documents.size();
        if (totalDocuments == 0) return;

        documentFrequency.clear();
        double totalLength = 0;

        for (String doc : documents) {
            List<String> tokens = TextPreprocessor.tokenize(doc);
            totalLength += tokens.size();

            // Count unique terms per document (for DF calculation)
            Set<String> uniqueTerms = new HashSet<>(tokens);
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        this.averageDocumentLength = totalLength / totalDocuments;

        System.out.println("[BM25] Model built: " + documentFrequency.size() + 
                " terms, avgDL=" + String.format("%.1f", averageDocumentLength) +
                ", " + totalDocuments + " docs");
    }

    /**
     * Computes the BM25 score for a query against a document.
     * Higher score = more relevant.
     *
     * @param query The user's search sentence
     * @param document The paper's title + abstract
     * @return BM25 relevance score (unbounded, typically 0 to ~30)
     */
    public double score(String query, String document) {
        if (totalDocuments == 0) return 0.0;

        List<String> queryTokens = TextPreprocessor.tokenize(query);
        List<String> docTokens = TextPreprocessor.tokenize(document);

        if (queryTokens.isEmpty() || docTokens.isEmpty()) return 0.0;

        // Count term frequencies in the document
        Map<String, Integer> docTermCounts = new HashMap<>();
        for (String token : docTokens) {
            docTermCounts.merge(token, 1, Integer::sum);
        }

        double docLength = docTokens.size();
        double score = 0.0;

        // For each unique query term, compute its BM25 contribution
        Set<String> uniqueQueryTerms = new LinkedHashSet<>(queryTokens);
        for (String queryTerm : uniqueQueryTerms) {
            // IDF component: log((N - df + 0.5) / (df + 0.5) + 1)
            int df = documentFrequency.getOrDefault(queryTerm, 0);
            double idf = Math.log(((totalDocuments - df + 0.5) / (df + 0.5)) + 1.0);

            // TF component with saturation and length normalization
            int rawTf = docTermCounts.getOrDefault(queryTerm, 0);
            if (rawTf == 0) continue;

            double tfNorm = (rawTf * (k1 + 1.0)) / 
                    (rawTf + k1 * (1.0 - b + b * (docLength / averageDocumentLength)));

            score += idf * tfNorm;
        }

        return score;
    }

    /**
     * Scores a query against a document and normalizes the result to 0-100 scale.
     * This makes BM25 scores comparable with other scoring methods.
     *
     * @param query The user's search sentence
     * @param document The paper's title + abstract
     * @param maxScore The maximum BM25 score in the result set (for normalization)
     * @return Normalized score between 0.0 and 100.0
     */
    public double scoreNormalized(String query, String document, double maxScore) {
        if (maxScore <= 0) return 0.0;
        double raw = score(query, document);
        return Math.min(100.0, (raw / maxScore) * 100.0);
    }

    /**
     * Returns true if the model has been built.
     */
    public boolean isReady() {
        return totalDocuments > 0;
    }

    // Setters for tuning BM25 parameters
    public void setK1(double k1) { this.k1 = k1; }
    public void setB(double b) { this.b = b; }
    public double getK1() { return k1; }
    public double getB() { return b; }
}
