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
 * Search engine that queries the Europe PMC API.
 * Europe PMC is a massive, completely free and unlimited database
 * that indexes life sciences, physics, materials science, and biomedical papers.
 * No API key is required, and there are no strict rate limits.
 * 
 * Demonstrates: POLYMORPHISM — implements SearchEngine interface.
 */
public class EuropePmcSearch implements SearchEngine {

    private static final String BASE_URL = "https://www.ebi.ac.uk/europepmc/webservices/rest/search";
    private static final int TIMEOUT_SECONDS = 20;

    private final HttpClient httpClient;

    public EuropePmcSearch() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

    }

    @Override
    public List<Publication> search(String query, int limit) {
        List<Publication> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        try {
            System.out.println("[EuropePMC] Searching for: " + query);

            // Europe PMC supports fuzzy searching and full sentences well
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&format=json&resultType=lite&pageSize=%d",
                    BASE_URL, encodedQuery, Math.min(limit, 25));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CiteRight/1.0 (Academic Citation Finder)")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                results = parseResponse(response.body());
                System.out.println("[EuropePMC] Found " + results.size() + " papers.");
            } else {
                System.out.println("[EuropePMC] API returned status: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("[EuropePMC] Search failed: " + e.getMessage());
        }

        return results;
    }

    private List<Publication> parseResponse(String jsonResponse) {
        List<Publication> papers = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            
            if (!root.has("resultList") || root.get("resultList").isJsonNull()) {
                return papers;
            }
            
            JsonObject resultList = root.getAsJsonObject("resultList");
            if (!resultList.has("result") || resultList.get("result").isJsonNull()) {
                return papers;
            }

            JsonArray items = resultList.getAsJsonArray("result");

            for (JsonElement element : items) {
                try {
                    JsonObject item = element.getAsJsonObject();
                    Publication paper = parsePaper(item);
                    if (paper != null && paper.getTitle() != null && !paper.getTitle().isEmpty()) {
                        papers.add(paper);
                    }
                } catch (Exception e) {
                    System.err.println("[EuropePMC] Error parsing paper: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[EuropePMC] Error parsing response: " + e.getMessage());
        }

        return papers;
    }

    private Publication parsePaper(JsonObject item) {
        JournalArticle paper = new JournalArticle();

        // DOI
        if (item.has("doi") && !item.get("doi").isJsonNull()) {
            String doi = item.get("doi").getAsString();
            paper.setDoi(doi);
            paper.setUrl("https://doi.org/" + doi);
        }

        // Title
        if (item.has("title") && !item.get("title").isJsonNull()) {
            // Strip HTML tags from title like <sub> and &lt;
            String title = item.get("title").getAsString()
                    .replaceAll("<[^>]*>", "")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
            paper.setTitle(title);
        }

        // Publication Year
        if (item.has("pubYear") && !item.get("pubYear").isJsonNull()) {
            try {
                paper.setYear(Integer.parseInt(item.get("pubYear").getAsString()));
            } catch (NumberFormatException e) {
                // Ignore parsing errors for year
            }
        }

        // Venue / Journal
        if (item.has("journalTitle") && !item.get("journalTitle").isJsonNull()) {
            String venue = item.get("journalTitle").getAsString();
            paper.setVenue(venue);
            paper.setJournalName(venue);
        }

        // Author string
        if (item.has("authorString") && !item.get("authorString").isJsonNull()) {
            String authorString = item.get("authorString").getAsString();
            String[] authorNames = authorString.split(",");
            for (String authorName : authorNames) {
                String cleanName = authorName.trim();
                // Avoid empty authors or dots
                if (!cleanName.isEmpty() && !cleanName.equals(".")) {
                    Author author = new Author(cleanName);
                    paper.addAuthor(author);
                }
            }
        }

        // Citations
        if (item.has("citedByCount") && !item.get("citedByCount").isJsonNull()) {
            paper.setCitationCount(item.get("citedByCount").getAsInt());
        }

        // ID
        if (item.has("id") && !item.get("id").isJsonNull()) {
            paper.setPaperId("EPMC-" + item.get("id").getAsString());
        }

        return paper;
    }

    @Override
    public String getSourceName() {
        return "Europe PMC";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "?query=test&format=json&resultType=lite&pageSize=1"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
