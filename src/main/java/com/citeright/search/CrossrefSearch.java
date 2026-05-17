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
 * Search engine that queries the Crossref API.
 * Crossref has excellent coverage of recently published papers and 
 * handles chemical formulas, special characters, and niche journals well.
 * 
 * API: https://api.crossref.org/works?query=...
 * Rate limit: Generous with polite pool (mailto parameter).
 * Data: 150M+ scholarly works from major publishers.
 * 
 * Demonstrates: POLYMORPHISM — implements SearchEngine interface.
 */
public class CrossrefSearch implements SearchEngine {

    private static final String BASE_URL = "https://api.crossref.org/works";
    private static final int TIMEOUT_SECONDS = 25;

    private final HttpClient httpClient;

    public CrossrefSearch() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

    }

    @Override
    public List<Publication> search(String query, int limit) {
        List<Publication> results = new ArrayList<>();

        try {
            System.out.println("[Crossref] Searching for: " + query);

            // Build the API URL — use the original query for better matching
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?query=%s&rows=%d&select=DOI,title,author,published-print,published-online,is-referenced-by-count,container-title,abstract,volume,issue,page&mailto=citeright@example.com",
                    BASE_URL, encodedQuery, Math.min(limit, 25));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CiteRight/1.0 (Academic Citation Finder; mailto:citeright@example.com)")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                results = parseResponse(response.body());
                System.out.println("[Crossref] Found " + results.size() + " papers.");
            } else {
                System.out.println("[Crossref] API returned status: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("[Crossref] Search failed: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Parses the JSON response from Crossref API into Publication objects.
     */
    private List<Publication> parseResponse(String jsonResponse) {
        List<Publication> papers = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            if (message == null) return papers;

            JsonArray items = message.getAsJsonArray("items");
            if (items == null) return papers;

            for (JsonElement element : items) {
                try {
                    JsonObject paperJson = element.getAsJsonObject();
                    Publication paper = parsePaper(paperJson);
                    if (paper != null) {
                        papers.add(paper);
                    }
                } catch (Exception e) {
                    System.err.println("[Crossref] Error parsing paper: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Crossref] Error parsing response: " + e.getMessage());
        }

        return papers;
    }

    /**
     * Parses a single paper JSON object from Crossref into a Publication.
     */
    private Publication parsePaper(JsonObject paperJson) {
        JournalArticle paper = new JournalArticle();

        // DOI
        if (paperJson.has("DOI") && !paperJson.get("DOI").isJsonNull()) {
            String doi = paperJson.get("DOI").getAsString();
            paper.setDoi(doi);
            paper.setUrl("https://doi.org/" + doi);
            paper.setPaperId("crossref-" + doi);
        } else {
            return null; // Skip papers without DOIs
        }

        // Title — Crossref returns title as an array
        if (paperJson.has("title") && !paperJson.get("title").isJsonNull()) {
            JsonArray titleArray = paperJson.getAsJsonArray("title");
            if (titleArray != null && titleArray.size() > 0) {
                String title = titleArray.get(0).getAsString();
                // Clean HTML tags from title (Crossref sometimes includes them)
                title = title.replaceAll("<[^>]*>", "").trim();
                if (title.isEmpty()) return null;
                paper.setTitle(title);
            } else {
                return null;
            }
        } else {
            return null;
        }

        // Year — try published-print first, then published-online
        int year = extractYear(paperJson, "published-print");
        if (year == 0) {
            year = extractYear(paperJson, "published-online");
        }
        if (year > 0) {
            paper.setYear(year);
        }

        // Citation count
        if (paperJson.has("is-referenced-by-count") && !paperJson.get("is-referenced-by-count").isJsonNull()) {
            paper.setCitationCount(paperJson.get("is-referenced-by-count").getAsInt());
        }

        // Journal / venue
        if (paperJson.has("container-title") && !paperJson.get("container-title").isJsonNull()) {
            JsonArray containerTitle = paperJson.getAsJsonArray("container-title");
            if (containerTitle != null && containerTitle.size() > 0) {
                String venue = containerTitle.get(0).getAsString();
                paper.setVenue(venue);
                paper.setJournalName(venue);
            }
        }

        // Authors
        if (paperJson.has("author") && !paperJson.get("author").isJsonNull()) {
            JsonArray authorsArray = paperJson.getAsJsonArray("author");
            for (JsonElement authElement : authorsArray) {
                JsonObject authorObj = authElement.getAsJsonObject();
                Author author = new Author();

                String given = "";
                String family = "";
                if (authorObj.has("given") && !authorObj.get("given").isJsonNull()) {
                    given = authorObj.get("given").getAsString();
                }
                if (authorObj.has("family") && !authorObj.get("family").isJsonNull()) {
                    family = authorObj.get("family").getAsString();
                }

                String fullName = (given + " " + family).trim();
                if (!fullName.isEmpty()) {
                    author.setName(fullName);
                    if (authorObj.has("ORCID") && !authorObj.get("ORCID").isJsonNull()) {
                        author.setAuthorId(authorObj.get("ORCID").getAsString());
                    }
                    paper.addAuthor(author);
                }
            }
        }

        // Abstract
        if (paperJson.has("abstract") && !paperJson.get("abstract").isJsonNull()) {
            String abstractText = paperJson.get("abstract").getAsString();
            // Clean JATS XML tags from abstract
            abstractText = abstractText.replaceAll("<[^>]*>", "").trim();
            paper.setAbstractText(abstractText);
        }

        // Volume, issue, pages
        if (paperJson.has("volume") && !paperJson.get("volume").isJsonNull()) {
            paper.setVolume(paperJson.get("volume").getAsString());
        }
        if (paperJson.has("issue") && !paperJson.get("issue").isJsonNull()) {
            paper.setIssue(paperJson.get("issue").getAsString());
        }
        if (paperJson.has("page") && !paperJson.get("page").isJsonNull()) {
            paper.setPages(paperJson.get("page").getAsString());
        }

        return paper;
    }

    /**
     * Extracts the year from a Crossref date field.
     */
    private int extractYear(JsonObject paperJson, String fieldName) {
        try {
            if (paperJson.has(fieldName) && !paperJson.get(fieldName).isJsonNull()) {
                JsonObject dateObj = paperJson.getAsJsonObject(fieldName);
                if (dateObj.has("date-parts") && !dateObj.get("date-parts").isJsonNull()) {
                    JsonArray dateParts = dateObj.getAsJsonArray("date-parts");
                    if (dateParts.size() > 0) {
                        JsonArray firstDate = dateParts.get(0).getAsJsonArray();
                        if (firstDate.size() > 0) {
                            return firstDate.get(0).getAsInt();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore date parsing errors
        }
        return 0;
    }

    @Override
    public String getSourceName() {
        return "Crossref";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "?query=test&rows=1&select=DOI"))
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
