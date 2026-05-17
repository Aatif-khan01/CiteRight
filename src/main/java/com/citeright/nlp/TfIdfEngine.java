package com.citeright.nlp;

import java.util.*;

/**
 * TF-IDF (Term Frequency–Inverse Document Frequency) Engine.
 * 
 * Converts text documents into numerical vectors where:
 * - Common words (the, is, study) get LOW weights
 * - Rare, discriminative words (Dzyaloshinskii, heterostructure) get HIGH weights
 * 
 * Then uses Cosine Similarity to measure how "close" two documents are in meaning.
 * 
 * This is the SAME algorithm used by early Google Search, Apache Lucene,
 * and most information retrieval systems.
 * 
 * Demonstrates: Custom AI/NLP algorithm implemented from scratch.
 */
public class TfIdfEngine {

    // The vocabulary built from all documents in the current search result set
    private Map<String, Double> idfScores;
    private int totalDocuments;

    public TfIdfEngine() {
        this.idfScores = new HashMap<>();
        this.totalDocuments = 0;
    }

    /**
     * Builds the IDF (Inverse Document Frequency) model from a collection of documents.
     * Each "document" is a string representing a paper's title + abstract.
     * 
     * IDF(t) = log(N / (1 + df(t)))
     * where N = total docs, df(t) = number of docs containing term t
     * 
     * Rare terms get high IDF; common terms get low IDF.
     *
     * @param documents List of text documents (title + abstract concatenated)
     */
    public void buildModel(List<String> documents) {
        this.totalDocuments = documents.size();
        if (totalDocuments == 0) return;

        // Count document frequency for each term
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (String doc : documents) {
            // Get unique terms in this document
            List<String> tokens = TextPreprocessor.tokenize(doc);
            Set<String> uniqueTerms = new HashSet<>(tokens);

            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        // Calculate IDF for each term
        idfScores.clear();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double idf = Math.log((double) totalDocuments / (1.0 + entry.getValue()));
            idfScores.put(entry.getKey(), idf);
        }

        System.out.println("[TF-IDF] Model built: " + idfScores.size() + 
                " unique terms from " + totalDocuments + " documents");
    }

    /**
     * Computes the TF-IDF vector for a given text.
     * Each dimension in the vector corresponds to a term, and its value
     * is the TF-IDF weight: how important that term is in this specific document.
     *
     * @param text The text to vectorize
     * @return Map of term → TF-IDF weight
     */
    public Map<String, Double> computeTfIdfVector(String text) {
        List<String> tokens = TextPreprocessor.tokenize(text);
        Map<String, Double> tf = TextPreprocessor.computeTermFrequency(tokens);
        Map<String, Double> tfidfVector = new HashMap<>();

        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            String term = entry.getKey();
            double termFreq = entry.getValue();
            double idf = idfScores.getOrDefault(term, 
                    Math.log((double) totalDocuments / 1.0)); // Unknown terms get max IDF (very rare)
            tfidfVector.put(term, termFreq * idf);
        }

        return tfidfVector;
    }

    /**
     * Computes Cosine Similarity between two TF-IDF vectors.
     * 
     * CosineSim(A, B) = (A · B) / (|A| × |B|)
     * 
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     * This measures the ANGLE between two vectors, not their magnitude,
     * so document length doesn't affect the score.
     *
     * @param vectorA First TF-IDF vector (e.g., user's query)
     * @param vectorB Second TF-IDF vector (e.g., paper's abstract)
     * @return Cosine similarity score between 0.0 and 1.0
     */
    public static double cosineSimilarity(Map<String, Double> vectorA, Map<String, Double> vectorB) {
        if (vectorA.isEmpty() || vectorB.isEmpty()) return 0.0;

        // Dot product: Σ(A[i] * B[i])
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            if (vectorB.containsKey(entry.getKey())) {
                dotProduct += entry.getValue() * vectorB.get(entry.getKey());
            }
        }

        // Magnitude of A: sqrt(Σ(A[i]²))
        double magnitudeA = 0.0;
        for (double val : vectorA.values()) {
            magnitudeA += val * val;
        }
        magnitudeA = Math.sqrt(magnitudeA);

        // Magnitude of B: sqrt(Σ(B[i]²))
        double magnitudeB = 0.0;
        for (double val : vectorB.values()) {
            magnitudeB += val * val;
        }
        magnitudeB = Math.sqrt(magnitudeB);

        // Avoid division by zero
        if (magnitudeA == 0.0 || magnitudeB == 0.0) return 0.0;

        return dotProduct / (magnitudeA * magnitudeB);
    }

    /**
     * Convenience method: compute the cosine similarity between a query string
     * and a document string, using the pre-built IDF model.
     *
     * @param query The user's search sentence
     * @param document The paper's title + abstract
     * @return Similarity score between 0.0 and 1.0
     */
    public double similarity(String query, String document) {
        Map<String, Double> queryVector = computeTfIdfVector(query);
        Map<String, Double> docVector = computeTfIdfVector(document);
        return cosineSimilarity(queryVector, docVector);
    }

    /**
     * Returns the IDF score for a specific term.
     * Higher IDF means the term is rarer and more discriminative.
     */
    public double getIdf(String term) {
        return idfScores.getOrDefault(term.toLowerCase(), 0.0);
    }

    /**
     * Returns the number of unique terms in the model.
     */
    public int getVocabularySize() {
        return idfScores.size();
    }

    /**
     * Returns true if the model has been built with documents.
     */
    public boolean isReady() {
        return totalDocuments > 0 && !idfScores.isEmpty();
    }
}
