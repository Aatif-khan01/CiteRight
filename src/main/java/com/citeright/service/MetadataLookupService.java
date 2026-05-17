package com.citeright.service;

import com.citeright.model.*;
import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Looks up paper metadata by DOI, ArXiv ID, or PMID using free APIs.
 */
public class MetadataLookupService {

    private final HttpClient httpClient;

    public MetadataLookupService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Auto-detect identifier type and look up metadata */
    public Publication lookup(String identifier) throws Exception {
        identifier = identifier.trim();
        if (identifier.startsWith("10.")) return lookupByDoi(identifier);
        if (identifier.matches("\\d+\\.\\d+")) return lookupByArxiv(identifier);
        if (identifier.matches("\\d+")) return lookupByPmid(identifier);
        // Try as DOI if contains slash
        if (identifier.contains("/")) return lookupByDoi(identifier);
        throw new Exception("Cannot detect identifier type. Use DOI (10.xxx), ArXiv (XXXX.XXXXX), or PMID (numeric).");
    }

    public Publication lookupByDoi(String doi) throws Exception {
        String url = "https://api.crossref.org/works/" + URLEncoder.encode(doi, StandardCharsets.UTF_8);
        String json = httpGet(url);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject msg = root.getAsJsonObject("message");
        if (msg == null) throw new Exception("DOI not found: " + doi);

        JournalArticle paper = new JournalArticle();
        paper.setDoi(doi);
        paper.setUrl("https://doi.org/" + doi);
        paper.setPaperId("doi-" + doi);

        if (msg.has("title") && !msg.get("title").isJsonNull()) {
            JsonArray titles = msg.getAsJsonArray("title");
            if (titles.size() > 0) paper.setTitle(titles.get(0).getAsString().replaceAll("<[^>]*>", "").trim());
        }
        if (msg.has("author") && !msg.get("author").isJsonNull()) {
            for (JsonElement ae : msg.getAsJsonArray("author")) {
                JsonObject ao = ae.getAsJsonObject();
                String given = ao.has("given") ? ao.get("given").getAsString() : "";
                String family = ao.has("family") ? ao.get("family").getAsString() : "";
                String name = (given + " " + family).trim();
                if (!name.isEmpty()) paper.addAuthor(new Author(name));
            }
        }
        extractYear(msg, paper);
        if (msg.has("container-title") && !msg.get("container-title").isJsonNull()) {
            JsonArray ct = msg.getAsJsonArray("container-title");
            if (ct.size() > 0) { paper.setVenue(ct.get(0).getAsString()); paper.setJournalName(ct.get(0).getAsString()); }
        }
        if (msg.has("abstract") && !msg.get("abstract").isJsonNull())
            paper.setAbstractText(msg.get("abstract").getAsString().replaceAll("<[^>]*>", "").trim());
        if (msg.has("is-referenced-by-count")) paper.setCitationCount(msg.get("is-referenced-by-count").getAsInt());
        if (msg.has("volume") && !msg.get("volume").isJsonNull()) paper.setVolume(msg.get("volume").getAsString());
        if (msg.has("issue") && !msg.get("issue").isJsonNull()) paper.setIssue(msg.get("issue").getAsString());
        if (msg.has("page") && !msg.get("page").isJsonNull()) paper.setPages(msg.get("page").getAsString());
        return paper;
    }

    public Publication lookupByArxiv(String arxivId) throws Exception {
        String url = "http://export.arxiv.org/api/query?id_list=" + URLEncoder.encode(arxivId, StandardCharsets.UTF_8);
        String xml = httpGet(url);

        JournalArticle paper = new JournalArticle();
        paper.setPaperId("arxiv-" + arxivId);
        paper.setUrl("https://arxiv.org/abs/" + arxivId);

        // Simple XML parsing for ArXiv Atom feed
        paper.setTitle(extractXml(xml, "title").replaceAll("\\s+", " ").trim());
        paper.setAbstractText(extractXml(xml, "summary").trim());
        String published = extractXml(xml, "published");
        if (published.length() >= 4) paper.setYear(Integer.parseInt(published.substring(0, 4)));

        // Authors
        int idx = 0;
        while ((idx = xml.indexOf("<author>", idx)) != -1) {
            int nameStart = xml.indexOf("<name>", idx);
            int nameEnd = xml.indexOf("</name>", nameStart);
            if (nameStart != -1 && nameEnd != -1) {
                paper.addAuthor(new Author(xml.substring(nameStart + 6, nameEnd).trim()));
            }
            idx = nameEnd != -1 ? nameEnd : idx + 8;
        }
        return paper;
    }

    public Publication lookupByPmid(String pmid) throws Exception {
        String url = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=" + pmid + "&retmode=json";
        String json = httpGet(url);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject result = root.getAsJsonObject("result");
        if (result == null || !result.has(pmid)) throw new Exception("PMID not found: " + pmid);
        JsonObject paper_data = result.getAsJsonObject(pmid);

        JournalArticle paper = new JournalArticle();
        paper.setPaperId("pmid-" + pmid);
        if (paper_data.has("title")) paper.setTitle(paper_data.get("title").getAsString());
        if (paper_data.has("source")) { paper.setVenue(paper_data.get("source").getAsString()); paper.setJournalName(paper_data.get("source").getAsString()); }
        if (paper_data.has("pubdate")) {
            String pubdate = paper_data.get("pubdate").getAsString();
            if (pubdate.length() >= 4) try { paper.setYear(Integer.parseInt(pubdate.substring(0, 4))); } catch (NumberFormatException ignored) {}
        }
        if (paper_data.has("authors") && !paper_data.get("authors").isJsonNull()) {
            for (JsonElement ae : paper_data.getAsJsonArray("authors")) {
                JsonObject ao = ae.getAsJsonObject();
                if (ao.has("name")) paper.addAuthor(new Author(ao.get("name").getAsString()));
            }
        }
        // Try to get DOI from article IDs
        if (paper_data.has("articleids") && !paper_data.get("articleids").isJsonNull()) {
            for (JsonElement id : paper_data.getAsJsonArray("articleids")) {
                JsonObject idObj = id.getAsJsonObject();
                if ("doi".equals(idObj.get("idtype").getAsString())) {
                    paper.setDoi(idObj.get("value").getAsString());
                    paper.setUrl("https://doi.org/" + paper.getDoi());
                }
            }
        }
        return paper;
    }

    /** Get open-access PDF URL via Unpaywall */
    public String getOpenAccessPdfUrl(String doi) {
        try {
            String url = "https://api.unpaywall.org/v2/" + URLEncoder.encode(doi, StandardCharsets.UTF_8) + "?email=citeright@example.com";
            String json = httpGet(url);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("best_oa_location") && !root.get("best_oa_location").isJsonNull()) {
                JsonObject oa = root.getAsJsonObject("best_oa_location");
                if (oa.has("url_for_pdf") && !oa.get("url_for_pdf").isJsonNull()) {
                    return oa.get("url_for_pdf").getAsString();
                }
            }
        } catch (Exception e) { System.err.println("[MetadataLookup] Unpaywall error: " + e.getMessage()); }
        return null;
    }

    /** Get ArXiv PDF URL */
    public String getArxivPdfUrl(String arxivId) {
        return "https://arxiv.org/pdf/" + arxivId + ".pdf";
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "CiteRight/1.0 (mailto:citeright@example.com)")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("HTTP " + response.statusCode());
        return response.body();
    }

    private void extractYear(JsonObject msg, Publication paper) {
        for (String field : new String[]{"published-print", "published-online"}) {
            try {
                if (msg.has(field) && !msg.get(field).isJsonNull()) {
                    JsonArray parts = msg.getAsJsonObject(field).getAsJsonArray("date-parts");
                    if (parts.size() > 0) { paper.setYear(parts.get(0).getAsJsonArray().get(0).getAsInt()); return; }
                }
            } catch (Exception ignored) {}
        }
    }

    private String extractXml(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        if (start == -1) { start = xml.indexOf("<" + tag + " "); if (start == -1) return ""; start = xml.indexOf(">", start); }
        else { start += tag.length() + 2; }
        int end = xml.indexOf("</" + tag + ">", start);
        return end > start ? xml.substring(start, end) : "";
    }
}
