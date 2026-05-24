package com.citeright.service;

import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;

import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) orchestrator for "Chat with Your Library".
 *
 * Pipeline:
 *   1. User asks a question
 *   2. SemanticLibrarySearch finds the top N most relevant papers (TF-IDF)
 *   3. Their titles + abstracts are packed into a context prompt
 *   4. Gemini or Ollama generates a synthesized answer with citations
 *
 * This is the killer feature that makes CiteRight different from Mendeley/Zotero.
 */
public class LibraryChatService {

    private static final int MAX_CONTEXT_PAPERS = 15;

    private final LibraryService libraryService;
    private final SemanticLibrarySearch semanticSearch;
    private final GeminiAIService aiService;

    public LibraryChatService(LibraryService libraryService) {
        this.libraryService = libraryService;
        this.semanticSearch = new SemanticLibrarySearch();
        this.aiService = new GeminiAIService();
    }

    /**
     * The main RAG method. Takes a user question, retrieves relevant papers,
     * and generates an AI-powered answer grounded in the user's own library.
     *
     * @param question The researcher's natural language question
     * @return A ChatResponse containing the AI answer and the papers used as context
     */
    public ChatResponse ask(String question) {
        if (question == null || question.isBlank()) {
            return new ChatResponse("Please ask a question about your library.", List.of());
        }

        // Check if AI is configured
        if (GeminiConfig.isGemini() && !GeminiConfig.isConfigured()) {
            return new ChatResponse(
                "⚠ **Gemini API key not set.**\n\n" +
                "To use AI Chat, you need a free Google Gemini API key:\n" +
                "1. Click the ⚙ Settings icon above\n" +
                "2. Get your free key from Google AI Studio\n" +
                "3. Paste it and click Save\n\n" +
                "Or switch to **Ollama** for 100% local, private AI.",
                List.of()
            );
        }

        // Step 1: Retrieve all active papers
        List<LibraryEntry> allPapers = libraryService.getAllActive();
        if (allPapers.isEmpty()) {
            return new ChatResponse(
                "Your library is empty! Add some papers first, then I can help you analyze them.",
                List.of()
            );
        }

        // Step 2: Semantic search — find the most relevant papers
        List<SemanticLibrarySearch.ScoredEntry> scored = semanticSearch.search(question, allPapers);

        // Take the top N results
        List<SemanticLibrarySearch.ScoredEntry> topResults = scored.stream()
                .limit(MAX_CONTEXT_PAPERS)
                .toList();

        // If no papers match at all, still try with a broader context
        List<LibraryEntry> contextPapers;
        if (topResults.isEmpty()) {
            // Use the first few papers as general context
            contextPapers = allPapers.stream().limit(MAX_CONTEXT_PAPERS).toList();
        } else {
            contextPapers = topResults.stream()
                    .map(SemanticLibrarySearch.ScoredEntry::entry)
                    .toList();
        }

        // Step 3: Build the RAG prompt
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(question, contextPapers);

        // Step 4: Call AI
        String aiResponse = aiService.chat(systemPrompt, userPrompt);

        return new ChatResponse(aiResponse, contextPapers);
    }

    private String buildSystemPrompt() {
        return "You are CiteRight AI, an expert academic research assistant embedded inside a reference manager application. " +
               "You help researchers understand, compare, and synthesize their saved papers.\n\n" +
               "RULES:\n" +
               "1. Answer ONLY based on the provided paper abstracts. Do not make up information.\n" +
               "2. When referencing a paper, cite it by its number, e.g. [1], [2].\n" +
               "3. Be concise but thorough. Use bullet points for clarity.\n" +
               "4. If the papers don't contain enough information to answer, say so honestly.\n" +
               "5. Write in an academic but approachable tone.";
    }

    private String buildUserPrompt(String question, List<LibraryEntry> papers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here are the relevant papers from my library:\n\n");

        for (int i = 0; i < papers.size(); i++) {
            Publication pub = papers.get(i).getPublication();
            sb.append("--- Paper [").append(i + 1).append("] ---\n");
            sb.append("Title: ").append(pub.getTitle() != null ? pub.getTitle() : "Untitled").append("\n");
            sb.append("Authors: ").append(pub.getAuthorsShort()).append("\n");
            if (pub.getYear() > 0) sb.append("Year: ").append(pub.getYear()).append("\n");
            if (pub.getVenue() != null && !pub.getVenue().isEmpty())
                sb.append("Venue: ").append(pub.getVenue()).append("\n");
            String abs = pub.getAbstractText();
            if (abs != null && !abs.isEmpty()) {
                // Truncate very long abstracts to save tokens
                if (abs.length() > 800) abs = abs.substring(0, 800) + "...";
                sb.append("Abstract: ").append(abs).append("\n");
            } else {
                sb.append("Abstract: Not available.\n");
            }
            sb.append("\n");
        }

        sb.append("---\n\nMy question: ").append(question);
        return sb.toString();
    }

    /**
     * Result of a RAG query — contains the AI answer and which papers were used.
     */
    public record ChatResponse(String answer, List<LibraryEntry> contextPapers) {}
}
