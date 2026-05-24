package com.citeright.service;

import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import com.citeright.nlp.TfIdfEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to handle Chat with Paper functionality.
 * Chunks a PDF's text, finds the most relevant chunks using TF-IDF, 
 * and generates an answer using an LLM.
 */
public class PdfChatService {

    private final GeminiAIService aiService;
    private final TfIdfEngine tfIdfEngine;
    private static final int CHUNK_SIZE = 500; // words per chunk

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

        // 1. Chunk the PDF text
        List<String> chunks = chunkText(pdfText, CHUNK_SIZE);

        List<String> topChunks = new ArrayList<>();
        double[] scores = new double[chunks.size()];

        // ── Try BGE-M3 Local Neural RAG First ────────────────────────────────
        if (com.citeright.ai.GeminiConfig.isBgeM3() && com.citeright.ai.BgeM3EmbeddingEngine.getInstance().isLoaded()) {
            try {
                System.out.println("[PdfChat] Performing BGE-M3 neural retrieval over " + chunks.size() + " chunks...");
                com.citeright.ai.BgeM3EmbeddingEngine neuralEngine = com.citeright.ai.BgeM3EmbeddingEngine.getInstance();
                float[] questionVec = neuralEngine.getEmbedding(question);

                if (questionVec != null) {
                    for (int i = 0; i < chunks.size(); i++) {
                        float[] chunkVec = neuralEngine.getEmbedding(chunks.get(i));
                        scores[i] = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(questionVec, chunkVec);
                    }
                }
            } catch (Exception e) {
                System.err.println("[PdfChat] Neural chunk retrieval failed: " + e.getMessage() + ". Falling back to TF-IDF.");
                // Fall back: trigger TF-IDF by zeroing scores
                scores = null;
            }
        } else {
            scores = null;
        }

        // ── Fallback: Local TF-IDF Chunk Retrieval ───────────────────────────
        if (scores == null) {
            System.out.println("[PdfChat] Performing TF-IDF keyword-based retrieval over " + chunks.size() + " chunks...");
            tfIdfEngine.buildModel(chunks);
            Map<String, Double> questionVec = tfIdfEngine.computeTfIdfVector(question);
            scores = new double[chunks.size()];
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Double> chunkVec = tfIdfEngine.computeTfIdfVector(chunks.get(i));
                scores[i] = TfIdfEngine.cosineSimilarity(questionVec, chunkVec);
            }
        }

        // Simple selection of top 3 chunks (Priority selection)
        for (int k = 0; k < 3 && k < chunks.size(); k++) {
            int bestIdx = -1;
            double bestScore = -1.0;
            for (int i = 0; i < chunks.size(); i++) {
                if (scores[i] > bestScore) {
                    bestScore = scores[i];
                    bestIdx = i;
                }
            }
            if (bestIdx != -1) {
                topChunks.add(chunks.get(bestIdx));
                scores[bestIdx] = -2.0; // mark as used
            }
        }

        // 3. Build prompts
        String context = String.join("\n\n---\n\n", topChunks);
        String systemPrompt = "You are CiteRight AI, an expert academic assistant. " +
                "You are helping a researcher understand a specific paper they are reading.\n\n" +
                "Use the following extracted text from the PDF to answer their question. " +
                "If the answer is not in the text, say 'I cannot find the answer in the provided PDF text.'\n\n" +
                "PDF TEXT EXTRACTS:\n" + context;

        // 4. Call AI
        return aiService.chat(systemPrompt, question);
    }

    private List<String> chunkText(String text, int wordsPerChunk) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int count = 0;

        for (String word : words) {
            currentChunk.append(word).append(" ");
            count++;
            if (count >= wordsPerChunk) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                count = 0;
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }
}
