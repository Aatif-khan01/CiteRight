package com.citeright.service;

import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import com.citeright.ai.NeuralAvailability;
import com.citeright.model.LibraryEntry;
import com.citeright.model.PaperRelationship;
import com.citeright.model.Publication;
import com.google.gson.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered relationship inference between papers using Gemini.
 *
 * Analyzes batches of paper abstracts and returns structured relationship
 * suggestions (SUPPORTS, CONTRADICTS, EXTENDS, METHODOLOGY) with confidence
 * scores and natural-language reasoning.
 *
 * Suggestions are persisted as AI_SUGGESTED relationships that the user
 * can confirm or dismiss with a single click.
 *
 * Rate-limit aware: respects Gemini's 15 RPM / 1,500 RPD free tier.
 * When the API is exhausted or unconfigured, automatically falls back to
 * a Local Semantic Rule Engine that infers relationships from keyword
 * overlap, chronological ordering, and methodology term detection.
 */
public class GraphAIService {

    private final GeminiAIService aiService;
    private final com.citeright.database.PaperRelationshipDAO relationshipDAO;
    private final Gson gson = new Gson();

    // ── Stopwords for local analysis ─────────────────────────────────────────
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "shall", "can", "this", "that",
            "these", "those", "it", "its", "we", "our", "they", "their", "them",
            "he", "she", "his", "her", "which", "who", "whom", "what", "when",
            "where", "how", "not", "no", "nor", "if", "then", "than", "so",
            "very", "just", "also", "more", "most", "much", "many", "such",
            "each", "every", "all", "both", "few", "some", "any", "other",
            "into", "over", "after", "before", "between", "under", "about",
            "up", "out", "off", "through", "during", "only", "own", "same",
            "while", "because", "until", "against", "above", "below", "upon",
            "used", "using", "based", "paper", "study", "results", "show",
            "propose", "proposed", "approach", "method", "methods", "however",
            "therefore", "although", "thus", "presented", "present", "new"
    );

    // ── Keyword sets for relationship type inference ─────────────────────────
    private static final Set<String> CONTRADICTION_KEYWORDS = Set.of(
            "contradict", "contradicts", "contradicted", "contradiction",
            "challenge", "challenges", "challenged", "dispute", "disputes",
            "refute", "refutes", "refuted", "oppose", "opposes", "opposed",
            "conflict", "conflicts", "conflicting", "disagree", "disagrees",
            "limitation", "limitations", "flaw", "flaws", "failure", "fails",
            "counter", "counterevidence", "critique", "criticism", "criticize",
            "disprove", "debunk", "incorrect", "erroneous", "misleading",
            "overestimate", "underestimate", "bias", "biased", "artifact"
    );

    private static final Set<String> EXTENSION_KEYWORDS = Set.of(
            "extend", "extends", "extended", "extension", "build", "builds",
            "improve", "improves", "improved", "improvement", "enhance",
            "enhances", "enhanced", "enhancement", "advance", "advances",
            "advanced", "generalize", "generalizes", "generalized",
            "augment", "augments", "augmented", "refine", "refines",
            "refined", "refinement", "scale", "scales", "scaled",
            "adapt", "adapts", "adapted", "adaptation", "evolve",
            "modify", "modifies", "modified", "modification", "upgrade",
            "outperform", "outperforms", "outperformed", "surpass",
            "novel", "innovative", "state-of-the-art", "superior"
    );

    private static final Set<String> METHODOLOGY_KEYWORDS = Set.of(
            "cnn", "rnn", "lstm", "transformer", "bert", "gpt", "resnet",
            "vgg", "gan", "vae", "autoencoder", "diffusion", "attention",
            "convolution", "convolutional", "recurrent", "reinforcement",
            "supervised", "unsupervised", "self-supervised", "semi-supervised",
            "classification", "segmentation", "detection", "regression",
            "clustering", "embedding", "fine-tuning", "pretraining",
            "transfer", "few-shot", "zero-shot", "meta-learning",
            "cross-validation", "ablation", "benchmark", "dataset",
            "imagenet", "cifar", "mnist", "coco", "pascal", "glue",
            "fmri", "eeg", "mri", "ct-scan", "x-ray", "ultrasound",
            "pcr", "crispr", "sequencing", "proteomics", "genomics",
            "monte-carlo", "bayesian", "markov", "gradient-descent",
            "backpropagation", "dropout", "batch-normalization",
            "random-forest", "svm", "xgboost", "lightgbm", "catboost",
            "pytorch", "tensorflow", "keras", "scikit-learn", "huggingface"
    );

    /** Callback for when AI suggestions are ready */
    public interface SuggestionCallback {
        /**
         * Called when suggestions have been computed.
         * @param suggestions The list of inferred relationships
         * @param isFallback  true if generated by the local semantic engine
         *                    (Gemini was unavailable/exhausted), false if from the AI API
         */
        void onSuggestionsReady(List<SuggestedRelationship> suggestions, boolean isFallback);
        void onError(String errorMessage);
    }

    /** A suggested relationship from AI analysis */
    public static class SuggestedRelationship {
        public final int sourcePaperId;
        public final int targetPaperId;
        public final String sourceTitle;
        public final String targetTitle;
        public final String type;        // SUPPORTS, CONTRADICTS, EXTENDS, METHODOLOGY
        public final String reasoning;   // AI's explanation
        public final double confidence;  // 0.0 - 1.0

        public SuggestedRelationship(int srcId, int tgtId, String srcTitle, String tgtTitle,
                                     String type, String reasoning, double confidence) {
            this.sourcePaperId = srcId;
            this.targetPaperId = tgtId;
            this.sourceTitle = srcTitle;
            this.targetTitle = tgtTitle;
            this.type = type;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }
    }

    public GraphAIService() {
        this.aiService = new GeminiAIService();
        this.relationshipDAO = new com.citeright.database.PaperRelationshipDAO();
    }

    /**
     * Analyzes a batch of papers and returns suggested relationships.
     * Runs asynchronously on a background thread.
     *
     * Strategy:
     * 1. Try Gemini/Ollama API first (if configured).
     * 2. If API returns a rate-limit error (429), connection failure, or
     *    is not configured at all, automatically fall back to the
     *    Local Semantic Rule Engine.
     * 3. The callback's {@code isFallback} flag tells the UI which engine
     *    produced the results so it can display the appropriate toast.
     *
     * @param papers   Papers to analyze (recommended: 3-10 for token efficiency)
     * @param callback Called on the background thread with results
     */
    public void analyzeRelationships(List<LibraryEntry> papers, SuggestionCallback callback) {
        if (papers.size() < 2) {
            callback.onError("Need at least 2 papers to analyze relationships.");
            return;
        }

        // Cap at 10 papers per batch (token budget)
        List<LibraryEntry> batch = papers.size() > 10 ? papers.subList(0, 10) : papers;

        new Thread(() -> {
            try {
                List<SuggestedRelationship> suggestions = null;
                boolean isFallback = true;

                // ── Try cloud AI first ───────────────────────────────────────
                if (GeminiConfig.isConfigured() || GeminiConfig.isOllama()) {
                    try {
                        suggestions = performAnalysis(batch);
                        isFallback = false;
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        System.err.println("[GraphAI] Cloud AI failed, falling back to local engine: " + msg);
                    }
                } else {
                    System.out.println("[GraphAI] No AI provider configured — using local semantic engine.");
                }

                // ── Fallback: Local Semantic Rule Engine ─────────────────────
                if (suggestions == null) {
                    suggestions = performLocalAnalysis(batch);
                    isFallback = true;
                }

                callback.onSuggestionsReady(suggestions, isFallback);
            } catch (Throwable t) {
                System.err.println("[GraphAI] Error in relationship analysis thread: " + t.getMessage());
                t.printStackTrace();
                callback.onError("AI Analysis failed: " + t.getMessage());
            }
        }, "GraphAI-Analyzer").start();
    }

    /**
     * Persist AI suggestions to the database as AI_SUGGESTED relationships.
     */
    public void saveSuggestions(List<SuggestedRelationship> suggestions) {
        List<PaperRelationship> rels = new ArrayList<>();
        for (SuggestedRelationship s : suggestions) {
            PaperRelationship rel = new PaperRelationship();
            rel.setSourcePaperId(s.sourcePaperId);
            rel.setTargetPaperId(s.targetPaperId);
            rel.setRelationshipType(s.type);
            rel.setReasoning(s.reasoning);
            rel.setConfidence(s.confidence);
            rel.setSource(PaperRelationship.Source.AI_SUGGESTED);
            rel.setDismissed(false);
            rels.add(rel);
        }
        relationshipDAO.insertBatch(rels);
    }

    // ── Cloud AI analysis ────────────────────────────────────────────────────

    private List<SuggestedRelationship> performAnalysis(List<LibraryEntry> papers) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(papers);

        String response = aiService.chat(systemPrompt, userPrompt);

        if (response == null) {
            throw new RuntimeException("AI service returned empty response.");
        }
        if (response.startsWith("⚠")) {
            throw new RuntimeException(response.substring(1).trim());
        }

        return parseResponse(response, papers);
    }

    private String buildSystemPrompt() {
        return """
            You are an expert academic research analyst. Your task is to analyze the relationships between academic papers.
            
            For each meaningful pair of papers, determine if one of these relationships exists:
            - SUPPORTS: Paper A provides evidence that strengthens Paper B's claims
            - CONTRADICTS: Paper A presents findings that conflict with Paper B's conclusions
            - EXTENDS: Paper A builds upon or advances the work introduced in Paper B
            - METHODOLOGY: Papers A and B use the same experimental approach or technique
            
            Only report relationships you are confident about. Do NOT force connections where none exist.
            
            Respond ONLY with a valid JSON array. No markdown, no explanation outside the JSON.
            Each element must have these exact fields:
            {
              "source_index": <integer, 0-based index of source paper>,
              "target_index": <integer, 0-based index of target paper>,
              "type": "<SUPPORTS|CONTRADICTS|EXTENDS|METHODOLOGY>",
              "reasoning": "<1-2 sentence explanation>",
              "confidence": <float between 0.0 and 1.0>
            }
            
            If no relationships exist, return an empty array: []
            """;
    }

    private String buildUserPrompt(List<LibraryEntry> papers) {
        StringBuilder sb = new StringBuilder("Analyze relationships between these papers:\n\n");
        for (int i = 0; i < papers.size(); i++) {
            Publication pub = papers.get(i).getPublication();
            sb.append("[").append(i).append("] ");
            sb.append("Title: ").append(pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled").append("\n");
            sb.append("Year: ").append(pub != null ? pub.getYear() : "Unknown").append("\n");
            String abstractText = pub != null ? pub.getAbstractText() : null;
            if (abstractText != null && !abstractText.isEmpty()) {
                // Truncate long abstracts to save tokens
                if (abstractText.length() > 500) {
                    abstractText = abstractText.substring(0, 500) + "...";
                }
                sb.append("Abstract: ").append(abstractText).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<SuggestedRelationship> parseResponse(String response, List<LibraryEntry> papers) {
        List<SuggestedRelationship> suggestions = new ArrayList<>();

        try {
            // Extract JSON array from response globally (handles markdown code blocks and preamble text)
            String json = response.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            JsonArray array = JsonParser.parseString(json).getAsJsonArray();

            for (JsonElement elem : array) {
                try {
                    JsonObject obj = elem.getAsJsonObject();
                    int srcIdx = obj.get("source_index").getAsInt();
                    int tgtIdx = obj.get("target_index").getAsInt();
                    String type = obj.get("type").getAsString().toUpperCase();
                    String reasoning = obj.has("reasoning") ? obj.get("reasoning").getAsString() : "";
                    double confidence = obj.has("confidence") ? obj.get("confidence").getAsDouble() : 0.7;

                    // Validate
                    if (srcIdx < 0 || srcIdx >= papers.size() || tgtIdx < 0 || tgtIdx >= papers.size()) continue;
                    if (srcIdx == tgtIdx) continue;
                    if (!Set.of("SUPPORTS", "CONTRADICTS", "EXTENDS", "METHODOLOGY").contains(type)) continue;
                    confidence = Math.max(0.0, Math.min(1.0, confidence));

                    LibraryEntry srcEntry = papers.get(srcIdx);
                    LibraryEntry tgtEntry = papers.get(tgtIdx);
                    String srcTitle = srcEntry.getPublication() != null ? srcEntry.getPublication().getTitle() : "Untitled";
                    String tgtTitle = tgtEntry.getPublication() != null ? tgtEntry.getPublication().getTitle() : "Untitled";

                    suggestions.add(new SuggestedRelationship(
                            srcEntry.getId(), tgtEntry.getId(),
                            srcTitle, tgtTitle,
                            type, reasoning, confidence
                    ));
                } catch (Exception e) {
                    System.err.println("[GraphAI] Skipping malformed relationship: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[GraphAI] Failed to parse AI response: " + e.getMessage());
            System.err.println("[GraphAI] Raw response: " + response);
        }

        System.out.println("[GraphAI] Parsed " + suggestions.size() + " relationship suggestions");
        return suggestions;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LOCAL SEMANTIC RULE ENGINE  —  Offline fallback when Gemini is exhausted
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Performs local rule-based relationship inference using:
     * 1. Tokenized keyword overlap between titles and abstracts.
     * 2. Chronological ordering (newer paper → older paper for EXTENDS).
     * 3. Methodology keyword detection (shared techniques/models).
     * 4. Contradiction keyword detection (conflict/dispute terms).
     *
     * Returns at most 15 high-confidence suggestions to avoid clutter.
     */
    private List<SuggestedRelationship> performLocalAnalysis(List<LibraryEntry> papers) {
        System.out.println("[GraphAI-Local] Running local semantic rule engine on " + papers.size() + " papers...");

        // ── Check if neural embeddings are available for confidence boosting ──
        boolean neuralReady = NeuralAvailability.isReady();
        Map<Integer, float[]> cachedEmbeddings = null;
        if (neuralReady) {
            cachedEmbeddings = NeuralAvailability.getCachedEmbeddings();
            System.out.println("[GraphAI-Local] \uD83E\uDDE0 Neural similarity boosting active (" + cachedEmbeddings.size() + " embeddings cached).");
        }

        // Build token sets for each paper
        List<Set<String>> paperTokens = new ArrayList<>();
        List<Set<String>> abstractTokens = new ArrayList<>();
        for (LibraryEntry entry : papers) {
            Publication pub = entry.getPublication();
            paperTokens.add(tokenize(pub != null && pub.getTitle() != null ? pub.getTitle() : ""));
            String absText = pub != null && pub.getAbstractText() != null ? pub.getAbstractText() : "";
            abstractTokens.add(tokenize(absText));
        }

        // Candidate list: score every pair
        List<ScoredCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < papers.size(); i++) {
            for (int j = i + 1; j < papers.size(); j++) {
                Publication pubA = papers.get(i).getPublication();
                Publication pubB = papers.get(j).getPublication();
                if (pubA == null || pubB == null) continue;

                Set<String> tokensA = paperTokens.get(i);
                Set<String> tokensB = paperTokens.get(j);
                Set<String> absA = abstractTokens.get(i);
                Set<String> absB = abstractTokens.get(j);

                // Combined token pool for each paper (title + abstract)
                Set<String> allA = new HashSet<>(tokensA);
                allA.addAll(absA);
                Set<String> allB = new HashSet<>(tokensB);
                allB.addAll(absB);

                // Compute Jaccard similarity on combined tokens
                Set<String> intersection = new HashSet<>(allA);
                intersection.retainAll(allB);
                Set<String> union = new HashSet<>(allA);
                union.addAll(allB);

                if (union.isEmpty()) continue;
                double jaccard = (double) intersection.size() / union.size();

                // Skip very low overlap pairs
                if (jaccard < 0.05 && intersection.size() < 3) continue;

                // Determine relationship type by checking keyword categories
                String type;
                String reasoning;
                double confidence;

                // Check methodology overlap first (highest specificity)
                Set<String> sharedMethods = new HashSet<>(intersection);
                sharedMethods.retainAll(METHODOLOGY_KEYWORDS);

                // Check contradiction keywords in either abstract
                Set<String> contradictionHitsA = new HashSet<>(absA);
                contradictionHitsA.retainAll(CONTRADICTION_KEYWORDS);
                Set<String> contradictionHitsB = new HashSet<>(absB);
                contradictionHitsB.retainAll(CONTRADICTION_KEYWORDS);
                boolean hasContradiction = !contradictionHitsA.isEmpty() || !contradictionHitsB.isEmpty();

                // Check extension keywords
                Set<String> extensionHitsA = new HashSet<>(absA);
                extensionHitsA.retainAll(EXTENSION_KEYWORDS);
                Set<String> extensionHitsB = new HashSet<>(absB);
                extensionHitsB.retainAll(EXTENSION_KEYWORDS);
                boolean hasExtension = !extensionHitsA.isEmpty() || !extensionHitsB.isEmpty();

                // Determine direction: newer paper is the "source" that builds on older
                int srcIdx, tgtIdx;
                int yearA = pubA.getYear();
                int yearB = pubB.getYear();
                if (yearA >= yearB) {
                    srcIdx = i;
                    tgtIdx = j;
                } else {
                    srcIdx = j;
                    tgtIdx = i;
                }

                // Shared keyword summary for reasoning
                List<String> sharedKeywords = intersection.stream()
                        .sorted()
                        .limit(5)
                        .collect(Collectors.toList());
                String keywordSummary = String.join(", ", sharedKeywords);

                // ── Neural confidence boost ──────────────────────────────────
                // When BGE-M3 is available, use cosine similarity to adjust confidence
                double neuralSimilarity = -1.0;
                if (neuralReady && cachedEmbeddings != null) {
                    float[] embA = cachedEmbeddings.get(papers.get(i).getId());
                    float[] embB = cachedEmbeddings.get(papers.get(j).getId());
                    if (embA != null && embB != null) {
                        neuralSimilarity = BgeM3EmbeddingEngine.cosineSimilarity(embA, embB);
                    }
                }

                if (!sharedMethods.isEmpty() && sharedMethods.size() >= 1) {
                    type = "METHODOLOGY";
                    String methodNames = sharedMethods.stream().limit(3).collect(Collectors.joining(", "));
                    reasoning = "Both papers share methodology/technique terms: " + methodNames + ". " +
                                "Additional overlapping keywords: " + keywordSummary + ".";
                    confidence = Math.min(0.85, 0.55 + sharedMethods.size() * 0.10);
                } else if (hasContradiction && jaccard > 0.08) {
                    type = "CONTRADICTS";
                    Set<String> allContra = new HashSet<>(contradictionHitsA);
                    allContra.addAll(contradictionHitsB);
                    String contraTerms = allContra.stream().limit(3).collect(Collectors.joining(", "));
                    reasoning = "Detected contradiction/conflict language (" + contraTerms + ") " +
                                "combined with topical overlap on: " + keywordSummary + ".";
                    confidence = Math.min(0.75, 0.45 + jaccard * 2.0);
                } else if (hasExtension && yearA != yearB) {
                    type = "EXTENDS";
                    Set<String> allExt = new HashSet<>(extensionHitsA);
                    allExt.addAll(extensionHitsB);
                    String extTerms = allExt.stream().limit(3).collect(Collectors.joining(", "));
                    reasoning = "Newer paper uses extension language (" + extTerms + ") " +
                                "and shares topical keywords with older paper: " + keywordSummary + ".";
                    confidence = Math.min(0.80, 0.50 + jaccard * 2.5);
                } else if (jaccard >= 0.10 || intersection.size() >= 4) {
                    type = "SUPPORTS";
                    reasoning = "Strong topical overlap detected on keywords: " + keywordSummary + ". " +
                                "Papers appear to work in the same research area with aligned findings.";
                    confidence = Math.min(0.75, 0.40 + jaccard * 3.0);
                } else if (neuralSimilarity >= 0.60 && jaccard < 0.10) {
                    // Neural-only catch: papers are semantically similar but use different vocabulary
                    type = "SUPPORTS";
                    reasoning = "Neural embedding analysis detected high semantic similarity (" +
                                String.format("%.0f%%", neuralSimilarity * 100) +
                                ") despite low keyword overlap. Papers likely address related research topics.";
                    confidence = Math.min(0.70, 0.35 + neuralSimilarity * 0.5);
                } else {
                    continue; // Overlap too weak to suggest any relationship
                }

                // Apply neural similarity as confidence multiplier when available
                if (neuralSimilarity >= 0.0) {
                    if (neuralSimilarity >= 0.75) {
                        confidence = Math.min(0.95, confidence * 1.15); // Boost high-similarity pairs
                    } else if (neuralSimilarity < 0.30) {
                        confidence *= 0.70; // Reduce confidence for semantically distant pairs
                    }
                }

                candidates.add(new ScoredCandidate(srcIdx, tgtIdx, type, reasoning, confidence));
            }
        }

        // Sort by confidence descending, cap at 15
        candidates.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        List<SuggestedRelationship> results = new ArrayList<>();
        int limit = Math.min(candidates.size(), 15);

        for (int k = 0; k < limit; k++) {
            ScoredCandidate c = candidates.get(k);
            LibraryEntry srcEntry = papers.get(c.srcIdx);
            LibraryEntry tgtEntry = papers.get(c.tgtIdx);
            String srcTitle = srcEntry.getPublication() != null ? srcEntry.getPublication().getTitle() : "Untitled";
            String tgtTitle = tgtEntry.getPublication() != null ? tgtEntry.getPublication().getTitle() : "Untitled";

            results.add(new SuggestedRelationship(
                    srcEntry.getId(), tgtEntry.getId(),
                    srcTitle, tgtTitle,
                    c.type,
                    "[Local Engine] " + c.reasoning,
                    c.confidence
            ));
        }

        System.out.println("[GraphAI-Local] Generated " + results.size() + " suggestions from local analysis.");
        return results;
    }

    /**
     * Tokenizes a text string: lowercases, splits on non-alphanumeric boundaries,
     * removes stopwords and tokens shorter than 3 characters.
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Collections.emptySet();
        String[] words = text.toLowerCase().split("[^a-z0-9-]+");
        Set<String> tokens = new HashSet<>();
        for (String w : words) {
            if (w.length() >= 3 && !STOPWORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens;
    }

    /** Internal scored candidate for ranking before output. */
    private static class ScoredCandidate {
        final int srcIdx;
        final int tgtIdx;
        final String type;
        final String reasoning;
        final double confidence;

        ScoredCandidate(int srcIdx, int tgtIdx, String type, String reasoning, double confidence) {
            this.srcIdx = srcIdx;
            this.tgtIdx = tgtIdx;
            this.type = type;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }
    }
}
