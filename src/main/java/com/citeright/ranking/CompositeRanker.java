package com.citeright.ranking;

import com.citeright.model.CitationResult;
import com.citeright.model.Publication;
import com.citeright.nlp.BM25Scorer;
import com.citeright.nlp.QualityAnalyzer;
import com.citeright.nlp.TfIdfEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combines multiple ranking strategies with configurable weights,
 * now powered by the custom TF-IDF + BM25 NLP engine.
 * 
 * New Score Formula (NLP-powered):
 *   Final = (BM25 × 0.35) + (Cosine × 0.25) + (Citations × 0.25) + (Recency × 0.15)
 * 
 * Fallback (if NLP model not ready):
 *   Final = (Keyword × 0.45) + (Citations × 0.30) + (Recency × 0.25)
 * 
 * Demonstrates: COMPOSITION + STRATEGY PATTERN + NLP Integration
 */
public class CompositeRanker {

    private final RelevanceRanker relevanceRanker;
    private final CitationCountRanker citationRanker;
    private final RecencyRanker recencyRanker;
    private final QualityAnalyzer qualityAnalyzer;

    // NLP engines (built per-query for accurate IDF)
    private final TfIdfEngine tfIdfEngine;
    private final BM25Scorer bm25Scorer;

    // NLP-powered weights
    private double bm25Weight = 0.35;
    private double cosineWeight = 0.25;
    private double citationWeight = 0.25;
    private double recencyWeight = 0.15;

    // Fallback weights (keyword mode)
    private double fallbackRelevanceWeight = 0.45;
    private double fallbackCitationWeight = 0.30;
    private double fallbackRecencyWeight = 0.25;

    public CompositeRanker() {
        this.relevanceRanker = new RelevanceRanker();
        this.citationRanker = new CitationCountRanker();
        this.recencyRanker = new RecencyRanker();
        this.qualityAnalyzer = new QualityAnalyzer();
        this.tfIdfEngine = new TfIdfEngine();
        this.bm25Scorer = new BM25Scorer();
    }

    /**
     * Ranks a list of publications using the NLP engine and returns CitationResult objects
     * sorted by composite relevance score (highest first).
     * 
     * Process:
     * 1. Build TF-IDF and BM25 models from all paper abstracts
     * 2. Score each paper with BM25 + Cosine Similarity
     * 3. Combine with citation count and recency scores
     * 4. Run quality analysis on each result
     * 5. Sort by final composite score
     */
    public List<CitationResult> rank(List<Publication> papers, String originalQuery) {
        List<CitationResult> results = new ArrayList<>();
        if (papers.isEmpty()) return results;

        // Step 1: Build NLP models from the paper corpus
        List<String> documents = new ArrayList<>();
        for (Publication paper : papers) {
            String doc = buildDocumentText(paper);
            documents.add(doc);
        }

        boolean nlpReady = false;
        try {
            tfIdfEngine.buildModel(documents);
            bm25Scorer.buildModel(documents);
            nlpReady = tfIdfEngine.isReady() && bm25Scorer.isReady();
        } catch (Exception e) {
            System.err.println("[CompositeRanker] NLP model build failed, using fallback: " + e.getMessage());
        }

        // Step 2: Compute raw BM25 scores to find max (for normalization)
        double maxBm25 = 0.0;
        if (nlpReady) {
            for (Publication paper : papers) {
                double bm25 = bm25Scorer.score(originalQuery, buildDocumentText(paper));
                maxBm25 = Math.max(maxBm25, bm25);
            }
        }

        // Step 3: Score each paper
        for (Publication paper : papers) {
            String docText = buildDocumentText(paper);
            double compositeScore;
            double bm25Raw = 0, cosineRaw = 0;

            if (nlpReady) {
                // NLP-powered scoring
                bm25Raw = bm25Scorer.score(originalQuery, docText);
                double bm25Normalized = maxBm25 > 0 ? (bm25Raw / maxBm25) * 100.0 : 0;

                cosineRaw = tfIdfEngine.similarity(originalQuery, docText);
                double cosineNormalized = cosineRaw * 100.0; // Already 0-1, scale to 0-100

                double citeScore = citationRanker.score(paper, originalQuery);
                double recScore = recencyRanker.score(paper, originalQuery);

                compositeScore = (bm25Normalized * bm25Weight)
                        + (cosineNormalized * cosineWeight)
                        + (citeScore * citationWeight)
                        + (recScore * recencyWeight);

                CitationResult result = new CitationResult(paper, compositeScore);
                result.setBm25Score(bm25Normalized);
                result.setCosineSimilarity(cosineRaw);
                result.setSemanticScore((bm25Normalized + cosineNormalized) / 2.0);
                result.setCitationScore(citeScore);
                result.setRecencyScore(recScore);

                // Quality analysis
                result.setQualityLabel(qualityAnalyzer.getQualityLabel(paper));
                result.setQualityFlags(qualityAnalyzer.analyzeQuality(paper));

                results.add(result);
            } else {
                // Fallback: keyword-based scoring (original behavior)
                double relevScore = relevanceRanker.score(paper, originalQuery);
                double citeScore = citationRanker.score(paper, originalQuery);
                double recScore = recencyRanker.score(paper, originalQuery);

                compositeScore = (relevScore * fallbackRelevanceWeight)
                        + (citeScore * fallbackCitationWeight)
                        + (recScore * fallbackRecencyWeight);

                CitationResult result = new CitationResult(paper, compositeScore);
                result.setKeywordMatchScore(relevScore);
                result.setCitationScore(citeScore);
                result.setRecencyScore(recScore);
                result.setQualityLabel(qualityAnalyzer.getQualityLabel(paper));
                result.setQualityFlags(qualityAnalyzer.analyzeQuality(paper));

                results.add(result);
            }
        }

        // Sort by relevance (highest first)
        Collections.sort(results);

        if (nlpReady) {
            System.out.println("[CompositeRanker] 🧠 NLP-powered ranking applied (" + results.size() + " papers)");
        } else {
            System.out.println("[CompositeRanker] 📝 Keyword-based ranking applied (fallback)");
        }

        return results;
    }

    /**
     * Runs quality analysis on the entire result set.
     * Returns high-level insights about the search results.
     */
    public List<String> getResultSetInsights(List<Publication> papers) {
        return qualityAnalyzer.analyzeResultSet(papers);
    }

    /**
     * Builds the combined text representation of a paper for NLP processing.
     * Title is weighted 3x (repeated) because it's the most informative field.
     */
    private String buildDocumentText(Publication paper) {
        StringBuilder sb = new StringBuilder();

        // Title gets 3x weight (repeated for emphasis in TF calculations)
        String title = paper.getTitle() != null ? paper.getTitle() : "";
        sb.append(title).append(" ").append(title).append(" ").append(title).append(" ");

        // Abstract
        if (paper.getAbstractText() != null && !paper.getAbstractText().isEmpty()) {
            sb.append(paper.getAbstractText()).append(" ");
        }

        // Venue
        if (paper.getVenue() != null) {
            sb.append(paper.getVenue());
        }

        return sb.toString();
    }

    // --- Weight Setters (for customization) ---
    public void setNlpWeights(double bm25, double cosine, double citation, double recency) {
        double total = bm25 + cosine + citation + recency;
        this.bm25Weight = bm25 / total;
        this.cosineWeight = cosine / total;
        this.citationWeight = citation / total;
        this.recencyWeight = recency / total;
    }
}
