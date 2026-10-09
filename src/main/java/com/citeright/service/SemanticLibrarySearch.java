package com.citeright.service;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.nlp.TfIdfEngine;

import java.util.*;

/**
 * Semantic search over the user's saved library using TF-IDF cosine similarity.
 *
 * Unlike keyword search (which requires exact word matches), this finds papers
 * that are conceptually similar to a query sentence — even if they use different
 * vocabulary.
 *
 * 100% local, 100% free, zero API keys, zero internet required.
 * Built entirely on the existing TF-IDF and TextPreprocessor infrastructure.
 */
public class SemanticLibrarySearch {

    /** Minimum similarity score to include a result (0.0–1.0) */
    private static final double MIN_THRESHOLD = 0.02;

    /**
     * Searches the library by semantic similarity to the query sentence.
     *
     * @param query   The sentence the researcher is writing / searching for
     * @param entries All active library entries to search across
     * @return Entries sorted by semantic relevance (most relevant first), with score
     */
    public List<ScoredEntry> search(String query, List<LibraryEntry> entries) {
        if (query == null || query.isBlank() || entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        // ── Try BGE-M3 Local Neural Search First ─────────────────────────────
        if (com.citeright.ai.NeuralAvailability.isReady()) {
            try {
                float[] queryVec = com.citeright.ai.NeuralAvailability.embed(query);

                if (queryVec != null) {
                    // Load all cached embeddings in one fast roundtrip
                    Map<Integer, float[]> cachedEmbeddings = com.citeright.ai.NeuralAvailability.getCachedEmbeddings();
                    com.citeright.database.PaperEmbeddingDAO embeddingDAO = new com.citeright.database.PaperEmbeddingDAO();
                    
                    List<ScoredEntry> results = new ArrayList<>();
                    
                    for (LibraryEntry entry : entries) {
                        int paperId = entry.getId();
                        float[] docVector = cachedEmbeddings.get(paperId);
                        
                        // Dynamic Fallback: if not yet cached by background indexer, compute on-the-fly and cache
                        if (docVector == null) {
                            String text = buildDocumentText(entry);
                            docVector = com.citeright.ai.NeuralAvailability.embed(text);
                            if (docVector != null) {
                                embeddingDAO.saveEmbedding(paperId, "bge-m3", "v1", docVector);
                            }
                        }
                        
                        if (docVector != null) {
                            double score = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(queryVec, docVector);
                            if (score >= 0.15) {
                                results.add(new ScoredEntry(entry, score));
                            }
                        }
                    }
                    
                    results.sort((a, b) -> Double.compare(b.score(), a.score()));
                    return results;
                }
            } catch (Exception e) {
                System.err.println("[SemanticSearch] Neural search failed, falling back to TF-IDF: " + e.getMessage());
            }
        }

        // ── Fallback: Local TF-IDF Cosine Similarity Search ──────────────────
        System.out.println("[SemanticSearch] Performing standard TF-IDF semantic search...");
        List<String> documents = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            documents.add(buildDocumentText(entry));
        }

        // Train a TF-IDF model on the user's library
        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(documents);

        // Compute the query vector
        Map<String, Double> queryVector = engine.computeTfIdfVector(query);

        // Score every library entry against the query
        List<ScoredEntry> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Double> docVector = engine.computeTfIdfVector(documents.get(i));
            double score = TfIdfEngine.cosineSimilarity(queryVector, docVector);

            if (score >= MIN_THRESHOLD) {
                results.add(new ScoredEntry(entries.get(i), score));
            }
        }

        // Sort by score descending
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results;
    }

    /**
     * Builds the searchable document text for an entry.
     * Combines title (weighted 3x) and abstract for better title relevance.
     */
    private String buildDocumentText(LibraryEntry entry) {
        Publication pub = entry.getPublication();
        if (pub == null) return "";

        StringBuilder sb = new StringBuilder();
        // Weight title more heavily by repeating it
        if (pub.getTitle() != null) {
            sb.append(pub.getTitle()).append(" ");
            sb.append(pub.getTitle()).append(" ");
            sb.append(pub.getTitle()).append(" ");
        }
        if (pub.getAbstractText() != null) {
            sb.append(pub.getAbstractText());
        }
        if (pub.getVenue() != null) {
            sb.append(" ").append(pub.getVenue());
        }
        return sb.toString();
    }

    /**
     * A library entry paired with its semantic similarity score.
     */
    public record ScoredEntry(LibraryEntry entry, double score) {
        /** Score as a percentage string, e.g. "87%" */
        public String scorePercent() {
            return Math.round(score * 100) + "%";
        }
    }
}
