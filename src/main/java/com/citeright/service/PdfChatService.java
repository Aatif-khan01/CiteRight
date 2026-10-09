package com.citeright.service;

import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.nlp.TfIdfEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Chat with Paper service.
 * Uses BGE-M3 Neural RAG + Hybrid Keyword Boosting for state-of-the-art semantic quality.
 * Caches chunk embeddings per PDF in memory so follow-up questions respond in milliseconds.
 */
public class PdfChatService {

    private final GeminiAIService aiService;
    private final TfIdfEngine tfIdfEngine;
    private static final int CHUNK_SIZE = 400; // words per chunk

    // Cached index structure per PDF text
    private static class PdfChunkIndex {
        final List<String> chunks;
        final float[][] neuralEmbeddings; // BGE-M3 vectors (null if BGE-M3 disabled)

        PdfChunkIndex(List<String> chunks, float[][] neuralEmbeddings) {
            this.chunks = chunks;
            this.neuralEmbeddings = neuralEmbeddings;
        }
    }

    // In-memory cache keyed by PDF text hash code
    private static final Map<Integer, PdfChunkIndex> indexCache = new ConcurrentHashMap<>();

    public PdfChatService() {
        this.aiService = new GeminiAIService();
        this.tfIdfEngine = new TfIdfEngine();
    }

    public String askPdf(String question, String pdfText) {
        if (question == null || question.isBlank()) {
            return "Please ask a question.";
        }
        if (pdfText == null || pdfText.isEmpty()) {
            return "This PDF contains no extractable text.";
        }

        if (GeminiConfig.isGemini() && !GeminiConfig.isConfigured()) {
            return "⚠ **Gemini API key not set.** Please configure it in Settings.";
        }

        long t0 = System.currentTimeMillis();

        // 1. Get or build cached chunk index for this PDF
        int textHash = pdfText.hashCode();
        PdfChunkIndex index = indexCache.get(textHash);

        boolean useBgeM3 = GeminiConfig.isBgeM3() && BgeM3EmbeddingEngine.getInstance().isLoaded();

        if (index == null || (useBgeM3 && index.neuralEmbeddings == null)) {
            System.out.println("[PdfChat] Building chunk index for PDF (" + pdfText.length() + " chars)...");
            List<String> chunks = chunkText(pdfText, CHUNK_SIZE);
            float[][] neuralEmbeddings = null;

            if (useBgeM3) {
                try {
                    System.out.println("[PdfChat] Pre-computing BGE-M3 neural embeddings for " + chunks.size() + " chunks...");
                    BgeM3EmbeddingEngine engine = BgeM3EmbeddingEngine.getInstance();
                    neuralEmbeddings = new float[chunks.size()][];
                    for (int i = 0; i < chunks.size(); i++) {
                        neuralEmbeddings[i] = engine.getEmbedding(chunks.get(i));
                    }
                    System.out.println("[PdfChat] Pre-computed " + chunks.size() + " BGE-M3 embeddings in " + (System.currentTimeMillis() - t0) + "ms");
                } catch (Exception e) {
                    System.err.println("[PdfChat] Neural indexing failed: " + e.getMessage() + ". Using TF-IDF.");
                    neuralEmbeddings = null;
                }
            }

            index = new PdfChunkIndex(chunks, neuralEmbeddings);
            indexCache.put(textHash, index);
        }

        List<String> chunks = index.chunks;
        double[] scores = new double[chunks.size()];

        // 2. Compute similarity scores
        if (useBgeM3 && index.neuralEmbeddings != null) {
            // Neural RAG: Compute question embedding ONCE (~100ms)
            long tQ = System.currentTimeMillis();
            float[] questionVec = BgeM3EmbeddingEngine.getInstance().getEmbedding(question);
            System.out.println("[PdfChat] Question embedded in " + (System.currentTimeMillis() - tQ) + "ms");

            if (questionVec != null) {
                for (int i = 0; i < chunks.size(); i++) {
                    if (index.neuralEmbeddings[i] != null) {
                        // Fast dot product / cosine sim against cached chunk vector (<0.01ms)
                        scores[i] = BgeM3EmbeddingEngine.cosineSimilarity(questionVec, index.neuralEmbeddings[i]);
                    }
                }
            }
        } else {
            // TF-IDF RAG fallback
            tfIdfEngine.buildModel(chunks);
            Map<String, Double> questionVec = tfIdfEngine.computeTfIdfVector(question);
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Double> chunkVec = tfIdfEngine.computeTfIdfVector(chunks.get(i));
                scores[i] = TfIdfEngine.cosineSimilarity(questionVec, chunkVec);
            }
        }

        // 3. Keyword / Table Match Boosting (boosts exact chemical formulas, numbers, tables, terms)
        String[] keywords = question.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        for (int i = 0; i < chunks.size(); i++) {
            String chunkLower = chunks.get(i).toLowerCase();
            double boost = 0.0;
            for (String kw : keywords) {
                if (kw.length() > 2 && chunkLower.contains(kw)) {
                    boost += 0.12;
                }
            }
            // Additional boost for table rows
            if (question.toLowerCase().contains("table") && chunkLower.contains(" | ")) {
                boost += 0.20;
            }
            scores[i] += boost;
        }

        // 4. Select top 8 most relevant chunks
        List<String> topChunks = new ArrayList<>();
        for (int k = 0; k < 8 && k < chunks.size(); k++) {
            int bestIdx = -1;
            double bestScore = -100.0;
            for (int i = 0; i < chunks.size(); i++) {
                if (scores[i] > bestScore) {
                    bestScore = scores[i];
                    bestIdx = i;
                }
            }
            if (bestIdx != -1) {
                topChunks.add(chunks.get(bestIdx));
                scores[bestIdx] = -200.0; // mark as used
            }
        }

        long totalTime = System.currentTimeMillis() - t0;
        System.out.println("[PdfChat] Neural RAG retrieval completed in " + totalTime + "ms (" + topChunks.size() + " chunks selected)");

        // 5. Build prompt
        String context = String.join("\n\n---\n\n", topChunks);
        String systemPrompt = "You are CiteRight AI, an expert academic research assistant. " +
                "A researcher is reading a specific paper and asking you questions about it.\n\n" +
                "INSTRUCTIONS:\n" +
                "1. Answer ONLY based on the PDF text extracts provided below. Do NOT use outside knowledge.\n" +
                "2. Be thorough, clear, and precise. Use bullet points and bold headers when helpful.\n" +
                "3. Quote key numbers, values, chemical formulas, and phrases directly from the text.\n" +
                "4. If the text mentions specific figures, tables, or sections, reference them (e.g., 'As shown in Table 2...').\n" +
                "5. TABLE DATA: Text separated by ' | ' characters represents table columns. Reconstruct table rows accurately when answering.\n" +
                "6. If the answer is not in the provided text, state clearly: 'I could not find this information in the provided sections of the paper.'\n" +
                "7. Keep your answer academic and direct.\n\n" +
                "PDF TEXT EXTRACTS:\n" + context;

        // 6. Call AI
        return aiService.chat(systemPrompt, question);
    }

    private List<String> chunkText(String text, int wordsPerChunk) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        int overlap = wordsPerChunk / 4; // 25% overlap between chunks
        int step = wordsPerChunk - overlap;

        for (int start = 0; start < words.length; start += step) {
            StringBuilder currentChunk = new StringBuilder();
            int end = Math.min(start + wordsPerChunk, words.length);
            for (int i = start; i < end; i++) {
                if (currentChunk.length() > 0) currentChunk.append(" ");
                currentChunk.append(words[i]);
            }
            chunks.add(currentChunk.toString());
            if (end >= words.length) break;
        }
        return chunks;
    }
}
