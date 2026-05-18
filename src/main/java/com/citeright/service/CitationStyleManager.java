package com.citeright.service;

import com.citeright.model.*;
import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.csl.*;
import de.undercouch.citeproc.ItemDataProvider;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages CSL citation formatting using citeproc-java.
 * 
 * - Bundles 10 core styles from classpath (via org.citationstyles:styles)
 * - Downloads niche styles on-demand from the official CSL GitHub repo
 * - Caches all downloaded styles permanently in ~/.citeright/styles/
 * - Provides formatted citations in any CSL style
 */
public class CitationStyleManager {

    private static CitationStyleManager instance;

    /** Core styles bundled inside the JAR via the styles dependency */
    private static final String[] CORE_STYLES = {
            "apa", "ieee", "modern-language-association",
            "chicago-author-date", "harvard-cite-them-right",
            "vancouver", "nature", "science", "cell", "elsevier-harvard"
    };

    /** Human-friendly display names for the core styles */
    private static final Map<String, String> DISPLAY_NAMES = new LinkedHashMap<>();
    static {
        DISPLAY_NAMES.put("apa", "APA 7th Edition");
        DISPLAY_NAMES.put("ieee", "IEEE");
        DISPLAY_NAMES.put("modern-language-association", "MLA 9th Edition");
        DISPLAY_NAMES.put("chicago-author-date", "Chicago (Author-Date)");
        DISPLAY_NAMES.put("harvard-cite-them-right", "Harvard");
        DISPLAY_NAMES.put("vancouver", "Vancouver");
        DISPLAY_NAMES.put("nature", "Nature");
        DISPLAY_NAMES.put("science", "Science");
        DISPLAY_NAMES.put("cell", "Cell");
        DISPLAY_NAMES.put("elsevier-harvard", "Elsevier Harvard");
    }

    private final Path stylesDir;

    private CitationStyleManager() {
        String home = System.getProperty("user.home");
        stylesDir = Paths.get(home, ".citeright", "styles");
        try {
            Files.createDirectories(stylesDir);
        } catch (IOException e) {
            System.err.println("[CSL] Could not create styles directory: " + e.getMessage());
        }
    }

    public static synchronized CitationStyleManager getInstance() {
        if (instance == null) {
            instance = new CitationStyleManager();
        }
        return instance;
    }

    /**
     * Returns all available style names (core + cached).
     */
    public List<String> getAvailableStyles() {
        List<String> styles = new ArrayList<>(Arrays.asList(CORE_STYLES));
        // Add any user-downloaded styles from the cache directory
        try {
            if (Files.exists(stylesDir)) {
                Files.list(stylesDir)
                        .filter(p -> p.toString().endsWith(".csl"))
                        .forEach(p -> {
                            String name = p.getFileName().toString().replace(".csl", "");
                            if (!styles.contains(name)) {
                                styles.add(name);
                            }
                        });
            }
        } catch (IOException ignored) {}
        return styles;
    }

    /**
     * Returns display name for a style ID, or the ID itself if unknown.
     */
    public String getDisplayName(String styleId) {
        return DISPLAY_NAMES.getOrDefault(styleId, styleId);
    }

    /**
     * Formats a single publication in the given CSL style.
     *
     * @param pub       The publication to format
     * @param styleId   CSL style identifier (e.g., "apa", "ieee")
     * @return Formatted citation string, or an error message
     */
    public String formatCitation(Publication pub, String styleId) {
        try {
            CSLItemData item = publicationToCSL(pub);
            ItemDataProvider provider = new SingleItemProvider(item);

            CSL csl = new CSL(provider, styleId);
            csl.setOutputFormat("text");
            csl.registerCitationItems(item.getId());
            return cleanHtml(csl.makeBibliography().makeString());
        } catch (Exception e) {
            System.err.println("[CSL] Error formatting with style '" + styleId + "': " + e.getMessage());
            // If it's a missing style, try downloading it
            if (e.getMessage() != null && e.getMessage().contains("Could not find")) {
                if (downloadStyle(styleId)) {
                    return formatCitationFromFile(pub, styleId);
                }
            }
            return "⚠️ Could not format citation: " + e.getMessage();
        }
    }

    /**
     * Formats using a locally cached .csl file.
     */
    private String formatCitationFromFile(Publication pub, String styleId) {
        try {
            Path cslFile = stylesDir.resolve(styleId + ".csl");
            if (!Files.exists(cslFile)) return "⚠️ Style file not found: " + styleId;

            String styleXml = Files.readString(cslFile);
            CSLItemData item = publicationToCSL(pub);
            ItemDataProvider provider = new SingleItemProvider(item);

            CSL csl = new CSL(provider, styleXml);
            csl.setOutputFormat("text");
            csl.registerCitationItems(item.getId());
            return cleanHtml(csl.makeBibliography().makeString());
        } catch (Exception e) {
            return "⚠️ Could not format citation: " + e.getMessage();
        }
    }

    /**
     * Downloads a CSL style from the official GitHub repository and caches it.
     */
    public boolean downloadStyle(String styleId) {
        String url = "https://raw.githubusercontent.com/citation-style-language/styles/master/" + styleId + ".csl";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Validate it's actual XML
                if (body.contains("<style") && body.contains("</style>")) {
                    Path dest = stylesDir.resolve(styleId + ".csl");
                    Files.writeString(dest, body);
                    System.out.println("[CSL] Downloaded and cached style: " + styleId);
                    return true;
                } else {
                    System.err.println("[CSL] Downloaded content is not valid CSL XML for: " + styleId);
                }
            } else {
                System.err.println("[CSL] Failed to download style '" + styleId + "': HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[CSL] Download error for style '" + styleId + "': " + e.getMessage());
        }
        return false;
    }

    /**
     * Converts our Publication model into a CSLItemData object for citeproc-java.
     */
    private CSLItemData publicationToCSL(Publication pub) {
        CSLItemDataBuilder builder = new CSLItemDataBuilder()
                .id(pub.getPaperId() != null ? pub.getPaperId() : "item-1");

        // Type
        if (pub instanceof Book) {
            builder.type(CSLType.BOOK);
        } else if (pub instanceof ConferencePaper) {
            builder.type(CSLType.PAPER_CONFERENCE);
        } else {
            builder.type(CSLType.ARTICLE_JOURNAL);
        }

        // Title
        if (pub.getTitle() != null) builder.title(pub.getTitle());

        // Authors
        if (pub.getAuthors() != null && !pub.getAuthors().isEmpty()) {
            CSLName[] names = pub.getAuthors().stream()
                    .map(a -> {
                        String fullName = a.getName();
                        String given = "";
                        String family = fullName;
                        if (fullName != null && fullName.contains(" ")) {
                            int lastSpace = fullName.lastIndexOf(' ');
                            given = fullName.substring(0, lastSpace).trim();
                            family = fullName.substring(lastSpace + 1).trim();
                        }
                        return new CSLNameBuilder()
                            .given(given)
                            .family(family)
                            .build();
                    })
                    .toArray(CSLName[]::new);
            builder.author(names);
        }

        // Year / Date
        if (pub.getYear() > 0) {
            builder.issued(pub.getYear());
        }

        // Venue
        if (pub.getVenue() != null && !pub.getVenue().isEmpty()) {
            if (pub instanceof ConferencePaper) {
                builder.containerTitle(pub.getVenue());
                builder.eventTitle(pub.getVenue());
            } else {
                builder.containerTitle(pub.getVenue());
            }
        }

        // DOI
        if (pub.getDoi() != null && !pub.getDoi().isEmpty()) {
            builder.DOI(pub.getDoi());
        }

        // URL
        if (pub.getUrl() != null && !pub.getUrl().isEmpty()) {
            builder.URL(pub.getUrl());
        }

        // Journal-specific fields
        if (pub instanceof JournalArticle ja) {
            if (ja.getVolume() != null) builder.volume(ja.getVolume());
            if (ja.getIssue() != null) builder.issue(ja.getIssue());
            if (ja.getPages() != null) builder.page(ja.getPages());
        }

        // Book-specific fields
        if (pub instanceof Book book) {
            if (book.getPublisher() != null) builder.publisher(book.getPublisher());
            if (book.getIsbn() != null) builder.ISBN(book.getIsbn());
            if (book.getEdition() != null) builder.edition(book.getEdition());
        }

        return builder.build();
    }

    /** Strips any residual HTML tags from citeproc output */
    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").trim();
    }

    // ─── Inner class: provides a single item to citeproc-java ───────────────

    private static class SingleItemProvider implements ItemDataProvider {
        private final CSLItemData item;

        SingleItemProvider(CSLItemData item) {
            this.item = item;
        }

        @Override
        public CSLItemData retrieveItem(String id) {
            return item;
        }

        @Override
        public java.util.Collection<String> getIds() {
            return java.util.List.of(item.getId());
        }
    }
}
