package com.citeright.search;

import com.citeright.model.*;
import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Search engine that queries the OpenAlex API.
 * Returns REAL, verified academic papers with citation counts, DOIs, etc.
 * 
 * API: https://api.openalex.org/works?search=query
 * Rate limit: Very generous — no issues for normal use.
 * Data: 250M+ academic works, completely free and open.
 * 
 * Demonstrates: POLYMORPHISM — implements SearchEngine interface.
 */
public class SemanticScholarSearch implements SearchEngine {

    private static final String BASE_URL = "https://api.openalex.org/works";
    private static final String SELECT_FIELDS = "id,title,authorships,publication_year,cited_by_count,doi,primary_location,abstract_inverted_index";
    private static final int TIMEOUT_SECONDS = 20;

    private final HttpClient httpClient;
    private final KeywordExtractor keywordExtractor;

    public SemanticScholarSearch() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.keywordExtractor = new KeywordExtractor();

    }

    @Override
    public List<Publication> search(String query, int limit) {
        List<Publication> results = new ArrayList<>();

        try {
            // Extract keywords from the user's sentence
            String keywords = keywordExtractor.extract(query);
            if (keywords.isEmpty()) {
                System.out.println("[OpenAlex] No meaningful keywords extracted.");
                return results;
            }

            System.out.println("[OpenAlex] Searching for: " + keywords);

            // Build the API URL
            String encodedQuery = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = String.format("%s?search=%s&per_page=%d&select=%s&mailto=citeright@example.com",
                    BASE_URL, encodedQuery, Math.min(limit, 25), SELECT_FIELDS);

            // Make the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CiteRight/1.0 (Academic Citation Finder)")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                results = parseResponse(response.body());
                System.out.println("[OpenAlex] Found " + results.size() + " papers.");
            } else {
                System.out.println("[OpenAlex] API returned status: " + response.statusCode());
                String body = response.body();
                System.out.println("[OpenAlex] Response: " + body.substring(0, Math.min(300, body.length())));
            }

        } catch (Exception e) {
            System.err.println("[OpenAlex] Search failed: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Parses the JSON response from OpenAlex API into Publication objects.
     */
    private List<Publication> parseResponse(String jsonResponse) {
        List<Publication> papers = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray resultsArray = root.getAsJsonArray("results");

            if (resultsArray == null) return papers;

            for (JsonElement element : resultsArray) {
                try {
                    JsonObject paperJson = element.getAsJsonObject();
                    Publication paper = parsePaper(paperJson);
                    if (paper != null) {
                        papers.add(paper);
                    }
                } catch (Exception e) {
                    System.err.println("[OpenAlex] Error parsing paper: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[OpenAlex] Error parsing response: " + e.getMessage());
        }

        return papers;
    }

    /**
     * Parses a single paper JSON object from OpenAlex into a Publication.
     */
    private Publication parsePaper(JsonObject paperJson) {
        JournalArticle paper = new JournalArticle();

        // Paper ID (OpenAlex ID)
        if (paperJson.has("id") && !paperJson.get("id").isJsonNull()) {
            String id = paperJson.get("id").getAsString();
            // Extract the ID part from URL like "https://openalex.org/W2592929672"
            paper.setPaperId(id.replace("https://openalex.org/", ""));
        }

        // Title
        if (paperJson.has("title") && !paperJson.get("title").isJsonNull()) {
            paper.setTitle(paperJson.get("title").getAsString());
        } else {
            return null; // Skip papers without titles
        }

        // Year
        if (paperJson.has("publication_year") && !paperJson.get("publication_year").isJsonNull()) {
            paper.setYear(paperJson.get("publication_year").getAsInt());
        }

        // Citation count
        if (paperJson.has("cited_by_count") && !paperJson.get("cited_by_count").isJsonNull()) {
            paper.setCitationCount(paperJson.get("cited_by_count").getAsInt());
        }

        // DOI
        if (paperJson.has("doi") && !paperJson.get("doi").isJsonNull()) {
            String doi = paperJson.get("doi").getAsString();
            // Remove the https://doi.org/ prefix if present
            doi = doi.replace("https://doi.org/", "");
            paper.setDoi(doi);
            paper.setUrl("https://doi.org/" + doi);
        }

        // Venue / Journal from primary_location
        if (paperJson.has("primary_location") && !paperJson.get("primary_location").isJsonNull()) {
            JsonObject location = paperJson.getAsJsonObject("primary_location");
            if (location.has("source") && !location.get("source").isJsonNull()) {
                JsonObject source = location.getAsJsonObject("source");
                if (source.has("display_name") && !source.get("display_name").isJsonNull()) {
                    String venue = source.get("display_name").getAsString();
                    paper.setVenue(venue);
                    paper.setJournalName(venue);
                }
            }
            // Landing page URL as fallback
            if (paper.getUrl() == null && location.has("landing_page_url") && !location.get("landing_page_url").isJsonNull()) {
                paper.setUrl(location.get("landing_page_url").getAsString());
            }
        }

        // Authors from authorships array
        if (paperJson.has("authorships") && !paperJson.get("authorships").isJsonNull()) {
            JsonArray authorships = paperJson.getAsJsonArray("authorships");
            for (JsonElement authElement : authorships) {
                JsonObject authorship = authElement.getAsJsonObject();
                if (authorship.has("author") && !authorship.get("author").isJsonNull()) {
                    JsonObject authorObj = authorship.getAsJsonObject("author");
                    Author author = new Author();
                    if (authorObj.has("id") && !authorObj.get("id").isJsonNull()) {
                        author.setAuthorId(authorObj.get("id").getAsString().replace("https://openalex.org/", ""));
                    }
                    if (authorObj.has("display_name") && !authorObj.get("display_name").isJsonNull()) {
                        author.setName(authorObj.get("display_name").getAsString());
                    }
                    if (author.getName() != null) {
                        paper.addAuthor(author);
                    }
                }
            }
        }

        // Abstract from inverted index
        if (paperJson.has("abstract_inverted_index") && !paperJson.get("abstract_inverted_index").isJsonNull()) {
            String abstractText = reconstructAbstract(paperJson.getAsJsonObject("abstract_inverted_index"));
            paper.setAbstractText(abstractText);
        }

        return paper;
    }

    /**
     * Reconstructs abstract text from OpenAlex's inverted index format.
     * The inverted index maps words to their positions in the text.
     */
    private String reconstructAbstract(JsonObject invertedIndex) {
        try {
            TreeMap<Integer, String> positionMap = new TreeMap<>();

            for (Map.Entry<String, JsonElement> entry : invertedIndex.entrySet()) {
                String word = entry.getKey();
                JsonArray positions = entry.getValue().getAsJsonArray();
                for (JsonElement pos : positions) {
                    positionMap.put(pos.getAsInt(), word);
                }
            }

            StringBuilder sb = new StringBuilder();
            for (String word : positionMap.values()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(word);
            }

            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getSourceName() {
        return "OpenAlex";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "?search=test&per_page=1&select=title"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
