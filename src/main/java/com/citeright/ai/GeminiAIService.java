package com.citeright.ai;

import com.citeright.model.Publication;
import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI Service supporting both Google Gemini (cloud) and Ollama (local).
 * Automatically routes to the configured provider.
 * Gracefully degrades if unconfigured.
 */
public class GeminiAIService {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private final HttpClient httpClient;
    private final Gson gson;
    
    public GeminiAIService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    // ── General Chat (routes to configured provider) ────────────────────────

    /**
     * Sends a chat request to the configured AI provider.
     * @param systemPrompt Instructions for the AI's behavior
     * @param userPrompt   The user's question with context
     * @return The AI's response text, or an error message
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (GeminiConfig.isOllama()) {
            String result = chatOllama(systemPrompt, userPrompt);
            // Auto-fallback: if Ollama fails and Gemini key exists, use Gemini
            if (result != null && result.startsWith("⚠") && GeminiConfig.isConfigured()) {
                return chatGemini(systemPrompt, userPrompt) + 
                       "\n\n💡 _Note: Ollama was unavailable, so I used Gemini instead. Check AI Settings to switch providers._";
            }
            return result;
        } else {
            return chatGemini(systemPrompt, userPrompt);
        }
    }

    // ── Gemini Cloud ────────────────────────────────────────────────────────

    private String chatGemini(String systemPrompt, String userPrompt) {
        String apiKey = GeminiConfig.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return "⚠ Gemini API key not configured. Go to AI Settings (⚙) to add your free API key.";
        }

        try {
            JsonObject requestBody = new JsonObject();

            // System instruction
            JsonObject systemInstruction = new JsonObject();
            JsonArray sysPartsArray = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysPartsArray.add(sysPart);
            systemInstruction.add("parts", sysPartsArray);
            requestBody.add("systemInstruction", systemInstruction);

            // User content
            JsonArray contents = new JsonArray();
            JsonObject contentObj = new JsonObject();
            contentObj.addProperty("role", "user");
            JsonArray parts = new JsonArray();
            JsonObject partObj = new JsonObject();
            partObj.addProperty("text", userPrompt);
            parts.add(partObj);
            contentObj.add("parts", parts);
            contents.add(contentObj);
            requestBody.add("contents", contents);

            // Generation config
            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("temperature", 0.3);
            genConfig.addProperty("maxOutputTokens", 8192);
            
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingBudget", 0);
            genConfig.add("thinkingConfig", thinkingConfig);
            
            requestBody.add("generationConfig", genConfig);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[GeminiAIService] Raw HTTP Response Body:\n" + response.body());
                return parseGeminiResponse(response.body());
            } else if (response.statusCode() == 429) {
                return "⚠ Rate limit or daily quota reached. Gemini's free tier allows 15 requests/min and 1,500 requests/day. Please try again later.";
            } else if (response.statusCode() == 400) {
                return "⚠ Invalid API key. Please check your Gemini API key in AI Settings.";
            } else {
                return "⚠ Gemini API error (HTTP " + response.statusCode() + "). Please try again.";
            }

        } catch (java.net.http.HttpTimeoutException e) {
            return "⚠ Request timed out. The Gemini server may be busy. Please try again.";
        } catch (Exception e) {
            return "⚠ Connection error: " + e.getMessage();
        }
    }

    private String parseGeminiResponse(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("candidates")) {
                JsonArray candidates = json.getAsJsonArray("candidates");
                if (!candidates.isEmpty()) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    JsonObject content = candidate.getAsJsonObject("content");
                    JsonArray outParts = content.getAsJsonArray("parts");
                    if (!outParts.isEmpty()) {
                        return outParts.get(0).getAsJsonObject().get("text").getAsString().trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GeminiAIService] Parse error: " + e.getMessage());
        }
        return "⚠ Could not parse AI response.";
    }

    // ── Ollama Local ────────────────────────────────────────────────────────

    private String chatOllama(String systemPrompt, String userPrompt) {
        String ollamaUrl = GeminiConfig.getOllamaUrl();
        String model = GeminiConfig.getOllamaModel();

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("prompt", userPrompt);
            requestBody.addProperty("system", systemPrompt);
            requestBody.addProperty("stream", false);

            // Ollama options
            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.3);
            options.addProperty("num_predict", 4096);
            requestBody.add("options", options);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(120)) // Ollama can be slow on first run
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("response")) {
                    return json.get("response").getAsString().trim();
                }
                return "⚠ Unexpected Ollama response format.";
            } else {
                return "⚠ Ollama error (HTTP " + response.statusCode() + "). Is the model '" + model + "' installed? Run: ollama pull " + model;
            }

        } catch (java.net.ConnectException e) {
            return "⚠ Cannot connect to Ollama at " + ollamaUrl + ".\n\nMake sure Ollama is running:\n1. Install from https://ollama.ai\n2. Run: ollama serve\n3. Pull a model: ollama pull " + model;
        } catch (java.net.http.HttpTimeoutException e) {
            return "⚠ Ollama timed out. The model may be loading for the first time. Please try again.";
        } catch (Exception e) {
            return "⚠ Ollama connection error: " + e.getMessage();
        }
    }

    /**
     * Tests if Ollama is reachable.
     * @return true if Ollama responds, false otherwise
     */
    public boolean testOllamaConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GeminiConfig.getOllamaUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Legacy: Evidence Extraction ─────────────────────────────────────────

    /**
     * Extracts exactly which sentence from a paper's abstract supports the user's claim.
     */
    public String extractEvidence(String claim, Publication paper) {
        if (paper.getAbstractText() == null || paper.getAbstractText().trim().isEmpty()) {
            try {
                String doi = paper.getDoi();
                if (doi != null && !doi.trim().isEmpty()) {
                    System.out.println("[Evidence] Abstract is empty. Fetching from Semantic Scholar using DOI: " + doi);
                    String abstractText = fetchAbstractFromSemanticScholar(doi);
                    if (abstractText != null && !abstractText.trim().isEmpty()) {
                        paper.setAbstractText(abstractText);
                        System.out.println("[Evidence] Successfully fetched abstract: " + abstractText.substring(0, Math.min(abstractText.length(), 60)) + "...");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Evidence] On-the-fly abstract fetch failed: " + e.getMessage());
            }
        }

        if (paper.getAbstractText() == null || paper.getAbstractText().trim().isEmpty()) {
            return null;
        }

        // --- Local BGE-M3 Neural Matcher (100% Offline & Instant) ---
        if (NeuralAvailability.isReady()) {
            try {
                System.out.println("[Evidence] Using local BGE-M3 neural engine to extract evidence.");
                float[] claimEmbedding = NeuralAvailability.embed(claim.trim());
                if (claimEmbedding != null) {
                    // Split abstract into sentences by punctuation followed by space
                    String[] sentences = paper.getAbstractText().split("(?<=[.!?])\\s+");
                    String bestSentence = null;
                    double bestScore = -1.0;

                    for (String sentence : sentences) {
                        sentence = sentence.trim();
                        if (sentence.length() < 10) continue; // Skip minor/short fragments

                        float[] sentenceEmbedding = NeuralAvailability.embed(sentence);
                        if (sentenceEmbedding != null) {
                            double sim = BgeM3EmbeddingEngine.cosineSimilarity(claimEmbedding, sentenceEmbedding);
                            if (sim > bestScore) {
                                bestScore = sim;
                                bestSentence = sentence;
                            }
                        }
                    }

                    System.out.println("[Evidence] Best neural match score: " + Math.round(bestScore * 100) + "%");
                    if (bestScore >= 0.30 && bestSentence != null) {
                        return bestSentence;
                    }
                }
            } catch (Exception e) {
                System.err.println("[Evidence] Neural extraction failed, falling back to LLM: " + e.getMessage());
            }
        }

        // --- Standard Fallback: Generative AI (Gemini or Ollama LLM) ---
        System.out.println("[Evidence] Falling back to LLM chat for evidence extraction.");
        String systemPrompt = "You output ONLY exact quotes from the given text. Do not add your own words.";
        String userPrompt = "Find the EXACT sentence in the following Abstract that provides evidence for the user's Claim. " +
                "If no sentence provides evidence, return exactly 'NONE'. If there is evidence, return ONLY the exact sentence.\n\n" +
                "Claim: " + claim + "\n\n" +
                "Title: " + paper.getTitle() + "\n" +
                "Abstract: " + paper.getAbstractText();

        String result = chat(systemPrompt, userPrompt);
        if (result != null && !result.startsWith("⚠") && !"NONE".equalsIgnoreCase(result.trim())) {
            // Strip quotes if wrapped
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length() - 1);
            }
            return result;
        }
        return null;
    }

    private String fetchAbstractFromSemanticScholar(String doi) {
        try {
            String url = "https://api.semanticscholar.org/graph/v1/paper/DOI:" + java.net.URLEncoder.encode(doi, java.nio.charset.StandardCharsets.UTF_8) + "?fields=abstract";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "CiteRight/1.0 (mailto:citeright@example.com)")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET().build();
            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (root.has("abstract") && !root.get("abstract").isJsonNull()) {
                    return root.get("abstract").getAsString().trim();
                }
            }
        } catch (Exception e) {
            System.err.println("[Evidence] Semantic Scholar API failed: " + e.getMessage());
        }
        return null;
    }
}
