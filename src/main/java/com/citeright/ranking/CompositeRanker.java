package com.citeright.ranking;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.NeuralAvailability;
import com.citeright.model.CitationResult;
import com.citeright.model.Publication;
import com.citeright.nlp.BM25Scorer;
import com.citeright.nlp.QualityAnalyzer;
import com.citeright.nlp.TfIdfEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // Neural-enhanced weights (when BGE-M3 is available)
    private double neuralWeight = 0.30;
    private double neuralBm25Weight = 0.25;
    private double neuralCosineWeight = 0.15;
    private double neuralCitationWeight = 0.20;
    private double neuralRecencyWeight = 0.10;

    // NLP-powered weights (TF-IDF + BM25 only)
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

        // Step 2: Check if neural embeddings are available for enhanced ranking
        boolean neuralReady = NeuralAvailability.isReady();
        Map<Integer, float[]> cachedEmbeddings = null;
        float[] queryEmbedding = null;
        if (neuralReady) {
            queryEmbedding = NeuralAvailability.embed(originalQuery);
            if (queryEmbedding != null) {
                cachedEmbeddings = NeuralAvailability.getCachedEmbeddings();
            } else {
                neuralReady = false; // embedding failed, skip neural path
            }
        }

        // Step 3: Compute raw BM25 scores to find max (for normalization)
        double maxBm25 = 0.0;
        if (nlpReady) {
            for (Publication paper : papers) {
                double bm25 = bm25Scorer.score(originalQuery, buildDocumentText(paper));
                maxBm25 = Math.max(maxBm25, bm25);
            }
        }

        // Step 4: Score each paper
        for (Publication paper : papers) {
            String docText = buildDocumentText(paper);
            double compositeScore;
            double bm25Raw = 0, cosineRaw = 0;
            double authorBoost = calculateAuthorMatchBoost(paper, originalQuery);

            if (nlpReady) {
                // NLP-powered scoring
                bm25Raw = bm25Scorer.score(originalQuery, docText);
                double bm25Normalized = maxBm25 > 0 ? (bm25Raw / maxBm25) * 100.0 : 0;

                cosineRaw = tfIdfEngine.similarity(originalQuery, docText);
                double cosineNormalized = cosineRaw * 100.0;

                double citeScore = citationRanker.score(paper, originalQuery);
                double recScore = recencyRanker.score(paper, originalQuery);

                if (neuralReady && cachedEmbeddings != null) {
                    // Neural-enhanced ranking: add semantic embedding similarity
                    double neuralScore = 0.0;
                    float[] paperEmbedding = paper.getEmbedding();
                    if (paperEmbedding == null && cachedEmbeddings != null) {
                        // Try to load from database cache by ID
                        try {
                            if (paper.getPaperId() != null && paper.getPaperId().matches("\\d+")) {
                                int id = Integer.parseInt(paper.getPaperId());
                                paperEmbedding = cachedEmbeddings.get(id);
                            }
                        } catch (Exception e) {
                            // ignore parsing errors
                        }
                    }
                    if (paperEmbedding != null && queryEmbedding != null) {
                        neuralScore = BgeM3EmbeddingEngine.cosineSimilarity(queryEmbedding, paperEmbedding) * 100.0;
                    } else {
                        // For fresh web search results (no cached embeddings), avoid heavy CPU inference.
                        // Instead, fall back to high-quality normalized BM25 + TF-IDF similarity.
                        neuralScore = (bm25Normalized + cosineNormalized) / 2.0;
                    }

                    compositeScore = (neuralScore * neuralWeight)
                            + (bm25Normalized * neuralBm25Weight)
                            + (cosineNormalized * neuralCosineWeight)
                            + (citeScore * neuralCitationWeight)
                            + (recScore * neuralRecencyWeight)
                            + authorBoost;

                    CitationResult result = new CitationResult(paper, Math.min(100.0, compositeScore));
                    result.setBm25Score(bm25Normalized);
                    result.setCosineSimilarity(cosineRaw);
                    result.setSemanticScore(neuralScore);
                    result.setCitationScore(citeScore);
                    result.setRecencyScore(recScore);
                    result.setQualityLabel(qualityAnalyzer.getQualityLabel(paper));
                    result.setQualityFlags(qualityAnalyzer.analyzeQuality(paper));
                    results.add(result);
                } else {
                    // Standard NLP ranking (BM25 + TF-IDF)
                    compositeScore = (bm25Normalized * bm25Weight)
                            + (cosineNormalized * cosineWeight)
                            + (citeScore * citationWeight)
                            + (recScore * recencyWeight)
                            + authorBoost;

                    CitationResult result = new CitationResult(paper, Math.min(100.0, compositeScore));
                    result.setBm25Score(bm25Normalized);
                    result.setCosineSimilarity(cosineRaw);
                    result.setSemanticScore((bm25Normalized + cosineNormalized) / 2.0);
                    result.setCitationScore(citeScore);
                    result.setRecencyScore(recScore);
                    result.setQualityLabel(qualityAnalyzer.getQualityLabel(paper));
                    result.setQualityFlags(qualityAnalyzer.analyzeQuality(paper));
                    results.add(result);
                }
            } else {
                // Fallback: keyword-based scoring (original behavior)
                double relevScore = relevanceRanker.score(paper, originalQuery);
                double citeScore = citationRanker.score(paper, originalQuery);
                double recScore = recencyRanker.score(paper, originalQuery);

                compositeScore = (relevScore * fallbackRelevanceWeight)
                        + (citeScore * fallbackCitationWeight)
                        + (recScore * fallbackRecencyWeight)
                        + authorBoost;

                CitationResult result = new CitationResult(paper, Math.min(100.0, compositeScore));
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

        if (neuralReady) {
            System.out.println("[CompositeRanker] 🧠 Neural-enhanced ranking applied (" + results.size() + " papers)");
        } else if (nlpReady) {
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

        // Authors gets 2x weight (repeated for emphasis in TF calculations)
        String authors = paper.getAuthorsFormatted();
        if (authors != null && !authors.isEmpty()) {
            sb.append(authors).append(" ").append(authors).append(" ");
        }

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

    /**
     * Gives a significant relevance score boost to papers where the query
     * terms explicitly match the names of the authors.
     */
    private double calculateAuthorMatchBoost(Publication paper, String query) {
        if (query == null || query.isBlank()) return 0.0;
        List<String> queryTokens = com.citeright.nlp.TextPreprocessor.tokenize(query);
        String authors = paper.getAuthorsFormatted();
        if (authors == null || authors.isBlank()) return 0.0;
        List<String> authorTokens = com.citeright.nlp.TextPreprocessor.tokenize(authors);

        int matches = 0;
        for (String qToken : queryTokens) {
            if (authorTokens.contains(qToken)) {
                matches++;
            }
        }

        if (matches == 0) return 0.0;
        // Direct boost: +15.0 per matching token, up to max +40.0
        return Math.min(40.0, matches * 15.0);
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
